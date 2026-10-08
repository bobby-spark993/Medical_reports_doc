package com.prescriptionscanner.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class DatesTest {

	@Test
	void parsesIsoAndSlashAndDashDates() {
		assertThat(Dates.parseFlexible("2026-04-04")).isEqualTo(LocalDate.of(2026, 4, 4));
		assertThat(Dates.parseFlexible("04/04/2026")).isEqualTo(LocalDate.of(2026, 4, 4));
		assertThat(Dates.parseFlexible("4-4-26")).isEqualTo(LocalDate.of(2026, 4, 4));
		assertThat(Dates.parseFlexible("04.04.2026")).isEqualTo(LocalDate.of(2026, 4, 4));
	}

	@Test
	void parsesDayMonthNameYearAsPrinted() {
		assertThat(Dates.parseFlexible("04 Apr 2026")).isEqualTo(LocalDate.of(2026, 4, 4));
		assertThat(Dates.parseFlexible("4 April 2026")).isEqualTo(LocalDate.of(2026, 4, 4));
		assertThat(Dates.parseFlexible("04-Apr-2026")).isEqualTo(LocalDate.of(2026, 4, 4));
		assertThat(Dates.parseFlexible("7 Sep 25")).isEqualTo(LocalDate.of(2025, 9, 7));
		assertThat(Dates.parseFlexible("04 Apr 2026 10:30 AM")).isEqualTo(LocalDate.of(2026, 4, 4));
	}

	@Test
	void parsesMonthNameDayYear() {
		assertThat(Dates.parseFlexible("Apr 04, 2026")).isEqualTo(LocalDate.of(2026, 4, 4));
		assertThat(Dates.parseFlexible("April 4 2026")).isEqualTo(LocalDate.of(2026, 4, 4));
	}

	@Test
	void returnsNullForUnreadableOrMissing() {
		assertThat(Dates.parseFlexible(null)).isNull();
		assertThat(Dates.parseFlexible("")).isNull();
		assertThat(Dates.parseFlexible("   ")).isNull();
		assertThat(Dates.parseFlexible("not a date")).isNull();
		assertThat(Dates.parseFlexible("32/13/2026")).isNull();
	}
}
