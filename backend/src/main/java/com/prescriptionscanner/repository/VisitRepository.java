package com.prescriptionscanner.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.prescriptionscanner.domain.Visit;

public interface VisitRepository extends JpaRepository<Visit, Long> {

	/** Draft/visit with doctor and patient fetched, so mapping can happen outside a session. */
	@EntityGraph(attributePaths = { "doctor", "patient" })
	Optional<Visit> findWithRefsById(Long id);

	List<Visit> findByPatientIdAndIsVerifiedTrueOrderByVisitDateDescIdDesc(Long patientId);

	List<Visit> findByPatientIdAndIsVerifiedTrueOrderByVisitDateAscIdAsc(Long patientId);

	/** patientId -> number of verified visits. Avoids a CASE expression that JPQL handles unevenly. */
	@Query("""
			SELECT v.patient.id, COUNT(v)
			FROM Visit v
			WHERE v.isVerified = true AND v.patient.id IN :patientIds
			GROUP BY v.patient.id
			""")
	List<Object[]> countVerifiedByPatient(@Param("patientIds") List<Long> patientIds);

	/** patientId -> earliest upcoming follow-up date. */
	@Query("""
			SELECT v.patient.id, MIN(v.followUpDate)
			FROM Visit v
			WHERE v.isVerified = true AND v.followUpDate >= :today AND v.patient.id IN :patientIds
			GROUP BY v.patient.id
			""")
	List<Object[]> nextFollowUpByPatient(@Param("patientIds") List<Long> patientIds, @Param("today") LocalDate today);
}
