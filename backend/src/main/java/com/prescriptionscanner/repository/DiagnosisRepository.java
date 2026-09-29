package com.prescriptionscanner.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.prescriptionscanner.domain.Diagnosis;

public interface DiagnosisRepository extends JpaRepository<Diagnosis, Long> {
}
