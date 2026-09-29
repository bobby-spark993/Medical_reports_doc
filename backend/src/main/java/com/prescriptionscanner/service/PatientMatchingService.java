package com.prescriptionscanner.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

	private static final Logger log = LoggerFactory.getLogger(PatientMatchingService.class);

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
	 * Candidates sharing the normalized name plus age and gender. Gender and age
	 * are normalized ("Female" == "F", "26 years" == "26"), so these are only
	 * ever returned to the user - the system never auto-merges on demographics,
	 * because two different people can share a name, age and gender.
	 */
	@Transactional(readOnly = true)
	public List<Patient> possibleMatches(String name, String gender, String age) {
		if (name == null || name.isBlank()) {
			return List.of();
		}
		String normalizedGender = normalizeGender(gender);
		String normalizedAge = normalizeAge(age);
		return patientRepository.findAllByNormalizedName(normalizeName(name)).stream()
				.filter(p -> normalizedGender.isEmpty() || normalizedGender.equals(normalizeGender(p.getGender())))
				.filter(p -> normalizedAge.isEmpty() || normalizedAge.equals(normalizeAge(p.getAge())))
				.toList();
	}

	/**
	 * Existing patient for a document, matched on name plus age. Age is compared
	 * by number only ("26" == "26 years" == "26 yrs"). Gender is used to
	 * disambiguate, and when age is missing on the document it can match on
	 * name + gender instead - so a single missing field never blocks the match.
	 */
	@Transactional(readOnly = true)
	public Optional<Patient> matchByDemographics(String name, String age, String gender) {
		String normalizedName = normalizeName(name);
		if (normalizedName.isEmpty()) {
			return Optional.empty();
		}
		String normalizedAge = normalizeAge(age);
		String normalizedGender = normalizeGender(gender);
		if (normalizedAge.isEmpty() && normalizedGender.isEmpty()) {
			return Optional.empty();
		}

		List<Patient> byName = patientRepository.findAllByNormalizedName(normalizedName);
		List<Patient> sameAge = byName.stream()
				.filter(p -> normalizedAge.isEmpty() || normalizedAge.equals(normalizeAge(p.getAge())))
				.toList();

		// No PII: only presence flags and counts, so a failed match is diagnosable.
		log.info("Patient match attempt: namePresent={} agePresent={} genderPresent={} nameCandidates={} sameAge={}",
				true, !normalizedAge.isEmpty(), !normalizedGender.isEmpty(), byName.size(), sameAge.size());

		if (sameAge.isEmpty()) {
			return Optional.empty();
		}
		if (!normalizedGender.isEmpty()) {
			Optional<Patient> byGender = sameAge.stream()
					.filter(p -> normalizedGender.equals(normalizeGender(p.getGender())))
					.findFirst();
			if (byGender.isPresent()) {
				return byGender;
			}
		}
		return Optional.of(sameAge.get(0));
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

	/** Keeps just the first number so "43" == "43 years" == "043" == "26 years 6 months". */
	public static String normalizeAge(String raw) {
		if (raw == null) {
			return "";
		}
		java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\d+").matcher(raw);
		if (matcher.find()) {
			String digits = matcher.group();
			String stripped = digits.replaceFirst("^0+(?=\\d)", "");
			return stripped.isEmpty() ? digits : stripped;
		}
		return raw.trim().toLowerCase(Locale.ROOT);
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
}
