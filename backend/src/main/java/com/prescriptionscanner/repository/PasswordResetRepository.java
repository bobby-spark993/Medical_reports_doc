package com.prescriptionscanner.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.prescriptionscanner.domain.PasswordReset;

public interface PasswordResetRepository extends JpaRepository<PasswordReset, Long> {

	Optional<PasswordReset> findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(String email);

	List<PasswordReset> findByEmailIgnoreCaseOrderByCreatedAtDesc(String email);

	void deleteByEmailIgnoreCase(String email);
}