package com.prescriptionscanner.service;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.prescriptionscanner.dto.GeminiExtraction;

/**
 * Decides a scanned page's document type from its printed PID, as required by
 * the clinic: a page whose PID is printed in the "SNP" + 12-digit format is a
 * prescription; every other page is a report. This overrides the model's own
 * document_type guess.
 */
@Component
public class DocumentClassifier {

	public static final String PRESCRIPTION = "PRESCRIPTION";
	public static final String REPORT = "LAB_REPORT";

	/** e.g. SNP260404071826. */
	static final Pattern PRESCRIPTION_PID = Pattern.compile("^SNP\\d{12}$", Pattern.CASE_INSENSITIVE);

	public boolean isPrescription(GeminiExtraction extraction) {
		if (extraction == null || extraction.patient() == null || extraction.patient().pid() == null) {
			return false;
		}
		return PRESCRIPTION_PID.matcher(extraction.patient().pid().trim()).matches();
	}

	public String decideType(GeminiExtraction extraction) {
		return isPrescription(extraction) ? PRESCRIPTION : REPORT;
	}

	/** Returns the same extraction with document_type forced by the PID rule. */
	public GeminiExtraction apply(GeminiExtraction extraction) {
		if (extraction == null) {
			return null;
		}
		return extraction.withDocumentType(decideType(extraction));
	}
}
