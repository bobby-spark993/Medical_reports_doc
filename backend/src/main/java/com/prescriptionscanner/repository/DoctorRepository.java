package com.prescriptionscanner.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.prescriptionscanner.domain.Doctor;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {

	Optional<Doctor> findFirstByRegistrationNo(String registrationNo);

	Optional<Doctor> findFirstByNameIgnoreCase(String name);
}
