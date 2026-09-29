package com.prescriptionscanner.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "lab_results", indexes = {
		@Index(name = "lab_results_visit_idx", columnList = "visit_id"),
		@Index(name = "lab_results_test_name_idx", columnList = "test_name")
})
public class LabResult extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "visit_id", nullable = false)
	private Visit visit;

	@Column(name = "test_name", nullable = false, length = 200)
	private String testName;

	@Column(length = 120)
	private String value;

	@Column(length = 60)
	private String unit;

	@Column(name = "reference_range", length = 120)
	private String referenceRange;

	@Column(name = "is_abnormal", nullable = false)
	private boolean isAbnormal = false;

	public Visit getVisit() { return visit; }
	public void setVisit(Visit visit) { this.visit = visit; }
	public String getTestName() { return testName; }
	public void setTestName(String testName) { this.testName = testName; }
	public String getValue() { return value; }
	public void setValue(String value) { this.value = value; }
	public String getUnit() { return unit; }
	public void setUnit(String unit) { this.unit = unit; }
	public String getReferenceRange() { return referenceRange; }
	public void setReferenceRange(String referenceRange) { this.referenceRange = referenceRange; }
	public boolean isAbnormal() { return isAbnormal; }
	public void setAbnormal(boolean abnormal) { isAbnormal = abnormal; }
}
