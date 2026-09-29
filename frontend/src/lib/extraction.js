// Maps the Gemini extraction payload (GeminiExtraction on the backend) to the
// editable shape used by the review form, and back to a VerifyRequest.

const TEXT_KEYS = ['description', 'diagnosis', 'name', 'test', 'value', 'text', 'finding', 'investigation']

function blankToEmpty(value) {
  return value ?? ''
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
  const appointment = e.appointment ?? {}
  const clinic = e.clinic ?? {}

  return {
    patient: {
      pid: blankToEmpty(patient.pid),
      name: blankToEmpty(patient.name),
      gender: blankToEmpty(patient.gender),
      age: blankToEmpty(patient.age),
      maritalStatus: blankToEmpty(patient.maritalStatus),
      phone: '',
      address: blankToEmpty(patient.address),
      allergies: '',
    },
    doctor: {
      name: blankToEmpty(doctor.name),
      qualification: blankToEmpty(doctor.qualification),
      registrationNo: blankToEmpty(doctor.registrationNo),
      designation: blankToEmpty(doctor.designation),
      clinic: blankToEmpty(clinic.name),
    },
    visit: {
      visitDate: blankToEmpty(appointment.date),
      visitTime: blankToEmpty(appointment.time),
      validUpTo: blankToEmpty(appointment.validUpto),
      appointmentNo: blankToEmpty(appointment.appointmentNo),
      mode: blankToEmpty(appointment.mode),
      followUpDate: blankToEmpty(e.followUpDate),
      notes: blankToEmpty(e.notes),
    },
    diagnoses: (e.diagnoses ?? []).map(diagnosisToString).filter((text) => text.trim()),
    medicines: (e.medicines ?? []).map((m) => ({
      name: blankToEmpty(m.name),
      dose: blankToEmpty(m.dose),
      frequency: blankToEmpty(m.frequency),
      duration: blankToEmpty(m.duration),
      instructions: blankToEmpty(m.instructions),
    })),
    labResults: (e.labResults ?? []).map((l) => ({
      testName: blankToEmpty(l.test),
      value: blankToEmpty(l.value),
      unit: blankToEmpty(l.unit),
      referenceRange: blankToEmpty(l.referenceRange),
      isAbnormal: Boolean(l.abnormal),
    })),
  }
}

function trimOrNull(value) {
  const trimmed = (value ?? '').trim()
  return trimmed === '' ? null : trimmed
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
  }
}
