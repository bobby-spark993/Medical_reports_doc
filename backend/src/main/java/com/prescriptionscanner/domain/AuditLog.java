package com.prescriptionscanner.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Append-only trail. DPDP Act 2023 requires knowing who touched a patient
 * record and when, so entries are never updated or deleted.
 */
@Entity
@Table(name = "audit_logs", indexes = {
		@Index(name = "audit_user_idx", columnList = "user_id"),
		@Index(name = "audit_entity_idx", columnList = "entity, entity_id"),
		@Index(name = "audit_created_idx", columnList = "created_at")
})
public class AuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id")
	private Long userId;

	@Column(nullable = false, length = 40)
	private String action;

	@Column(nullable = false, length = 60)
	private String entity;

	@Column(name = "entity_id")
	private Long entityId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		if (this.createdAt == null) {
			this.createdAt = Instant.now();
		}
	}

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }
	public Long getUserId() { return userId; }
	public void setUserId(Long userId) { this.userId = userId; }
	public String getAction() { return action; }
	public void setAction(String action) { this.action = action; }
	public String getEntity() { return entity; }
	public void setEntity(String entity) { this.entity = entity; }
	public Long getEntityId() { return entityId; }
	public void setEntityId(Long entityId) { this.entityId = entityId; }
	public Instant getCreatedAt() { return createdAt; }
	public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
