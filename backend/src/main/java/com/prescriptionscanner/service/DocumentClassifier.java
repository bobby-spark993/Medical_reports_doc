package com.prescriptionscanner.service;

import java.util.Locale;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.prescriptionscanner.domain.Visit;
import com.prescriptionscanner.dto.GeminiExtraction;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Decides a scanned page's document type from its printed PID, as required by
 * the clinic: a page whose PID is printed in the "SNP" + 12-digit format is a
 * prescription; every other page is a report. This overrides the model's own
 * document_type guess.
 *
 * <p>The same rule is re-applied when a saved document is read back, so the
 * patient folder always shows the type the scan decided on: the document the
 * PID was printed on is the prescription.
 */
@Component
public class DocumentClassifier {

	public static final String PRESCRIPTION = "PRESCRIPTION";
	public static final String REPORT = "LAB_REPORT";

	/** e.g. SNP260404071826. */
	static final Pattern PRESCRIPTION_PID = Pattern.compile("^SNP\\d{12}$", Pattern.CASE_INSENSITIVE);

	/**
	 * Any PID-shaped value: letters followed by digits. Catches a prescription
	 * whose PID was read with the wrong digit count, without ever matching the
	 * bare 6-digit reference number a lab report prints next to the name.
	 */
	static final Pattern PID_LIKE = Pattern.compile("^[A-Z]{2,4}\\d{6,}$");

	private static final Logger log = LoggerFactory.getLogger(DocumentClassifier.class);

	private final ObjectMapper mapper;

	public DocumentClassifier(ObjectMapper mapper) {
		this.mapper = mapper;
	}

	public boolean isPrescription(GeminiExtraction extraction) {
		if (extraction == null || extraction.patient() == null || extraction.patient().pid() == null) {
			return false;
		}
		return isPrescriptionPid(extraction.patient().pid());
	}

	/** A printed PID means a prescription - whatever spacing or casing the OCR produced. */
	public static boolean isPrescriptionPid(String rawPid) {
		String pid = normalizePid(rawPid);
		return pid != null && (PRESCRIPTION_PID.matcher(pid).matches() || PID_LIKE.matcher(pid).matches());
	}

	/** "SNP 260404-071826" becomes "SNP260404071826". */
	public static String normalizePid(String rawPid) {
		if (rawPid == null) {
			return null;
		}
		String pid = rawPid.replaceAll("[\\s\\-]", "").toUpperCase(Locale.ROOT);
		return pid.isEmpty() ? null : pid;
	}

	public String decideType(GeminiExtraction extraction) {
		return isPrescription(extraction) ? PRESCRIPTION : REPORT;
	}

	/** Returns the same extraction with document_type forced by the PID rule. */
	public GeminiExtraction apply(GeminiExtraction extraction) {
		if (extraction == null) {
			return null;
		}
		return extraction.withDocumentType(decideType(extraction));
	}

	// ── Reading the type back ───────────────────────────────────────────────

	/**
	 * The type of a saved document, decided from the scan itself: the PID
	 * printed on the page makes it the prescription, everything else keeps the
	 * type the scan recorded. The reviewed JSON is only consulted when the scan
	 * JSON is missing, so a PID typed in by hand never turns a report into a
	 * prescription.
	 */
	public String typeOf(Visit visit) {
		if (visit == null) {
			return null;
		}
		String scanned = typeFromJson(visit.getRawAiJson());
		if (scanned != null) {
			return scanned;
		}
		return typeFromJson(visit.getReviewedJson());
	}

	/**
	 * The document type of a stored extraction. A printed PID wins over the
	 * recorded type; otherwise the recorded type is kept.
	 */
	public String typeFromJson(String json) {
		JsonNode root = parse(json);
		if (root == null) {
			return null;
		}
		String pid = text(root.path("patient").path("pid"));
		if (pid != null && isPrescriptionPid(pid)) {
			return PRESCRIPTION;
		}
		return text(root.path("document_type"));
	}

	private JsonNode parse(String json) {
		if (json == null || json.isBlank()) {
			return null;
		}
		try {
			JsonNode root = mapper.readTree(json);
			return root == null || root.isMissingNode() || root.isNull() ? null : root;
		} catch (Exception ex) {
			log.warn("Stored extraction could not be parsed: {}", ex.getMessage());
			return null;
		}
	}

	private static String text(JsonNode node) {
		if (node == null || node.isMissingNode() || node.isNull()) {
			return null;
		}
		String value = node.asString();
		return value == null || value.isBlank() ? null : value.trim();
	}
}
