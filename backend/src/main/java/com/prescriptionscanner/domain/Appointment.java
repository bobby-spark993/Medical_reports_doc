package com.prescriptionscanner.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "appointments", indexes = {
		@Index(name = "appointments_patient_idx", columnList = "patient_id"),
		@Index(name = "appointments_doctor_idx", columnList = "doctor_id"),
		@Index(name = "appointments_scheduled_at_idx", columnList = "scheduled_at"),
		@Index(name = "appointments_status_idx", columnList = "status")
})
public class Appointment extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "patient_id", nullable = false)
	private Patient patient;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "doctor_id")
	private Doctor doctor;

	@Column(name = "scheduled_at", nullable = false)
	private LocalDateTime scheduledAt;

	@Column(nullable = false, length = 20)
	private AppointmentStatus status = AppointmentStatus.SCHEDULED;

	@Column(columnDefinition = "text")
	private String notes;

	public Patient getPatient() { return patient; }
	public void setPatient(Patient patient) { this.patient = patient; }
	public Doctor getDoctor() { return doctor; }
	public void setDoctor(Doctor doctor) { this.doctor = doctor; }
	public LocalDateTime getScheduledAt() { return scheduledAt; }
	public void setScheduledAt(LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
	public AppointmentStatus getStatus() { return status; }
	public void setStatus(AppointmentStatus status) { this.status = status; }
	public String getNotes() { return notes; }
	public void setNotes(String notes) { this.notes = notes; }
}
