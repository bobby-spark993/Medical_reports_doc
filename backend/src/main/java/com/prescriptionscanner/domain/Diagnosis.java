package com.prescriptionscanner.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "diagnoses", indexes = {
		@Index(name = "diagnoses_visit_idx", columnList = "visit_id")
})
public class Diagnosis extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "visit_id", nullable = false)
	private Visit visit;

	@Column(nullable = false, columnDefinition = "text")
	private String description;

	public Visit getVisit() { return visit; }
	public void setVisit(Visit visit) { this.visit = visit; }
	public String getDescription() { return description; }
	public void setDescription(String description) { this.description = description; }
}
