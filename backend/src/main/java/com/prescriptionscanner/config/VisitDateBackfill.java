package com.prescriptionscanner.config;

import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.prescriptionscanner.domain.AppSetting;
import com.prescriptionscanner.domain.Visit;
import com.prescriptionscanner.repository.AppSettingRepository;
import com.prescriptionscanner.repository.VisitRepository;
import com.prescriptionscanner.util.Dates;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * One-time, idempotent backfill that re-dates already-saved documents by the
 * date printed on the document itself - a prescription by its "Appt. Date", a
 * report by its "Received on" date - instead of the day it was scanned.
 *
 * <p>The work is recorded in {@code app_settings} so it runs exactly once: later
 * startups skip it, which means a date a user edited by hand is never undone.
 * Set {@code app.backfill.visit-dates=false} to switch it off entirely.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class VisitDateBackfill implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(VisitDateBackfill.class);

	private static final String DONE_KEY = "backfill.visit-dates.v2";

	private final VisitRepository visitRepository;
	private final AppSettingRepository settingRepository;
	private final ObjectMapper mapper;
	private final boolean enabled;

	public VisitDateBackfill(VisitRepository visitRepository, AppSettingRepository settingRepository,
			ObjectMapper mapper, @Value("${app.backfill.visit-dates:true}") boolean enabled) {
		this.visitRepository = visitRepository;
		this.settingRepository = settingRepository;
		this.mapper = mapper;
		this.enabled = enabled;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (!enabled) {
			log.info("[backfill] visit-date backfill disabled (app.backfill.visit-dates=false)");
			return;
		}
		if (settingRepository.existsById(DONE_KEY)) {
			log.info("[backfill] visit dates already backfilled, skipping");
			return;
		}

		List<Visit> visits = visitRepository.findAll();
		int updated = 0;
		for (Visit visit : visits) {
			if (!visit.isVerified()) {
				continue;
			}
			String json = firstNonBlank(visit.getReviewedJson(), visit.getRawAiJson());
			if (json == null) {
				continue;
			}
			try {
				LocalDate documentDate = documentDate(mapper.readTree(json));
				if (documentDate != null && !documentDate.equals(visit.getVisitDate())) {
					visit.setVisitDate(documentDate);
					visitRepository.save(visit);
					updated++;
				}
			} catch (Exception ex) {
				log.warn("[backfill] visit {} skipped: {}", visit.getId(), ex.getMessage());
			}
		}

		AppSetting done = new AppSetting();
		done.setKey(DONE_KEY);
		done.setValue("done");
		settingRepository.save(done);

		log.info("[backfill] re-dated {} document(s) from their printed date", updated);
	}

	/** Mirrors the review form's rule: prescription = Appt. Date, report = Received on. */
	private static LocalDate documentDate(JsonNode root) {
		String receivedOn = text(root.path("report").path("received_on"));
		String reportDate = firstNonBlank(receivedOn,
				text(root.path("report").path("report_date")),
				text(root.path("report").path("reported_on")));
		String type = text(root.path("document_type"));
		boolean prescription = type == null || "PRESCRIPTION".equalsIgnoreCase(type);
		String value = prescription
				? firstNonBlank(text(root.path("appointment").path("date")), reportDate)
				: reportDate;
		return Dates.parseFlexible(value);
	}

	private static String text(JsonNode node) {
		if (node == null || node.isMissingNode() || node.isNull()) {
			return null;
		}
		String value = node.asString();
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}
}
