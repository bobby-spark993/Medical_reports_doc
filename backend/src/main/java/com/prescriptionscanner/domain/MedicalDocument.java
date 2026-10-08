package com.prescriptionscanner.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * A stored medical document (lab report or prescription) plus its extracted,
 * user-reviewed data. Coexists with the older Visit model.
 */
@Entity
@Table(name = "medical_documents", indexes = {
		@Index(name = "medical_documents_patient_idx", columnList = "patient_id"),
		@Index(name = "medical_documents_type_idx", columnList = "document_type"),
		@Index(name = "medical_documents_lab_report_id_idx", columnList = "lab_report_id"),
		@Index(name = "medical_documents_patient_date_idx", columnList = "patient_id,document_date")
})
public class MedicalDocument extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "patient_id")
	private Patient patient;

	@Enumerated(EnumType.STRING)
	@Column(name = "document_type", nullable = false, length = 30)
	private MedicalDocumentType documentType = MedicalDocumentType.OTHER;

	/** Project-relative path under app.upload.dir. Never publicly served. */
	@Column(name = "original_file_path", length = 400)
	private String originalFilePath;

	@Column(name = "original_file_name", length = 300)
	private String originalFileName;

	@Column(name = "document_date")
	private LocalDate documentDate;

	@Column(name = "reported_date")
	private LocalDate reportedDate;

	@Column(name = "lab_name", length = 200)
	private String labName;

	@Column(name = "lab_report_id", length = 80)
	private String labReportId;

	@Column(name = "referred_by", length = 160)
	private String referredBy;

	/** The raw Gemini JSON, kept for the audit trail. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "raw_extracted_json", columnDefinition = "jsonb")
	private String rawExtractedJson;

	@Column(name = "reviewed_by_user", nullable = false)
	private boolean reviewedByUser = false;

	@Column(name = "reviewed_by")
	private Long reviewedBy;

	@OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<DocumentLabResult> results = new ArrayList<>();

	@OneToOne(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private Prescription prescription;

	public void addResult(DocumentLabResult result) {
		result.setDocument(this);
		this.results.add(result);
	}

	public void setPrescription(Prescription prescription) {
		if (prescription != null) {
			prescription.setDocument(this);
		}
		this.prescription = prescription;
	}

	public Patient getPatient() { return patient; }
	public void setPatient(Patient patient) { this.patient = patient; }
	public MedicalDocumentType getDocumentType() { return documentType; }
	public void setDocumentType(MedicalDocumentType documentType) { this.documentType = documentType; }
	public String getOriginalFilePath() { return originalFilePath; }
	public void setOriginalFilePath(String originalFilePath) { this.originalFilePath = originalFilePath; }
	public String getOriginalFileName() { return originalFileName; }
	public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }
	public LocalDate getDocumentDate() { return documentDate; }
	public void setDocumentDate(LocalDate documentDate) { this.documentDate = documentDate; }
	public LocalDate getReportedDate() { return reportedDate; }
	public void setReportedDate(LocalDate reportedDate) { this.reportedDate = reportedDate; }
	public String getLabName() { return labName; }
	public void setLabName(String labName) { this.labName = labName; }
	public String getLabReportId() { return labReportId; }
	public void setLabReportId(String labReportId) { this.labReportId = labReportId; }
	public String getReferredBy() { return referredBy; }
	public void setReferredBy(String referredBy) { this.referredBy = referredBy; }
	public String getRawExtractedJson() { return rawExtractedJson; }
	public void setRawExtractedJson(String rawExtractedJson) { this.rawExtractedJson = rawExtractedJson; }
	public boolean isReviewedByUser() { return reviewedByUser; }
	public void setReviewedByUser(boolean reviewedByUser) { this.reviewedByUser = reviewedByUser; }
	public Long getReviewedBy() { return reviewedBy; }
	public void setReviewedBy(Long reviewedBy) { this.reviewedBy = reviewedBy; }
	public List<DocumentLabResult> getResults() { return results; }
	public void setResults(List<DocumentLabResult> results) { this.results = results; }
	public Prescription getPrescription() { return prescription; }
}
