package com.prescriptionscanner.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.prescriptionscanner.domain.Appointment;
import com.prescriptionscanner.domain.Diagnosis;
import com.prescriptionscanner.domain.LabResult;
import com.prescriptionscanner.domain.Patient;
import com.prescriptionscanner.domain.PrescribedMedicine;
import com.prescriptionscanner.domain.Visit;

/**
 * Builds the patient report PDF (OpenPDF 3.x, whose packages moved from
 * com.lowagie.text to org.openpdf.text).
 *
 * <p><b>FONT NOTE:</b> the built-in Helvetica is WinAnsi-encoded and cannot
 * render Devanagari. English output is fine; for Hindi, register a .ttf such as
 * NotoSansDevanagari-Regular via {@code FontFactory.register(...)}.
 */
@Service
public class ReportService {

	private static final Logger log = LoggerFactory.getLogger(ReportService.class);

	private static final Color INK = new Color(17, 24, 39);
	private static final Color MUTED = new Color(107, 114, 128);
	private static final Color RED = new Color(185, 28, 28);
	private static final Color HEADER_BG = new Color(243, 244, 246);

	private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, INK);
	private static final Font WARNING = FontFactory.getFont(FontFactory.HELVETICA, 9, RED);
	private static final Font SMALL_MUTED = FontFactory.getFont(FontFactory.HELVETICA, 8, MUTED);
	private static final Font SECTION = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, INK);
	private static final Font LABEL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, MUTED);
	private static final Font VALUE = FontFactory.getFont(FontFactory.HELVETICA, 9.5f, INK);
	private static final Font SUBHEAD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, INK);
	private static final Font CELL = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, INK);
	private static final Font CELL_RED = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, RED);
	private static final Font CELL_HEAD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, MUTED);
	private static final Font ITALIC = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, MUTED);
	private static final Font FOOTER = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 7.5f, MUTED);

	private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

	private final PatientService patientService;

	public ReportService(PatientService patientService) {
		this.patientService = patientService;
	}

	/** Runs inside a read-only transaction so lazy collections can be walked. */
	@Transactional(readOnly = true)
	public byte[] buildPatientReport(Long patientId, String generatedBy) {
		Patient patient = patientService.requirePatient(patientId);
		List<Visit> visits = patientService.verifiedVisitsOldestFirst(patientId);
		List<Appointment> appointments = patientService.appointmentsOldestFirst(patientId);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Document doc = new Document(PageSize.A4, 50, 50, 50, 50);
		try {
			PdfWriter.getInstance(doc, out);
			doc.open();

			doc.add(new Paragraph("PrescriptionScanner", TITLE));
			doc.add(new Paragraph("CONFIDENTIAL MEDICAL RECORD - DPDP Act 2023", WARNING));
			doc.add(new Paragraph(
					"Generated " + formatInstant(Instant.now())
							+ (notBlank(generatedBy) ? " by " + generatedBy : ""),
					SMALL_MUTED));
			horizontalRule(doc);

			sectionTitle(doc, "Patient Details");
			labelValue(doc, "Name", patient.getName());
			labelValue(doc, "PID", patient.getPid());
			labelValue(doc, "Gender / Age", join(patient.getGender(), patient.getAge()));
			labelValue(doc, "Marital Status", patient.getMaritalStatus());
			labelValue(doc, "Phone", patient.getPhone());
			labelValue(doc, "Address", patient.getAddress());
			if (notBlank(patient.getAllergies())) {
				labelValue(doc, "ALLERGIES", patient.getAllergies());
			}
			horizontalRule(doc);

			if (visits.isEmpty()) {
				sectionTitle(doc, "Visit History");
				doc.add(new Paragraph("No verified visits recorded.", ITALIC));
			}

			for (Visit visit : visits) {
				sectionTitle(doc, "Visit - " + formatDate(visit.getVisitDate()));

				String meta = join(
						visit.getDoctor() == null ? null : "Dr. " + visit.getDoctor().getName(),
						visit.getDoctor() == null ? null : visit.getDoctor().getQualification(),
						visit.getMode(),
						visit.getAppointmentNo() == null ? null : "Appt " + visit.getAppointmentNo());
				if (notBlank(meta)) {
					doc.add(new Paragraph(meta, SUBHEAD));
				}
				labelValue(doc, "Valid up to", formatDate(visit.getValidUpTo()));
				labelValue(doc, "Follow-up", formatDate(visit.getFollowUpDate()));
				if (notBlank(visit.getNotes())) {
					labelValue(doc, "Notes", visit.getNotes());
				}

				if (!visit.getDiagnoses().isEmpty()) {
					doc.add(new Paragraph("Diagnosis", SUBHEAD));
					for (Diagnosis diagnosis : visit.getDiagnoses()) {
						doc.add(new Paragraph("- " + diagnosis.getDescription(), VALUE));
					}
				}

				if (!visit.getMedicines().isEmpty()) {
					doc.add(new Paragraph("Prescription", SUBHEAD));
					PdfPTable table = table(new float[] { 3.4f, 1.6f, 2.0f, 1.6f, 1.4f });
					headerRow(table, "Medicine", "Dose", "Frequency", "Duration", "Instructions");
					for (PrescribedMedicine medicine : visit.getMedicines()) {
						row(table, CELL,
								medicine.getName(),
								orDash(medicine.getDose()),
								orDash(medicine.getFrequency()),
								orDash(medicine.getDuration()),
								orDash(medicine.getInstructions()));
					}
					doc.add(table);
				}

				if (!visit.getLabResults().isEmpty()) {
					doc.add(new Paragraph("Lab Results", SUBHEAD));
					PdfPTable table = table(new float[] { 3.4f, 1.8f, 1.4f, 2.0f, 1.4f });
					headerRow(table, "Test", "Value", "Unit", "Reference", "Flag");
					for (LabResult lab : visit.getLabResults()) {
						row(table, lab.isAbnormal() ? CELL_RED : CELL,
								lab.getTestName(),
								orDash(lab.getValue()),
								orDash(lab.getUnit()),
								orDash(lab.getReferenceRange()),
								lab.isAbnormal() ? "ABNORMAL" : "");
					}
					doc.add(table);
				}

				horizontalRule(doc);
			}

			if (!appointments.isEmpty()) {
				sectionTitle(doc, "Appointments");
				PdfPTable table = table(new float[] { 3.0f, 3.5f, 2.0f, 1.5f });
				headerRow(table, "Scheduled", "Doctor", "Status", "Notes");
				for (Appointment appointment : appointments) {
					row(table, CELL,
							formatDateTime(appointment.getScheduledAt()),
							appointment.getDoctor() == null ? "-"
									: "Dr. " + appointment.getDoctor().getName(),
							appointment.getStatus() == null ? "-" : appointment.getStatus().value(),
							orDash(appointment.getNotes()));
				}
				doc.add(table);
			}

			Paragraph footer = new Paragraph(
					"This report was generated electronically. Verify against the original prescription "
							+ "before clinical use.",
					FOOTER);
			footer.setAlignment(Element.ALIGN_CENTER);
			doc.add(footer);

			doc.close();
			return out.toByteArray();

		} catch (Exception ex) {
			log.error("Could not build the patient report PDF", ex);
			if (doc.isOpen()) {
				doc.close();
			}
			throw new IllegalStateException("Could not build the patient report", ex);
		}
	}

	// --- helpers -------------------------------------------------------------

	private void sectionTitle(Document doc, String text) throws DocumentException {
		Paragraph p = new Paragraph(text.toUpperCase(), SECTION);
		p.setSpacingBefore(10);
		p.setSpacingAfter(4);
		doc.add(p);
	}

	private void labelValue(Document doc, String label, String value) throws DocumentException {
		Paragraph p = new Paragraph();
		p.add(new Phrase(label + ": ", LABEL));
		p.add(new Phrase(notBlank(value) ? value : "-", VALUE));
		p.setSpacingAfter(2);
		doc.add(p);
	}

	private void horizontalRule(Document doc) throws DocumentException {
		PdfPTable rule = new PdfPTable(1);
		rule.setWidthPercentage(100);
		rule.setSpacingBefore(8);
		rule.setSpacingAfter(4);
		PdfPCell cell = new PdfPCell(new Phrase(" ", SMALL_MUTED));
		cell.setBorderWidthTop(0.5f);
		cell.setBorderWidthBottom(0);
		cell.setBorderWidthLeft(0);
		cell.setBorderWidthRight(0);
		cell.setBorderColor(INK);
		cell.setFixedHeight(2f);
		rule.addCell(cell);
		doc.add(rule);
	}

	private PdfPTable table(float[] widths) throws DocumentException {
		PdfPTable table = new PdfPTable(widths);
		table.setWidthPercentage(100);
		table.setSpacingBefore(2);
		table.setSpacingAfter(6);
		return table;
	}

	private void headerRow(PdfPTable table, String... labels) {
		for (String label : labels) {
			PdfPCell cell = new PdfPCell(new Phrase(label.toUpperCase(), CELL_HEAD));
			cell.setBackgroundColor(HEADER_BG);
			cell.setPadding(4);
			cell.setBorderWidth(0.4f);
			table.addCell(cell);
		}
	}

	private void row(PdfPTable table, Font font, String... values) {
		for (String value : values) {
			PdfPCell cell = new PdfPCell(new Phrase(value == null || value.isBlank() ? "-" : value, font));
			cell.setPadding(4);
			cell.setBorderWidth(0.4f);
			table.addCell(cell);
		}
	}

	private static String orDash(String value) {
		return notBlank(value) ? value : "-";
	}

	private static boolean notBlank(String value) {
		return value != null && !value.isBlank();
	}

	private static String join(String... parts) {
		StringBuilder sb = new StringBuilder();
		for (String part : parts) {
			if (notBlank(part)) {
				if (!sb.isEmpty()) {
					sb.append("  |  ");
				}
				sb.append(part);
			}
		}
		return sb.toString();
	}

	private static String formatDate(LocalDate date) {
		return date == null ? "-" : date.format(DATE_FMT);
	}

	private static String formatDateTime(LocalDateTime dateTime) {
		return dateTime == null ? "-" : dateTime.format(DATETIME_FMT);
	}

	private static String formatInstant(Instant instant) {
		return instant == null ? "-"
				: LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).format(DATETIME_FMT);
	}
}
