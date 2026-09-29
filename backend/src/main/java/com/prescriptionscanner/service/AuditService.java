package com.prescriptionscanner.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.prescriptionscanner.domain.AuditLog;
import com.prescriptionscanner.repository.AuditLogRepository;

/**
 * Every read/write of a patient record is logged (DPDP Act 2023 traceability).
 *
 * <p>The methods here run with {@code REQUIRED} propagation and are always
 * invoked through the Spring proxy (never via {@code this.}), so they join an
 * active transaction when one exists and open their own when none does.
 *
 * <p>Callers must therefore NOT sit in a {@code readOnly = true} transaction
 * when they audit: a read-only Hibernate session refuses to flush the insert.
 */
@Service
public class AuditService {

	public static final String VIEW = "view";
	public static final String CREATE = "create";
	public static final String UPDATE = "update";
	public static final String DELETE = "delete";
	public static final String VERIFY = "verify";
	public static final String LOGIN = "login";
	public static final String LOGOUT = "logout";
	public static final String DOWNLOAD_REPORT = "download_report";

	private static final Logger log = LoggerFactory.getLogger(AuditService.class);

	private final AuditLogRepository repository;

	public AuditService(AuditLogRepository repository) {
		this.repository = repository;
	}

	/** Joins the caller's transaction when one is active. */
	@Transactional
	public void record(Long userId, String action, String entity, Long entityId) {
		try {
			AuditLog entry = new AuditLog();
			entry.setUserId(userId);
			entry.setAction(action);
			entry.setEntity(entity);
			entry.setEntityId(entityId);
			repository.save(entry);
		} catch (Exception ex) {
			log.warn("Could not write audit entry action={} entity={} id={}: {}",
					action, entity, entityId, ex.getMessage());
		}
	}

	/** Annotated so it joins/opens a transaction even when called without one. */
	@Transactional
	public void view(Long userId, String entity, Long entityId) {
		record(userId, VIEW, entity, entityId);
	}
}
