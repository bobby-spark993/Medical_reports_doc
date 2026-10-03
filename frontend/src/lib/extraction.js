// Maps the Gemini extraction payload (GeminiExtraction on the backend) to the
// editable shape used by the review form, and back to a VerifyRequest.

const TEXT_KEYS = ['description', 'diagnosis', 'name', 'test', 'value', 'text', 'finding', 'investigation']

function blankToEmpty(value) {
  return value ?? ''
}

function asStringList(value) {
  if (Array.isArray(value)) return value.map((item) => (item == null ? '' : String(item)))
  if (value == null) return []
  return [String(value)]
}

function cleanList(list) {
  return (list ?? []).map((item) => (item ?? '').trim()).filter((item) => item !== '')
}

function handwrittenToLabel(value) {
  if (value === true || value === 'YES' || value === 'true') return 'YES'
  if (value === false || value === 'NO' || value === 'false') return 'NO'
  return ''
}

function diagnosisToString(node) {
  if (node === null || node === undefined) return ''
  if (typeof node === 'string') return node
  if (typeof node === 'number' || typeof node === 'boolean') return String(node)
  if (typeof node === 'object') {
    for (const key of TEXT_KEYS) {
      const value = node[key]
      if (typeof value === 'string' && value.trim()) return value
    }
    for (const value of Object.values(node)) {
      if ((typeof value === 'string' || typeof value === 'number') && String(value).trim()) {
        return String(value)
      }
    }
  }
  return ''
}

// The backend serialises GeminiExtraction with snake_case names (see the
// @JsonProperty annotations), so read those first and fall back to camelCase.
function pick(source, snakeKey, camelKey) {
  const value = source?.[snakeKey]
  return value === undefined || value === null ? source?.[camelKey] : value
}

export function buildForm(extracted) {
  const e = extracted ?? {}
  const patient = e.patient ?? {}
  const doctor = e.doctor ?? {}
  const facility = e.facility ?? {}
  const appointment = e.appointment ?? {}
  const report = e.report ?? {}
  const radiology = e.radiology ?? {}

  // Reports have no "Appt. Date"; fall back to the report dates so the record
  // still lands on the patient's timeline date-wise.
  const reportDate = pick(report, 'report_date', 'reportDate')
    || pick(report, 'received_on', 'receivedOn')
    || pick(report, 'reported_on', 'reportedOn')

  return {
    documentType: blankToEmpty(pick(e, 'document_type', 'documentType')),
    documentTitle: blankToEmpty(pick(e, 'document_title', 'documentTitle')),
    facility: {
      name: blankToEmpty(facility.name),
      address: blankToEmpty(facility.address),
      phone: asStringList(facility.phone).join(', '),
      mobile: asStringList(facility.mobile).join(', '),
      email: blankToEmpty(facility.email),
      website: blankToEmpty(facility.website),
      note: blankToEmpty(facility.note),
      timings: blankToEmpty(facility.timings),
      closedDays: blankToEmpty(pick(facility, 'closed_days', 'closedDays')),
      services: asStringList(facility.services).join(', '),
      poweredBy: blankToEmpty(pick(facility, 'powered_by', 'poweredBy')),
    },
    patient: {
      pid: blankToEmpty(patient.pid),
      patientRefNo: blankToEmpty(pick(patient, 'patient_ref_no', 'patientRefNo')),
      name: blankToEmpty(patient.name),
      gender: blankToEmpty(patient.gender),
      age: blankToEmpty(patient.age),
      ageYears: pick(patient, 'age_years', 'ageYears') ?? '',
      maritalStatus: blankToEmpty(pick(patient, 'marital_status', 'maritalStatus')),
      address: blankToEmpty(patient.address),
      ptRegdValidUpto: blankToEmpty(pick(patient, 'pt_regd_valid_upto', 'ptRegdValidUpto')),
      labId: blankToEmpty(pick(patient, 'lab_id', 'labId')),
      barcodeText: blankToEmpty(pick(patient, 'barcode_text', 'barcodeText')),
      phone: '',
      allergies: '',
    },
    doctor: {
      name: blankToEmpty(doctor.name),
      qualification: blankToEmpty(doctor.qualification),
      experience: blankToEmpty(doctor.experience),
      registrationNo: blankToEmpty(pick(doctor, 'registration_no', 'registrationNo')),
      designation: blankToEmpty(doctor.designation),
      clinic: blankToEmpty(facility.name),
    },
    referredBy: blankToEmpty(pick(e, 'referred_by', 'referredBy')),
    visit: {
      visitDate: blankToEmpty(appointment.date || reportDate),
      visitTime: blankToEmpty(appointment.time),
      validUpTo: blankToEmpty(pick(appointment, 'valid_upto', 'validUpto')),
      appointmentNo: blankToEmpty(pick(appointment, 'appointment_no', 'appointmentNo')),
      mode: blankToEmpty(appointment.mode),
      followUpDate: blankToEmpty(pick(e, 'follow_up_date', 'followUpDate')),
      notes: blankToEmpty(e.notes),
    },
    report: {
      reportId: blankToEmpty(pick(report, 'report_id', 'reportId')),
      receivedOn: blankToEmpty(pick(report, 'received_on', 'receivedOn')),
      reportedOn: blankToEmpty(pick(report, 'reported_on', 'reportedOn')),
      reportDate: blankToEmpty(pick(report, 'report_date', 'reportDate')),
      signedBy: blankToEmpty(pick(report, 'signed_by', 'signedBy')),
      signedByDesignation: blankToEmpty(pick(report, 'signed_by_designation', 'signedByDesignation')),
      technician: blankToEmpty(report.technician),
    },
    chiefComplaints: asStringList(pick(e, 'chief_complaints', 'chiefComplaints')),
    examination: asStringList(e.examination),
    diagnoses: (e.diagnoses ?? []).map(diagnosisToString).filter((text) => text.trim()),
    medicines: (e.medicines ?? []).map((m) => ({
      name: blankToEmpty(m.name),
      dose: blankToEmpty(m.dose),
      frequency: blankToEmpty(m.frequency),
      duration: blankToEmpty(m.duration),
      instructions: blankToEmpty(m.instructions),
    })),
    investigationsAdvised: asStringList(pick(e, 'investigations_advised', 'investigationsAdvised')),
    advice: asStringList(e.advice),
    labResults: (pick(e, 'lab_results', 'labResults') ?? []).map((l) => ({
      group: blankToEmpty(l.group),
      testName: blankToEmpty(l.test),
      value: blankToEmpty(l.value),
      unit: blankToEmpty(l.unit),
      referenceRange: blankToEmpty(pick(l, 'reference_range', 'referenceRange')),
      flag: blankToEmpty(l.flag),
      remark: blankToEmpty(l.remark),
      isAbnormal: Boolean(l.abnormal),
    })),
    radiology: {
      examination: blankToEmpty(radiology.examination),
      protocol: blankToEmpty(radiology.protocol),
      observations: asStringList(radiology.observations),
      impression: blankToEmpty(radiology.impression),
    },
    handwritingConfidence: blankToEmpty(pick(e, 'handwriting_confidence', 'handwritingConfidence')),
    handwrittenPresent: handwrittenToLabel(pick(e, 'handwritten_present', 'handwrittenPresent')),
    abnormalFindings: asStringList(pick(e, 'abnormal_findings', 'abnormalFindings')),
    warnings: asStringList(e.warnings),
    uncertainFields: asStringList(pick(e, 'uncertain_fields', 'uncertainFields')),
  }
}

function trimOrNull(value) {
  const trimmed = (value ?? '').toString().trim()
  return trimmed === '' ? null : trimmed
}

function toIntOrNull(value) {
  const trimmed = (value ?? '').toString().trim()
  if (trimmed === '') return null
  const parsed = Number(trimmed)
  return Number.isFinite(parsed) ? Math.trunc(parsed) : null
}

function csvList(value) {
  return (value ?? '')
    .split(',')
    .map((item) => item.trim())
    .filter((item) => item !== '')
}

/** The reviewed extraction in the same snake_case shape the AI schema uses. */
export function buildReviewedJson(form) {
  return {
    document_type: trimOrNull(form.documentType),
    document_title: trimOrNull(form.documentTitle),
    facility: {
      name: trimOrNull(form.facility.name),
      address: trimOrNull(form.facility.address),
      phone: csvList(form.facility.phone),
      mobile: csvList(form.facility.mobile),
      email: trimOrNull(form.facility.email),
      website: trimOrNull(form.facility.website),
      note: trimOrNull(form.facility.note),
      timings: trimOrNull(form.facility.timings),
      closed_days: trimOrNull(form.facility.closedDays),
      services: csvList(form.facility.services),
      powered_by: trimOrNull(form.facility.poweredBy),
    },
    patient: {
      pid: trimOrNull(form.patient.pid),
      patient_ref_no: trimOrNull(form.patient.patientRefNo),
      name: trimOrNull(form.patient.name),
      gender: trimOrNull(form.patient.gender),
      age: trimOrNull(form.patient.age),
      age_years: toIntOrNull(form.patient.ageYears),
      marital_status: trimOrNull(form.patient.maritalStatus),
      address: trimOrNull(form.patient.address),
      pt_regd_valid_upto: trimOrNull(form.patient.ptRegdValidUpto),
      lab_id: trimOrNull(form.patient.labId),
      barcode_text: trimOrNull(form.patient.barcodeText),
    },
    doctor: {
      name: trimOrNull(form.doctor.name),
      qualification: trimOrNull(form.doctor.qualification),
      experience: trimOrNull(form.doctor.experience),
      registration_no: trimOrNull(form.doctor.registrationNo),
      designation: trimOrNull(form.doctor.designation),
    },
    referred_by: trimOrNull(form.referredBy),
    appointment: {
      date: trimOrNull(form.visit.visitDate),
      valid_upto: trimOrNull(form.visit.validUpTo),
      appointment_no: trimOrNull(form.visit.appointmentNo),
      mode: trimOrNull(form.visit.mode),
    },
    report: {
      report_id: trimOrNull(form.report.reportId),
      received_on: trimOrNull(form.report.receivedOn),
      reported_on: trimOrNull(form.report.reportedOn),
      report_date: trimOrNull(form.report.reportDate),
      signed_by: trimOrNull(form.report.signedBy),
      signed_by_designation: trimOrNull(form.report.signedByDesignation),
      technician: trimOrNull(form.report.technician),
    },
    chief_complaints: cleanList(form.chiefComplaints),
    examination: cleanList(form.examination),
    diagnoses: cleanList(form.diagnoses),
    medicines: form.medicines
      .filter((m) => (m.name ?? '').trim() !== '')
      .map((m) => ({
        name: m.name.trim(),
        dose: trimOrNull(m.dose),
        frequency: trimOrNull(m.frequency),
        duration: trimOrNull(m.duration),
        instructions: trimOrNull(m.instructions),
      })),
    investigations_advised: cleanList(form.investigationsAdvised),
    advice: cleanList(form.advice),
    follow_up_date: trimOrNull(form.visit.followUpDate),
    lab_results: form.labResults
      .filter((l) => (l.testName ?? '').trim() !== '')
      .map((l) => ({
        group: trimOrNull(l.group),
        test: l.testName.trim(),
        value: trimOrNull(l.value),
        unit: trimOrNull(l.unit),
        reference_range: trimOrNull(l.referenceRange),
        flag: trimOrNull(l.flag),
        abnormal: Boolean(l.isAbnormal),
        remark: trimOrNull(l.remark),
      })),
    radiology: {
      examination: trimOrNull(form.radiology.examination),
      protocol: trimOrNull(form.radiology.protocol),
      observations: cleanList(form.radiology.observations),
      impression: trimOrNull(form.radiology.impression),
    },
    handwriting_confidence: trimOrNull(form.handwritingConfidence),
    handwritten_present: form.handwrittenPresent === 'YES' ? true
      : form.handwrittenPresent === 'NO' ? false
      : null,
    notes: trimOrNull(form.visit.notes),
    abnormal_findings: cleanList(form.abnormalFindings),
    warnings: cleanList(form.warnings),
    uncertain_fields: form.uncertainFields ?? [],
  }
}

export function buildVerifyRequest(form, rawAiJson) {
  return {
    patient: {
      pid: trimOrNull(form.patient.pid),
      name: trimOrNull(form.patient.name),
      gender: trimOrNull(form.patient.gender),
      age: trimOrNull(form.patient.age),
      maritalStatus: trimOrNull(form.patient.maritalStatus),
      phone: trimOrNull(form.patient.phone),
      address: trimOrNull(form.patient.address),
      allergies: trimOrNull(form.patient.allergies),
    },
    doctor: {
      name: trimOrNull(form.doctor.name),
      qualification: trimOrNull(form.doctor.qualification),
      experience: trimOrNull(form.doctor.experience),
      registrationNo: trimOrNull(form.doctor.registrationNo),
      designation: trimOrNull(form.doctor.designation),
      clinic: trimOrNull(form.doctor.clinic),
    },
    visit: {
      visitDate: trimOrNull(form.visit.visitDate),
      visitTime: trimOrNull(form.visit.visitTime),
      validUpTo: trimOrNull(form.visit.validUpTo),
      appointmentNo: trimOrNull(form.visit.appointmentNo),
      mode: trimOrNull(form.visit.mode),
      followUpDate: trimOrNull(form.visit.followUpDate),
      notes: trimOrNull(form.visit.notes),
    },
    diagnoses: form.diagnoses
      .map((description) => ({ description: (description ?? '').trim() }))
      .filter((d) => d.description !== ''),
    medicines: form.medicines
      .filter((m) => (m.name ?? '').trim() !== '')
      .map((m) => ({
        name: m.name.trim(),
        dose: trimOrNull(m.dose),
        frequency: trimOrNull(m.frequency),
        duration: trimOrNull(m.duration),
        instructions: trimOrNull(m.instructions),
      })),
    labResults: form.labResults
      .filter((l) => (l.testName ?? '').trim() !== '')
      .map((l) => ({
        testName: l.testName.trim(),
        value: trimOrNull(l.value),
        unit: trimOrNull(l.unit),
        referenceRange: trimOrNull(l.referenceRange),
        isAbnormal: Boolean(l.isAbnormal),
      })),
    rawAiJson: rawAiJson ?? null,
    reviewedJson: buildReviewedJson(form),
  }
}
