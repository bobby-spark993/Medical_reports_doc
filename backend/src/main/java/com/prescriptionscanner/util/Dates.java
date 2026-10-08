package com.prescriptionscanner.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns whatever Gemini read off the page into a LocalDate.
 *
 * <p>Indian prescriptions write dates as dd/mm/yyyy most of the time, but
 * yyyy-mm-dd, "12-03-2026", and month-name forms like "04 Apr 2026" or
 * "Apr 04, 2026" also show up. Unparseable values become null rather than a
 * wrong date.
 */
public final class Dates {

	private static final Pattern ISO = Pattern.compile("^(\\d{4})-(\\d{1,2})-(\\d{1,2})");
	private static final Pattern DMY = Pattern.compile("^(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{2,4})");
	private static final Pattern DMY_NAME = Pattern.compile("^(\\d{1,2})[\\s\\-.]+([A-Za-z]{3,9})\\.?[\\s\\-.]+(\\d{2,4})");
	private static final Pattern MDY_NAME = Pattern.compile("^([A-Za-z]{3,9})\\.?[\\s\\-.]+(\\d{1,2}),?[\\s\\-.]+(\\d{2,4})");
	private static final Pattern ISO_DATETIME = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})T");

	private static final Map<String, Integer> MONTHS = Map.ofEntries(
			Map.entry("jan", 1), Map.entry("feb", 2), Map.entry("mar", 3), Map.entry("apr", 4),
			Map.entry("may", 5), Map.entry("jun", 6), Map.entry("jul", 7), Map.entry("aug", 8),
			Map.entry("sep", 9), Map.entry("oct", 10), Map.entry("nov", 11), Map.entry("dec", 12));

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
			return build(normalizeYear(dmy.group(3)), dmy.group(2), dmy.group(1));
		}

		// "04 Apr 2026" / "4 April 2026".
		Matcher dmyName = DMY_NAME.matcher(value);
		if (dmyName.find()) {
			return buildMonthName(normalizeYear(dmyName.group(3)), dmyName.group(2), dmyName.group(1));
		}

		// "Apr 04, 2026" / "April 4 2026".
		Matcher mdyName = MDY_NAME.matcher(value);
		if (mdyName.find()) {
			return buildMonthName(normalizeYear(mdyName.group(3)), mdyName.group(1), mdyName.group(2));
		}

		return null;
	}

	private static String normalizeYear(String year) {
		return year.length() == 2 ? "20" + year : year;
	}

	private static LocalDate buildMonthName(String year, String monthName, String day) {
		Integer month = MONTHS.get(monthName.length() >= 3
				? monthName.substring(0, 3).toLowerCase(Locale.ROOT)
				: monthName.toLowerCase(Locale.ROOT));
		if (month == null) {
			return null;
		}
		return build(year, String.valueOf(month), day);
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
