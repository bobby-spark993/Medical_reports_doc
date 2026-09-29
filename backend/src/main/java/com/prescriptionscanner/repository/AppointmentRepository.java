package com.prescriptionscanner.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.prescriptionscanner.domain.Appointment;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

	@EntityGraph(attributePaths = { "doctor" })
	List<Appointment> findByPatientIdOrderByScheduledAtDesc(Long patientId);

	@EntityGraph(attributePaths = { "doctor" })
	List<Appointment> findByPatientIdOrderByScheduledAtAsc(Long patientId);
}
