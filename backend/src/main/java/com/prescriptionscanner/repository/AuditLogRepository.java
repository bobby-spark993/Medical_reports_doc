package com.prescriptionscanner.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.prescriptionscanner.domain.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
