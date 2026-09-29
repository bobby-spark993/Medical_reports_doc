package com.prescriptionscanner.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns whatever Gemini read off the page into a LocalDate.
 *
 * <p>Indian prescriptions write dates as dd/mm/yyyy most of the time, but
 * yyyy-mm-dd and "12-03-2026" also show up. Unparseable values become null
 * rather than a wrong date.
 */
public final class Dates {

	private static final Pattern ISO = Pattern.compile("^(\\d{4})-(\\d{1,2})-(\\d{1,2})");
	private static final Pattern DMY = Pattern.compile("^(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{2,4})");
	private static final Pattern ISO_DATETIME = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})T");

	private Dates() {
	}

	public static LocalDate parseFlexible(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String value = raw.trim();

		// Strip a time component if present.
		Matcher isoDateTime = ISO_DATETIME.matcher(value);
		if (isoDateTime.find()) {
			return safeParse(isoDateTime.group(1));
		}

		Matcher iso = ISO.matcher(value);
		if (iso.find()) {
			return build(iso.group(1), iso.group(2), iso.group(3));
		}

		Matcher dmy = DMY.matcher(value);
		if (dmy.find()) {
			String day = dmy.group(1);
			String month = dmy.group(2);
			String year = dmy.group(3);
			if (year.length() == 2) {
				year = "20" + year;
			}
			return build(year, month, day);
		}

		return null;
	}

	private static LocalDate build(String year, String month, String day) {
		try {
			int y = Integer.parseInt(year);
			int m = Integer.parseInt(month);
			int d = Integer.parseInt(day);
			if (m < 1 || m > 12 || d < 1 || d > 31 || y < 1900 || y > 2200) {
				return null;
			}
			return LocalDate.of(y, m, d);
		} catch (Exception ex) {
			return null;
		}
	}

	private static LocalDate safeParse(String value) {
		try {
			return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
		} catch (Exception ex) {
			return null;
		}
	}
}
