package com.prescriptionscanner.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.prescriptionscanner.domain.DocumentLabResult;

public interface DocumentLabResultRepository extends JpaRepository<DocumentLabResult, Long> {

	List<DocumentLabResult> findByDocumentId(Long documentId);
}
