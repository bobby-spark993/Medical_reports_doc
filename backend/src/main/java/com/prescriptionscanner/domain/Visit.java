package com.prescriptionscanner.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * A Visit doubles as the AI-extraction DRAFT before verification.
 *
 * <p>patient and doctor are deliberately nullable: at upload time neither may
 * exist yet. "Verify &amp; Save" fills both and flips isVerified to true, all
 * inside one transaction.
 */
@Entity
@Table(name = "visits", indexes = {
		@Index(name = "visits_patient_idx", columnList = "patient_id"),
		@Index(name = "visits_doctor_idx", columnList = "doctor_id"),
		@Index(name = "visits_is_verified_idx", columnList = "is_verified"),
		@Index(name = "visits_visit_date_idx", columnList = "visit_date"),
		@Index(name = "visits_follow_up_date_idx", columnList = "follow_up_date"),
		@Index(name = "visits_patient_verified_visit_date_idx", columnList = "patient_id,is_verified,visit_date"),
		@Index(name = "visits_verified_follow_up_idx", columnList = "is_verified,follow_up_date")
})
public class Visit extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "patient_id")
	private Patient patient;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "doctor_id")
	private Doctor doctor;

	@Column(name = "visit_date")
	private LocalDate visitDate;

	/** Time printed on the document (e.g. "10:30 AM"), when the scan shows one. */
	@Column(name = "visit_time", length = 20)
	private String visitTime;

	@Column(name = "valid_up_to")
	private LocalDate validUpTo;

	@Column(name = "appointment_no", length = 80)
	private String appointmentNo;

	@Column(length = 60)
	private String mode;

	@Column(name = "follow_up_date")
	private LocalDate followUpDate;

	@Column(columnDefinition = "text")
	private String notes;

	/** Project-relative path under storage/uploads. Never publicly served. */
	@Column(name = "scan_file_path", length = 400)
	private String scanFilePath;

	/** Groups the drafts created from one page-wise upload; null for single-file scans. */
	@Column(name = "batch_id", length = 36)
	private String batchId;

	/** 1-based page number of this draft within its source document. */
	@Column(name = "page_no")
	private Integer pageNo;

	/** Total pages produced by the source document (1 for a single image). */
	@Column(name = "page_count")
	private Integer pageCount;

	/** The raw Gemini response, kept for the audit trail. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "raw_ai_json", columnDefinition = "jsonb")
	private String rawAiJson;

	/** The reviewed/edited extraction (all page types), stored as JSON. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "reviewed_json", columnDefinition = "jsonb")
	private String reviewedJson;

	@Column(name = "is_verified", nullable = false)
	private boolean isVerified = false;

	@Column(name = "verified_by")
	private Long verifiedBy;

	@OneToMany(mappedBy = "visit", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<Diagnosis> diagnoses = new ArrayList<>();

	@OneToMany(mappedBy = "visit", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<PrescribedMedicine> medicines = new ArrayList<>();

	@OneToMany(mappedBy = "visit", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<LabResult> labResults = new ArrayList<>();

	public Patient getPatient() { return patient; }
	public void setPatient(Patient patient) { this.patient = patient; }
	public Doctor getDoctor() { return doctor; }
	public void setDoctor(Doctor doctor) { this.doctor = doctor; }
	public LocalDate getVisitDate() { return visitDate; }
	public void setVisitDate(LocalDate visitDate) { this.visitDate = visitDate; }
	public String getVisitTime() { return visitTime; }
	public void setVisitTime(String visitTime) { this.visitTime = visitTime; }
	public LocalDate getValidUpTo() { return validUpTo; }
	public void setValidUpTo(LocalDate validUpTo) { this.validUpTo = validUpTo; }
	public String getAppointmentNo() { return appointmentNo; }
	public void setAppointmentNo(String appointmentNo) { this.appointmentNo = appointmentNo; }
	public String getMode() { return mode; }
	public void setMode(String mode) { this.mode = mode; }
	public LocalDate getFollowUpDate() { return followUpDate; }
	public void setFollowUpDate(LocalDate followUpDate) { this.followUpDate = followUpDate; }
	public String getNotes() { return notes; }
	public void setNotes(String notes) { this.notes = notes; }
	public String getScanFilePath() { return scanFilePath; }
	public void setScanFilePath(String scanFilePath) { this.scanFilePath = scanFilePath; }
	public String getBatchId() { return batchId; }
	public void setBatchId(String batchId) { this.batchId = batchId; }
	public Integer getPageNo() { return pageNo; }
	public void setPageNo(Integer pageNo) { this.pageNo = pageNo; }
	public Integer getPageCount() { return pageCount; }
	public void setPageCount(Integer pageCount) { this.pageCount = pageCount; }
	public String getRawAiJson() { return rawAiJson; }
	public void setRawAiJson(String rawAiJson) { this.rawAiJson = rawAiJson; }
	public String getReviewedJson() { return reviewedJson; }
	public void setReviewedJson(String reviewedJson) { this.reviewedJson = reviewedJson; }
	public boolean isVerified() { return isVerified; }
	public void setVerified(boolean verified) { isVerified = verified; }
	public Long getVerifiedBy() { return verifiedBy; }
	public void setVerifiedBy(Long verifiedBy) { this.verifiedBy = verifiedBy; }
	public List<Diagnosis> getDiagnoses() { return diagnoses; }
	public void setDiagnoses(List<Diagnosis> diagnoses) { this.diagnoses = diagnoses; }
	public List<PrescribedMedicine> getMedicines() { return medicines; }
	public void setMedicines(List<PrescribedMedicine> medicines) { this.medicines = medicines; }
	public List<LabResult> getLabResults() { return labResults; }
	public void setLabResults(List<LabResult> labResults) { this.labResults = labResults; }
}
