package com.prescriptionscanner.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.prescriptionscanner.domain.MedicalDocument;

public interface MedicalDocumentRepository extends JpaRepository<MedicalDocument, Long> {

	List<MedicalDocument> findByPatientIdOrderByDocumentDateDescIdDesc(Long patientId);

	long countByPatientId(Long patientId);
}
