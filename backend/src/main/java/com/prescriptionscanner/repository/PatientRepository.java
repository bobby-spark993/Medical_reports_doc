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
	 * Every patient whose name matches after normalisation (lower-case, trimmed
	 * and inner whitespace collapsed). Gender and age are compared in code after
	 * normalisation, because reports write "Female" and "F" interchangeably and
	 * "26" and "26 years" interchangeably.
	 */
	@Query(value = """
			SELECT * FROM patients p
			WHERE regexp_replace(lower(trim(p.name)), '[[:space:]]+', ' ', 'g') = :name
			ORDER BY p.id ASC
			""", nativeQuery = true)
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
}
