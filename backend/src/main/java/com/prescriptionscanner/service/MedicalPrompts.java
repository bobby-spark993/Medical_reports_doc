package com.prescriptionscanner.service;

/**
 * All Gemini prompts and the JSON schemas they target, kept in one place so the
 * extraction contract is easy to review.
 */
public final class MedicalPrompts {

	private MedicalPrompts() {
	}

	/** System prompt + inline schema for the legacy /api/prescriptions flow. */
	public static final String MEDICAL_PROMPT_PATH = "prompts/medical-extraction.txt";
	public static final String MEDICAL_SCHEMA_PATH = "prompts/medical-extraction-schema.json";

	/**
	 * Structured response schema for the legacy extraction. Every field the
	 * model returns is declared here so Jackson can map it onto
	 * {@code GeminiExtraction}.
	 */
	public static final String EXTRACTION_SCHEMA_PATH = "prompts/prescription-extraction-schema.json";

	public static final String SYSTEM_PROMPT = """
			You are a medical-document data-extraction service for Indian clinics and diagnostic centres.

			Each scan is ONE page. First decide which kind of page it is:
			1. PRESCRIPTION - doctor's letterhead (English + Hindi) with a patient box (NAME, GENDER, AGE,
			   ADDRESS, Marital Status, PID, Pt. Regd. Valid Up To, Appt. Date, Valid Up To, Appointment No,
			   ONLINE/OFFLINE) followed by the doctor's HANDWRITTEN notes.
			2. LAB_REPORT - pathology report (Blood Sugar, CBC, ESR, LFT, KFT, etc.) laid out as a table:
			   Investigation / Result / Unit / Expected Value, with (L) or (H) printed next to abnormal results.
			3. RADIOLOGY_REPORT - CT, MRI, X-ray, USG, ECHO or similar report with Examination, Protocol,
			   Observations and Impression, signed by a radiologist.
			4. OTHER - anything else (discharge summary, certificate, etc.).

			A page that prints a PID in the form "SNP" followed by 12 digits (e.g. SNP260404071826) is always a
			PRESCRIPTION. A page with no such PID is a report (LAB_REPORT or RADIOLOGY_REPORT).

			Return ONLY JSON matching the requested schema. Use null (or an empty array) for anything not present.
			NEVER guess illegible handwriting. If a value is hard to read, still put your best reading in its
			field AND add its exact path to uncertain_fields so a human can check it.
			Do not invent diagnoses, medicines, or lab values that are not written on the document.
			Copy values exactly as written; do not translate or reformat units or dates.
			Ignore signatures, stamps, logos, barcodes and QR codes. Capture legal footers such as
			"Not Valid For Medico Legal Purpose" in facility.note.
			Put every handwritten item on the page into the "notes" field, one item per line with a short label,
			even when it is also captured in a structured field.
			""";

	public static final String USER_PROMPT = """
			Read this scanned Indian medical page (Hindi/English, printed + handwritten).
			Return ONLY JSON in the given schema. Use null for anything not present. NEVER guess illegible
			handwriting: list its path in uncertain_fields instead.

			Schema:
			{
			  "document_type": "PRESCRIPTION" | "LAB_REPORT" | "RADIOLOGY_REPORT" | "OTHER",
			  "document_title": string|null,
			  "facility": { "name": string|null, "address": string|null, "phone": string[], "mobile": string[], "email": string|null, "website": string|null, "note": string|null,
			                "timings": string|null, "closed_days": string|null, "services": string[], "powered_by": string|null },
			  "patient": {
			    "pid": string|null,
			    "patient_ref_no": string|null,
			    "name": string|null, "gender": string|null, "age": string|null, "age_years": integer|null,
			    "marital_status": string|null, "address": string|null, "pt_regd_valid_upto": string|null,
			    "lab_id": string|null, "barcode_text": string|null
			  },
			  "doctor": { "name": string|null, "qualification": string|null, "experience": string|null, "registration_no": string|null, "designation": string|null },
			  "referred_by": string|null,
			  "appointment": { "date": string|null, "time": string|null, "valid_upto": string|null, "appointment_no": string|null, "mode": string|null },
			  "report": { "report_id": string|null, "received_on": string|null, "reported_on": string|null, "report_date": string|null,
			              "signed_by": string|null, "signed_by_designation": string|null, "technician": string|null },
			  "chief_complaints": string[],
			  "examination": string[],
			  "diagnoses": string[],
			  "medicines": [ { "name": string, "dose": string|null, "frequency": string|null, "duration": string|null, "instructions": string|null } ],
			  "investigations_advised": string[],
			  "advice": string[],
			  "follow_up_date": string|null,
			  "lab_results": [ { "group": string|null, "test": string, "value": string|null, "unit": string|null,
			                     "reference_range": string|null, "flag": "L"|"H"|null, "abnormal": boolean, "remark": string|null } ],
			  "radiology": { "examination": string|null, "protocol": string|null, "observations": string[], "impression": string|null },
			  "handwriting_confidence": "LOW" | "MEDIUM" | "HIGH" | null,
			  "handwritten_present": boolean|null,
			  "notes": string|null,
			  "abnormal_findings": string[],
			  "warnings": string[],
			  "uncertain_fields": string[]
			}

			Rules for every page:
			- Dates: keep them as written (e.g. "04/04/2026", "04 Apr 2026"). Do not reformat.
			- "uncertain_fields" uses dotted paths from the root, e.g. "patient.age", "medicines[0].frequency".
			  Put only paths there, never values or explanations.
			- "patient.age" is copied as written (e.g. "43 Years", "12 YRS."); "age_years" is just the number.
			- The PID is ONLY the value printed immediately after the literal label "PID:". No other number on
			  the page is ever the PID: not the barcode number, not a registration / appointment / report / ref
			  number, and not the trailing digits after a name. If there is no "PID:" label, use null.
			- Fill only the sections that belong to the page type; leave the others null / empty.
			- A PID printed as "SNP" + 12 digits means the page is a PRESCRIPTION; a page without that PID is a
			  report. Set document_type accordingly.
			- Copy text exactly as printed, including spelling and number formats; never correct or guess.
			- If a printed value is present but illegible, put the literal "UNREADABLE" in its field and add
			  its path to uncertain_fields. If a field is absent, use null (or an empty array); never invent it.

			Rules for PRESCRIPTION pages (this page is the MASTER record, extract every field):
			- "patient.pid" is ONLY the value printed immediately after the literal label "PID:" (e.g.
			  "SNP260404071824"). Never use any other number as the PID. If there is no "PID:" label, set
			  patient.pid to null.
			- Copy name, gender (FEMALE/MALE as written), age, address (e.g. "BANA,GARHWA"), marital status,
			  "Pt. Regd. Valid Up To" into patient.pt_regd_valid_upto.
			- Always capture patient.name, patient.gender and patient.age (or patient.age_years) on a
			  prescription: these identify the patient folder. If a value is truly unreadable, still put
			  your best reading and add its path to uncertain_fields.
			- appointment.date = "Appt. Date", appointment.time = the appointment time if one is printed,
			  appointment.valid_upto = "Valid Up To", appointment.appointment_no =
			  the circled number, appointment.mode = the bracketed word (OFFLINE / ONLINE).
			- doctor = the letterhead doctor (name, qualification such as M.B.B.S., D.P.M., FIPS, MIEA,
			  experience such as "Ex-Senior Resident (C.I.P. Kanke, Ranchi)", registration number,
			  designation such as Consultant Neuropsychiatrist).
			- facility = the clinic name, address, phones, mobiles, email, website and any legal note on the
			  letterhead (prefer the English text if both languages are printed; otherwise copy the Hindi as
			  written). Put landline numbers in facility.phone and mobile numbers in facility.mobile; a footer
			  such as "Not Valid For Medico Legal Purpose" goes to facility.note.
			- HANDWRITING: read every handwritten line. Put the complete transcription in "notes", one item per
			  line, in the original language and order, with labels such as "C/O:", "O/E:", "Dx:", "Rx:",
			  "Advice:", "Follow-up:" when the doctor groups it that way. "C/O" or "C/o" means complaints and goes to
			  chief_complaints, "O/E" goes to examination, "Rx" lines go to medicines, "Adv" goes to advice,
			  "Ix" / investigations go to investigations_advised. Fill these structured fields only for lines you
			  can read reasonably; still keep them in "notes" too.
			- Medicines: "dose" is strength (e.g. "0.5 mg"), "frequency" is timing (e.g. "1-0-1 after food").
			  Doctors' abbreviations (OD, BD, HS, SOS, tab, cap, syp) are copied as written.
			- Set handwriting_confidence to LOW if most of the handwriting is unclear, MEDIUM if partly clear,
			  HIGH if clearly legible. Use null if nothing is handwritten.

			Rules for LAB_REPORT pages (extract only the PRINTED fields; do not interpret handwriting):
			- Set handwritten_present = true if there is any handwritten mark on the page, otherwise false.
			- The name line looks like "SUSHILA DEVI 071824". Put only the name in patient.name and the trailing
			  number in patient.patient_ref_no (this number is the last 6 digits of the prescription PID).
			  Set patient.pid to null unless a value is printed after a literal "PID:" label.
			- patient.lab_id = the lab / sample / report ID printed for this report (e.g. "20260404046").
			  patient.barcode_text = the human-readable text printed under or beside a barcode / QR code, if legible.
			- report.report_id = the lab report number printed on the page. "Received on" goes to
			  report.received_on, "Reported on" to report.reported_on, any report date to report.report_date.
			  "Refd. by" goes to referred_by.
			- report.signed_by = the pathologist (e.g. "Dr. ZAARA NASEEM"), report.signed_by_designation =
			  "M.D. PATHOLOGIST, Consultant Pathologist", report.technician = the lab technician name or
			  signature if printed.
			- facility = the lab name, address, phones/mobiles, email, website. facility.note holds disclaimers
			  and footer text such as "Not Valid For Medico Legal Purposes", facility.timings holds any
			  visiting / sample-collection hours, facility.closed_days holds weekly-off / closed days,
			  facility.services lists the tests or services offered, facility.powered_by holds any
			  "Powered by ..." / software text.
			- One entry in lab_results per printed test row. "group" is the underlined section heading above it
			  (e.g. "Complete Blood Count", "D.C. of W.B.C.", "R.B.C. Indices", "Platelet Indices"); use null
			  for tests without a heading (e.g. "Blood Sugar (Random)", "E.S.R. First hr."). Keep every heading.
			- "value", "unit" and "reference_range" are copied exactly as printed (e.g. "14.6 gm/dl = 100%",
			  "< 20 mm", "0.108 - 0.282"). Extra text under a value, such as "(72%)" below haemoglobin, goes
			  to "remark".
			- "flag" is "L" or "H" only when (L) or (H) is printed beside the result; then "abnormal" is true.
			  Never compute the flag yourself by comparing with the reference range.
			- abnormal_findings: one short line per result that is out of range or marked (L)/(H),
			  e.g. "Haemoglobin 9.2 gm/dl (L)". Leave empty when every result is normal.
			- warnings: flag any printed problem a human should check, such as a patient name/age/ID that does
			  not match across the page. Leave empty when there is nothing to warn about.
			- document_title = the panel name if there is one (e.g. "Complete Blood Count"), else the test name.
			- Do not output a diagnosis, treatment advice or medicine suggestion.

			Rules for RADIOLOGY_REPORT pages:
			- patient.name, gender ("Sex"), age come from the header; there is usually NO PID and NO ref number,
			  so set them to null. "Refd. by Doctor" goes to referred_by. "Date" goes to report.report_date.
			- radiology.examination = the "Examination of" line, protocol = the Protocol paragraph,
			  observations = one array item per observation line, impression = the IMPRESSION text.
			- report.signed_by / signed_by_designation = the radiologist (name + qualification, designation).
			- Do not summarise or reword the observations or impression; copy them.
			""";
}
