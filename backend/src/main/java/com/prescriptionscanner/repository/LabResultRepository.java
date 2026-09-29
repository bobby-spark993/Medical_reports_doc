package com.prescriptionscanner.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.prescriptionscanner.domain.LabResult;

public interface LabResultRepository extends JpaRepository<LabResult, Long> {

	List<LabResult> findByIsAbnormalTrue();
}
