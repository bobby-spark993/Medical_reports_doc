package com.prescriptionscanner.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.prescriptionscanner.domain.Patient;
import com.prescriptionscanner.repository.PatientRepository;

/**
 * Decides which existing patient a document belongs to.
 *
 * <p>Order of confidence: exact {@code pid} first, then the 6-digit
 * {@code pidShort} printed on lab reports. Name/age/gender collisions are only
 * ever returned as candidates for the user to choose - never auto-merged.
 */
@Service
public class PatientMatchingService {

	private final PatientRepository patientRepository;

	public PatientMatchingService(PatientRepository patientRepository) {
		this.patientRepository = patientRepository;
	}

	@Transactional(readOnly = true)
	public Optional<Patient> matchByPid(String pid) {
		if (pid == null || pid.isBlank()) {
			return Optional.empty();
		}
		return patientRepository.findByPid(pid.trim());
	}

	@Transactional(readOnly = true)
	public Optional<Patient> matchByPidShort(String rawRefNo) {
		String pidShort = pidShortOf(rawRefNo);
		if (pidShort == null) {
			return Optional.empty();
		}
		return patientRepository.findFirstByPidShort(pidShort);
	}

	/**
	 * Candidates sharing the normalized name plus age and gender. These are
	 * returned to the user only - the system never auto-merges on demographics,
	 * because two different people can share a name, age and gender.
	 */
	@Transactional(readOnly = true)
	public List<Patient> possibleMatches(String name, String gender, String age) {
		if (name == null || name.isBlank()) {
			return List.of();
		}
		return patientRepository.findPossibleMatches(normalizeName(name), clean(gender), clean(age));
	}

	/**
	 * First existing patient with the same name + age + gender. Used to add a new
	 * document to an existing patient folder. All three must be present: matching
	 * on a name alone (or only some demographics) would merge unrelated people.
	 */
	@Transactional(readOnly = true)
	public Optional<Patient> matchByDemographics(String name, String age, String gender) {
		String normalizedName = normalizeName(name);
		String normalizedAge = normalizeAge(age);
		String normalizedGender = normalizeGender(gender);
		if (normalizedName.isEmpty() || normalizedAge.isEmpty() || normalizedGender.isEmpty()) {
			return Optional.empty();
		}
		return patientRepository.findAllByNormalizedName(normalizedName).stream()
				.filter(p -> normalizedAge.equals(normalizeAge(p.getAge())))
				.filter(p -> normalizedGender.equals(normalizeGender(p.getGender())))
				.findFirst();
	}

	/** Trim, collapse internal whitespace and lowercase, so "SUSHILA  DEVI " == "sushila devi". */
	public static String normalizeName(String raw) {
		if (raw == null) {
			return "";
		}
		return raw.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	/** "Female" == "female" == "F" == "f"; anything else keeps its lowercased first letter. */
	public static String normalizeGender(String raw) {
		if (raw == null) {
			return "";
		}
		String value = raw.trim().toLowerCase(Locale.ROOT);
		return value.isEmpty() ? "" : value.substring(0, 1);
	}

	/** Keeps just the digits so "43" == "43 years" == "043". */
	public static String normalizeAge(String raw) {
		if (raw == null) {
			return "";
		}
		String digits = raw.replaceAll("\\D", "");
		if (digits.isEmpty()) {
			return raw.trim().toLowerCase(Locale.ROOT);
		}
		String stripped = digits.replaceFirst("^0+(?=\\d)", "");
		return stripped.isEmpty() ? digits : stripped;
	}

	/**
	 * The last 6 digits of a pid or of the trailing number on a lab report.
	 * Returns null when there are no digits at all.
	 */
	public static String pidShortOf(String raw) {
		if (raw == null) {
			return null;
		}
		String digits = raw.replaceAll("\\D", "");
		if (digits.isEmpty()) {
			return null;
		}
		return digits.length() <= 6 ? digits : digits.substring(digits.length() - 6);
	}

	private static String clean(String value) {
		return value == null ? "" : value.trim();
	}
}
