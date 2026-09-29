package com.prescriptionscanner.domain;

import java.time.LocalDate;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** The prescription-specific details of a {@link MedicalDocument}. */
@Entity
@Table(name = "prescriptions", indexes = {
		@Index(name = "prescriptions_document_unique", columnList = "document_id", unique = true)
})
public class Prescription extends BaseEntity {

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "document_id", nullable = false, unique = true)
	private MedicalDocument document;

	@Column(name = "doctor_name", length = 160)
	private String doctorName;

	@Column(name = "doctor_qualification", length = 160)
	private String doctorQualification;

	@Column(name = "clinic_name", length = 200)
	private String clinicName;

	@Column(name = "registration_no", length = 80)
	private String registrationNo;

	@Column(name = "appointment_date")
	private LocalDate appointmentDate;

	@Column(name = "appointment_no", length = 80)
	private String appointmentNo;

	@Column(name = "valid_up_to")
	private LocalDate validUpTo;

	@Column(name = "patient_regd_valid_up_to")
	private LocalDate patientRegdValidUpTo;

	@Column(name = "diagnosis_or_complaints", columnDefinition = "text")
	private String diagnosisOrComplaints;

	/** JSON array: [{ name, dose, frequency, duration, notes }]. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "medicines_json", columnDefinition = "jsonb")
	private String medicinesJson;

	@Column(columnDefinition = "text")
	private String advice;

	@Column(name = "handwritten_raw_text", columnDefinition = "text")
	private String handwrittenRawText;

	@Enumerated(EnumType.STRING)
	@Column(length = 10)
	private ConfidenceLevel confidence = ConfidenceLevel.LOW;

	public MedicalDocument getDocument() { return document; }
	public void setDocument(MedicalDocument document) { this.document = document; }
	public String getDoctorName() { return doctorName; }
	public void setDoctorName(String doctorName) { this.doctorName = doctorName; }
	public String getDoctorQualification() { return doctorQualification; }
	public void setDoctorQualification(String doctorQualification) { this.doctorQualification = doctorQualification; }
	public String getClinicName() { return clinicName; }
	public void setClinicName(String clinicName) { this.clinicName = clinicName; }
	public String getRegistrationNo() { return registrationNo; }
	public void setRegistrationNo(String registrationNo) { this.registrationNo = registrationNo; }
	public LocalDate getAppointmentDate() { return appointmentDate; }
	public void setAppointmentDate(LocalDate appointmentDate) { this.appointmentDate = appointmentDate; }
	public String getAppointmentNo() { return appointmentNo; }
	public void setAppointmentNo(String appointmentNo) { this.appointmentNo = appointmentNo; }
	public LocalDate getValidUpTo() { return validUpTo; }
	public void setValidUpTo(LocalDate validUpTo) { this.validUpTo = validUpTo; }
	public LocalDate getPatientRegdValidUpTo() { return patientRegdValidUpTo; }
	public void setPatientRegdValidUpTo(LocalDate patientRegdValidUpTo) { this.patientRegdValidUpTo = patientRegdValidUpTo; }
	public String getDiagnosisOrComplaints() { return diagnosisOrComplaints; }
	public void setDiagnosisOrComplaints(String diagnosisOrComplaints) { this.diagnosisOrComplaints = diagnosisOrComplaints; }
	public String getMedicinesJson() { return medicinesJson; }
	public void setMedicinesJson(String medicinesJson) { this.medicinesJson = medicinesJson; }
	public String getAdvice() { return advice; }
	public void setAdvice(String advice) { this.advice = advice; }
	public String getHandwrittenRawText() { return handwrittenRawText; }
	public void setHandwrittenRawText(String handwrittenRawText) { this.handwrittenRawText = handwrittenRawText; }
	public ConfidenceLevel getConfidence() { return confidence; }
	public void setConfidence(ConfidenceLevel confidence) { this.confidence = confidence; }
}
