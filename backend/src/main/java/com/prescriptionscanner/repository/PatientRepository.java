package com.prescriptionscanner.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.prescriptionscanner.domain.Patient;

public interface PatientRepository extends JpaRepository<Patient, Long> {

	Optional<Patient> findByPid(String pid);

	/** Lab reports only print the trailing 6 digits; pidShort is that number. */
	Optional<Patient> findFirstByPidShort(String pidShort);

	List<Patient> findByNameAndPhone(String name, String phone);

	/**
	 * Every patient whose name matches exactly (case-insensitive). Gender and age
	 * are compared in code after normalisation, because reports write "Female"
	 * and "F" interchangeably.
	 */
	@Query("""
			SELECT p FROM Patient p
			WHERE LOWER(p.name) = LOWER(:name)
			ORDER BY p.id ASC
			""")
	List<Patient> findAllByNormalizedName(@Param("name") String name);

	/** Case-insensitive partial match across name, pid and phone. */
	@Query("""
			SELECT p FROM Patient p
			WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :term, '%'))
			   OR LOWER(COALESCE(p.pid, '')) LIKE LOWER(CONCAT('%', :term, '%'))
			   OR LOWER(COALESCE(p.phone, '')) LIKE LOWER(CONCAT('%', :term, '%'))
			""")
	Page<Patient> search(@Param("term") String term, Pageable pageable);

	/**
	 * Partial, case-insensitive name match and exact-or-prefix pid match. Pass an
	 * empty string for a side that should be ignored ("" matches everything).
	 */
	@Query("""
			SELECT p FROM Patient p
			WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%'))
			  AND LOWER(COALESCE(p.pid, '')) LIKE LOWER(CONCAT(:pid, '%'))
			ORDER BY p.name ASC, p.id ASC
			""")
	List<Patient> searchByNameAndPid(@Param("name") String name, @Param("pid") String pid);

	/**
	 * Candidate duplicates for the "never auto-merge" rule: same normalized name
	 * plus age and gender. The caller passes an already-normalized name.
	 */
	@Query("""
			SELECT p FROM Patient p
			WHERE LOWER(p.name) = LOWER(:name)
			  AND LOWER(COALESCE(p.gender, '')) = LOWER(COALESCE(:gender, ''))
			  AND LOWER(COALESCE(p.age, '')) = LOWER(COALESCE(:age, ''))
			ORDER BY p.id ASC
			""")
	List<Patient> findPossibleMatches(@Param("name") String name,
			@Param("gender") String gender, @Param("age") String age);
}
