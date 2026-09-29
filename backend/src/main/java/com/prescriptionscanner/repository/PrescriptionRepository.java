package com.prescriptionscanner.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.prescriptionscanner.domain.Prescription;

public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

	Optional<Prescription> findByDocumentId(Long documentId);
}
