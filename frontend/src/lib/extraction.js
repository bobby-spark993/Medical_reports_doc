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

export function buildForm(extracted) {
  const e = extracted ?? {}
  const patient = e.patient ?? {}
  const doctor = e.doctor ?? {}
  const facility = e.facility ?? {}
  const appointment = e.appointment ?? {}
  const report = e.report ?? {}
  const radiology = e.radiology ?? {}

  return {
    documentType: blankToEmpty(e.documentType),
    documentTitle: blankToEmpty(e.documentTitle),
    facility: {
      name: blankToEmpty(facility.name),
      address: blankToEmpty(facility.address),
      phone: asStringList(facility.phone).join(', '),
      email: blankToEmpty(facility.email),
      website: blankToEmpty(facility.website),
    },
    patient: {
      pid: blankToEmpty(patient.pid),
      patientRefNo: blankToEmpty(patient.patientRefNo),
      name: blankToEmpty(patient.name),
      gender: blankToEmpty(patient.gender),
      age: blankToEmpty(patient.age),
      ageYears: patient.ageYears ?? '',
      maritalStatus: blankToEmpty(patient.maritalStatus),
      address: blankToEmpty(patient.address),
      ptRegdValidUpto: blankToEmpty(patient.ptRegdValidUpto),
      phone: '',
      allergies: '',
    },
    doctor: {
      name: blankToEmpty(doctor.name),
      qualification: blankToEmpty(doctor.qualification),
      registrationNo: blankToEmpty(doctor.registrationNo),
      designation: blankToEmpty(doctor.designation),
      clinic: blankToEmpty(facility.name),
    },
    referredBy: blankToEmpty(e.referredBy),
    visit: {
      visitDate: blankToEmpty(appointment.date),
      visitTime: blankToEmpty(appointment.time),
      validUpTo: blankToEmpty(appointment.validUpto),
      appointmentNo: blankToEmpty(appointment.appointmentNo),
      mode: blankToEmpty(appointment.mode),
      followUpDate: blankToEmpty(e.followUpDate),
      notes: blankToEmpty(e.notes),
    },
    report: {
      reportId: blankToEmpty(report.reportId),
      receivedOn: blankToEmpty(report.receivedOn),
      reportedOn: blankToEmpty(report.reportedOn),
      reportDate: blankToEmpty(report.reportDate),
      signedBy: blankToEmpty(report.signedBy),
      signedByDesignation: blankToEmpty(report.signedByDesignation),
    },
    chiefComplaints: asStringList(e.chiefComplaints),
    examination: asStringList(e.examination),
    diagnoses: (e.diagnoses ?? []).map(diagnosisToString).filter((text) => text.trim()),
    medicines: (e.medicines ?? []).map((m) => ({
      name: blankToEmpty(m.name),
      dose: blankToEmpty(m.dose),
      frequency: blankToEmpty(m.frequency),
      duration: blankToEmpty(m.duration),
      instructions: blankToEmpty(m.instructions),
    })),
    investigationsAdvised: asStringList(e.investigationsAdvised),
    advice: asStringList(e.advice),
    labResults: (e.labResults ?? []).map((l) => ({
      group: blankToEmpty(l.group),
      testName: blankToEmpty(l.test),
      value: blankToEmpty(l.value),
      unit: blankToEmpty(l.unit),
      referenceRange: blankToEmpty(l.referenceRange),
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
    handwritingConfidence: blankToEmpty(e.handwritingConfidence),
    uncertainFields: asStringList(e.uncertainFields),
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

/** The reviewed extraction in the same snake_case shape the AI schema uses. */
export function buildReviewedJson(form) {
  return {
    document_type: trimOrNull(form.documentType),
    document_title: trimOrNull(form.documentTitle),
    facility: {
      name: trimOrNull(form.facility.name),
      address: trimOrNull(form.facility.address),
      phone: (form.facility.phone ?? '')
        .split(',')
        .map((phone) => phone.trim())
        .filter((phone) => phone !== ''),
      email: trimOrNull(form.facility.email),
      website: trimOrNull(form.facility.website),
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
    },
    doctor: {
      name: trimOrNull(form.doctor.name),
      qualification: trimOrNull(form.doctor.qualification),
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
    notes: trimOrNull(form.visit.notes),
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
