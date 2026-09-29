package com.prescriptionscanner.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.prescriptionscanner.domain.PrescribedMedicine;

public interface PrescribedMedicineRepository extends JpaRepository<PrescribedMedicine, Long> {
}
