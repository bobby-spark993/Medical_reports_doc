package com.prescriptionscanner.dto;

import java.time.LocalDate;

/**
 * Aggregated per-patient visit stats.
 *
 * <p>Must stay a top-level type: it is referenced from a JPQL constructor
 * expression, and nested classes use a "$" in their binary name which JPQL
 * cannot express.
 */
public record PatientVisitStats(Long patientId, Long visitCount, LocalDate nextFollowUp) {
}
