package com.prescriptionscanner.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.prescriptionscanner.domain.Visit;
import com.prescriptionscanner.dto.GeminiExtraction;

import tools.jackson.databind.ObjectMapper;

/**
 * The PID rule: the document the PID was printed on is the prescription, every
 * other document is a report - both when a page is scanned and when a saved
 * document is read back for the patient folder.
 */
class DocumentClassifierTest {

	private DocumentClassifier classifier;

	@BeforeEach
	void setUp() {
		classifier = new DocumentClassifier(new ObjectMapper());
	}

	private Visit visit(String rawJson, String reviewedJson) {
		Visit visit = new Visit();
		visit.setRawAiJson(rawJson);
		visit.setReviewedJson(reviewedJson);
		return visit;
	}

	private static String extraction(String documentType, String pid) {
		return """
				{
				  "document_type": "%s",
				  "patient": { "name": "RAMNIWASH THAKUR", "pid": %s }
				}
				""".formatted(documentType, pid == null ? "null" : "\"" + pid + "\"");
	}

	@Test
	void pidPrintedOnTheScanMakesItThePrescription() {
		String json = extraction("LAB_REPORT", "SNP260406071902");

		assertThat(classifier.typeOf(visit(json, null))).isEqualTo(DocumentClassifier.PRESCRIPTION);
	}

	@Test
	void aReportWithoutAPidStaysAReport() {
		String json = extraction("LAB_REPORT", null);

		assertThat(classifier.typeOf(visit(json, null))).isEqualTo("LAB_REPORT");
	}

	@Test
	void theReviewedJsonIsOnlyUsedWhenTheScanJsonIsMissing() {
		// A lab report the reviewer typed a PID into stays a report: the PID was
		// never printed on the scanned page.
		String scanned = extraction("LAB_REPORT", null);
		String reviewed = extraction("LAB_REPORT", "SNP260406071902");

		assertThat(classifier.typeOf(visit(scanned, reviewed))).isEqualTo("LAB_REPORT");
	}

	@Test
	void theReviewedJsonIsUsedWhenTheScanJsonIsMissing() {
		String reviewed = extraction("PRESCRIPTION", null);

		assertThat(classifier.typeOf(visit(null, reviewed))).isEqualTo("PRESCRIPTION");
	}

	@Test
	void aDocumentWithoutAnyStoredJsonHasNoType() {
		assertThat(classifier.typeOf(visit(null, null))).isNull();
		assertThat(classifier.typeOf(visit("not json", "also not json"))).isNull();
	}

	@Test
	void aPidIsRecognisedWhateverSpacingOrCasingTheOcrProduced() {
		assertThat(DocumentClassifier.isPrescriptionPid("SNP260406071902")).isTrue();
		assertThat(DocumentClassifier.isPrescriptionPid("snp260406071902")).isTrue();
		assertThat(DocumentClassifier.isPrescriptionPid("SNP 260406071902")).isTrue();
		assertThat(DocumentClassifier.isPrescriptionPid("SNP-260406-071902")).isTrue();
		// A bare reference number is not a PID.
		assertThat(DocumentClassifier.isPrescriptionPid("071902")).isFalse();
		assertThat(DocumentClassifier.isPrescriptionPid(null)).isFalse();
	}

	@Test
	void anExtractionWithAPidIsClassifiedAsAPrescription() {
		GeminiExtraction extraction = new GeminiExtraction(
				"LAB_REPORT", null, null,
				new GeminiExtraction.PatientInfo("SNP 260406071902", null, "RAMNIWASH THAKUR", "MALE",
						"41 Years", null, null, null, null, null, null),
				null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
				null, null, null);

		assertThat(classifier.decideType(extraction)).isEqualTo(DocumentClassifier.PRESCRIPTION);
		assertThat(classifier.apply(extraction).documentType()).isEqualTo(DocumentClassifier.PRESCRIPTION);
	}
}
