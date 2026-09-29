package com.prescriptionscanner.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One analysed value on a lab report (e.g. Haemoglobin 10.5 gm/dl LOW). */
@Entity
@Table(name = "document_lab_results", indexes = {
		@Index(name = "document_lab_results_document_idx", columnList = "document_id"),
		@Index(name = "document_lab_results_name_idx", columnList = "test_name")
})
public class DocumentLabResult extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "document_id", nullable = false)
	private MedicalDocument document;

	/** e.g. "Complete Blood Count". */
	@Column(name = "test_group", length = 160)
	private String testGroup;

	@Column(name = "test_name", nullable = false, length = 200)
	private String testName;

	@Column(name = "result_value", length = 120)
	private String resultValue;

	@Column(length = 60)
	private String unit;

	@Column(name = "reference_range", length = 120)
	private String referenceRange;

	@Enumerated(EnumType.STRING)
	@Column(length = 10)
	private LabFlag flag = LabFlag.NORMAL;

	public MedicalDocument getDocument() { return document; }
	public void setDocument(MedicalDocument document) { this.document = document; }
	public String getTestGroup() { return testGroup; }
	public void setTestGroup(String testGroup) { this.testGroup = testGroup; }
	public String getTestName() { return testName; }
	public void setTestName(String testName) { this.testName = testName; }
	public String getResultValue() { return resultValue; }
	public void setResultValue(String resultValue) { this.resultValue = resultValue; }
	public String getUnit() { return unit; }
	public void setUnit(String unit) { this.unit = unit; }
	public String getReferenceRange() { return referenceRange; }
	public void setReferenceRange(String referenceRange) { this.referenceRange = referenceRange; }
	public LabFlag getFlag() { return flag; }
	public void setFlag(LabFlag flag) { this.flag = flag; }
}
