package com.prescriptionscanner.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "prescribed_medicines", indexes = {
		@Index(name = "medicines_visit_idx", columnList = "visit_id"),
		@Index(name = "medicines_name_idx", columnList = "name")
})
public class PrescribedMedicine extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "visit_id", nullable = false)
	private Visit visit;

	@Column(nullable = false, length = 200)
	private String name;

	@Column(length = 120)
	private String dose;

	@Column(length = 120)
	private String frequency;

	@Column(length = 120)
	private String duration;

	@Column(columnDefinition = "text")
	private String instructions;

	public Visit getVisit() { return visit; }
	public void setVisit(Visit visit) { this.visit = visit; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public String getDose() { return dose; }
	public void setDose(String dose) { this.dose = dose; }
	public String getFrequency() { return frequency; }
	public void setFrequency(String frequency) { this.frequency = frequency; }
	public String getDuration() { return duration; }
	public void setDuration(String duration) { this.duration = duration; }
	public String getInstructions() { return instructions; }
	public void setInstructions(String instructions) { this.instructions = instructions; }
}
