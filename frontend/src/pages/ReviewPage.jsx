import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { API_BASE, get, post } from '../lib/api'
import { buildForm, buildVerifyRequest } from '../lib/extraction'
import { formatDateTime, toDateInputValue } from '../lib/format'
import ErrorBanner from '../components/ErrorBanner'
import Loading from '../components/Loading'
import FolderIcon from '../components/FolderIcon'

function Field({ label, wide = false, ...props }) {
  return (
    <label className={`field${wide ? ' field--wide' : ''}`}>
      <span>{label}</span>
      <input {...props} />
    </label>
  )
}

// Only renders fields that actually came back in the extracted JSON.
function hasValue(value) {
  return value !== null && value !== undefined && String(value).trim() !== ''
}

function MetaRow({ label, value }) {
  if (!hasValue(value)) return null
  return (
    <div>
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  )
}

function MetaBlock({ title, rows }) {
  if (!rows.some(([, value]) => hasValue(value))) return null
  return (
    <div className="block">
      <h4>{title}</h4>
      <dl className="meta meta--stacked">
        {rows.map(([label, value]) => (
          <MetaRow key={label} label={label} value={value} />
        ))}
      </dl>
    </div>
  )
}

// A simple editor for a list of free-text lines (complaints, advice, ...).
function ListEditor({ title, items, verified, onPatch, onAdd, onRemove, placeholder }) {
  return (
    <section className="card">
      <div className="card__head">
        <h2 className="card__title">{title}</h2>
        {!verified ? (
          <button type="button" className="btn btn--ghost btn--sm" onClick={onAdd}>+ Add</button>
        ) : null}
      </div>
      {items.length === 0 ? <p className="muted">None captured.</p> : null}
      {items.map((value, index) => (
        <div className="repeat-row" key={`${title}-${index}`}>
          <input
            value={value}
            onChange={(e) => onPatch(index, e.target.value)}
            disabled={verified}
            placeholder={placeholder}
          />
          {!verified ? (
            <button type="button" className="icon-btn" onClick={() => onRemove(index)} aria-label={`Remove ${title}`}>
              ×
            </button>
          ) : null}
        </div>
      ))}
    </section>
  )
}

export default function ReviewPage() {
  const { id } = useParams()
  const navigate = useNavigate()

  const [draft, setDraft] = useState(null)
  const [form, setForm] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const [imgError, setImgError] = useState(false)
  const [showJson, setShowJson] = useState(false)

  useEffect(() => {
    let active = true
    setLoading(true)
    get(`/api/prescriptions/${id}`)
      .then((res) => {
        if (!active) return
        setDraft(res)
        const nextForm = buildForm(res.extracted)
        // If the scan matched an existing patient (pid or name+age+gender),
        // pre-fill the form with that stored record.
        const match = res.matchedPatient
        if (match) {
          nextForm.patient = {
            ...nextForm.patient,
            pid: match.pid || nextForm.patient.pid,
            name: match.name || nextForm.patient.name,
            gender: match.gender || nextForm.patient.gender,
            age: match.age || nextForm.patient.age,
            address: match.address || nextForm.patient.address,
            phone: match.phone || nextForm.patient.phone,
          }
        }
        setForm(nextForm)
      })
      .catch((err) => active && setError(err))
      .finally(() => active && setLoading(false))
    return () => {
      active = false
    }
  }, [id])

  const scanUrl = useMemo(() => `${API_BASE}/api/files/${id}`, [id])

  // Pages saved together by one page-wise upload, so we can page through them.
  const batch = useMemo(() => {
    const batchId = draft?.visit?.batchId
    if (!batchId) return null
    try {
      const raw = sessionStorage.getItem(`rx_batch_${batchId}`)
      return raw ? JSON.parse(raw) : null
    } catch {
      return null
    }
  }, [draft])

  const batchPages = batch?.pages ?? []
  const batchIndex = batchPages.findIndex((page) => page.id === draft?.visit?.id)
  const prevPage = batchIndex > 0 ? batchPages[batchIndex - 1] : null
  const nextPage = batchIndex >= 0 && batchIndex < batchPages.length - 1 ? batchPages[batchIndex + 1] : null

  // The exact JSON saved on the visit; fall back to the parsed extraction.
  const rawJsonText = useMemo(() => {
    const source = draft?.rawJson
    if (source) {
      try {
        return JSON.stringify(JSON.parse(source), null, 2)
      } catch {
        return source
      }
    }
    return JSON.stringify(draft?.extracted ?? {}, null, 2)
  }, [draft])

  function patch(section, key, value) {
    setForm((prev) => ({ ...prev, [section]: { ...prev[section], [key]: value } }))
  }

  function patchTop(key, value) {
    setForm((prev) => ({ ...prev, [key]: value }))
  }

  function patchList(section, index, key, value) {
    setForm((prev) => {
      const list = prev[section].map((item, i) => (i === index ? { ...item, [key]: value } : item))
      return { ...prev, [section]: list }
    })
  }

  function patchStringList(section, index, value) {
    setForm((prev) => ({
      ...prev,
      [section]: prev[section].map((item, i) => (i === index ? value : item)),
    }))
  }

  function patchRadiologyObs(index, value) {
    setForm((prev) => ({
      ...prev,
      radiology: {
        ...prev.radiology,
        observations: prev.radiology.observations.map((item, i) => (i === index ? value : item)),
      },
    }))
  }

  function addRadiologyObs() {
    setForm((prev) => ({
      ...prev,
      radiology: { ...prev.radiology, observations: [...prev.radiology.observations, ''] },
    }))
  }

  function removeRadiologyObs(index) {
    setForm((prev) => ({
      ...prev,
      radiology: { ...prev.radiology, observations: prev.radiology.observations.filter((_, i) => i !== index) },
    }))
  }

  function addToList(section, blank) {
    setForm((prev) => ({ ...prev, [section]: [...prev[section], blank] }))
  }

  function removeFromList(section, index) {
    setForm((prev) => ({ ...prev, [section]: prev[section].filter((_, i) => i !== index) }))
  }

  async function handleVerify() {
    setError(null)
    setSubmitting(true)
    try {
      const payload = buildVerifyRequest(form, draft.extracted)
      const res = await post(`/api/prescriptions/${id}/verify`, payload)
      navigate(`/patients/${res.patientId}`, { replace: true })
    } catch (err) {
      setError(err)
      setSubmitting(false)
    }
  }

  if (loading) return <Loading fullscreen label="Loading draft…" />
  if (!draft) return <div className="page"><ErrorBanner error={error ?? { message: 'Draft not found' }} /></div>

  const { visit, patient, doctor } = draft
  const verified = visit?.isVerified

  return (
    <div className="page">
      <header className="page__head">
        <div>
          <Link to="/" className="back-link">← Back to dashboard</Link>
          <h1>Review extraction</h1>
          <p className="muted">
            Draft #{visit?.id} · created {formatDateTime(visit?.createdAt)}
            {verified ? ' · already verified' : ''}
          </p>
        </div>
        <span className={`pill ${verified ? 'pill--ok' : 'pill--warn'}`}>
          {verified ? 'Verified' : 'Needs review'}
        </span>
      </header>

      <ErrorBanner error={error} />

      {(batchPages.length > 1 || (visit?.pageCount ?? 1) > 1) ? (
        <div className="banner banner--info banner--inline page-nav">
          <span>
            Page {visit?.pageNo ?? 1}
            {visit?.pageCount ? ` of ${visit.pageCount}` : ''}
            {nextPage ? ` · next: ${nextPage.documentType === 'PRESCRIPTION' ? 'Prescription' : 'Report'}` : ''}
          </span>
          <span className="page-nav__actions">
            {prevPage ? (
              <Link to={`/review/${prevPage.id}`} className="btn btn--ghost btn--sm">← Previous page</Link>
            ) : null}
            {nextPage ? (
              <Link to={`/review/${nextPage.id}`} className="btn btn--ghost btn--sm">Next page →</Link>
            ) : null}
          </span>
        </div>
      ) : null}

      {verified ? (
        <div className="banner banner--info">
          This prescription has already been verified.{' '}
          {patient?.id ? <Link to={`/patients/${patient.id}`}>View the patient record →</Link> : null}
        </div>
      ) : null}

      {/* Read-only, clean preview of everything the scan produced. */}
      <section className="card">
        <div className="card__head">
          <h2 className="card__title">Preview — extracted from scan</h2>
          <button type="button" className="btn btn--ghost btn--sm" onClick={() => setShowJson((v) => !v)}>
            {showJson ? 'Hide JSON' : 'Show JSON'}
          </button>
        </div>

        {draft.matchedPatient ? (
          <div className="folder-banner folder-banner--existing">
            <span className="folder-banner__icon"><FolderIcon size={30} /></span>
            <div className="folder-banner__body">
              <strong>Existing patient folder — {draft.matchedPatient.name}</strong>
              <div className="profile__tags">
                <span className="tag">PID: {draft.matchedPatient.pid || '—'}</span>
                {draft.matchedPatient.pidShort ? <span className="tag">Ref: {draft.matchedPatient.pidShort}</span> : null}
              </div>
            </div>
            <Link to={`/patients/${draft.matchedPatient.id}`} className="btn btn--ghost btn--sm">Open folder</Link>
          </div>
        ) : (
          <div className="folder-banner folder-banner--new">
            <span className="folder-banner__icon"><FolderIcon size={30} /></span>
            <div className="folder-banner__body">
              <strong>New patient folder will be created</strong>
              <div className="muted small">No existing patient matched by name, age and gender.</div>
            </div>
            <span className="pill pill--info">New folder</span>
          </div>
        )}

        {form.documentType || form.handwritingConfidence || form.handwrittenPresent ? (
          <div className="profile__tags">
            {form.documentType ? <span className="tag">Type: {form.documentType}</span> : null}
            {form.documentTitle ? <span className="tag">{form.documentTitle}</span> : null}
            {form.handwritingConfidence ? <span className="tag">Handwriting: {form.handwritingConfidence}</span> : null}
            {form.handwrittenPresent ? <span className="tag">Handwritten: {form.handwrittenPresent}</span> : null}
          </div>
        ) : null}

        <div className="profile">
          <div className="profile__avatar" aria-hidden="true">
            {(form.patient.name || '?').trim().charAt(0).toUpperCase()}
          </div>
          <div className="profile__info">
            <strong className="profile__name">{form.patient.name || 'Unknown patient'}</strong>
            <div className="profile__tags">
              {hasValue(form.patient.pid) ? <span className="tag">PID: {form.patient.pid}</span> : null}
              {hasValue(form.patient.patientRefNo) ? <span className="tag">Ref: {form.patient.patientRefNo}</span> : null}
              {hasValue(form.patient.age) ? <span className="tag">Age: {form.patient.age}</span> : null}
              {hasValue(form.patient.gender) ? <span className="tag">Gender: {form.patient.gender}</span> : null}
              {hasValue(form.patient.maritalStatus) ? <span className="tag">{form.patient.maritalStatus}</span> : null}
              {hasValue(form.patient.labId) ? <span className="tag">Lab ID: {form.patient.labId}</span> : null}
              {hasValue(form.patient.barcodeText) ? <span className="tag">Barcode: {form.patient.barcodeText}</span> : null}
            </div>
            {hasValue(form.patient.address) ? <p className="muted small">{form.patient.address}</p> : null}
            {hasValue(form.patient.ptRegdValidUpto) ? (
              <p className="muted small">Pt. Regd. valid up to: {form.patient.ptRegdValidUpto}</p>
            ) : null}
          </div>
        </div>

        <div className="grid grid--detail">
          <MetaBlock
            title="Clinic details"
            rows={[
              ['Name', form.facility.name],
              ['Address', form.facility.address],
              ['Phone', form.facility.phone],
              ['Mobile', form.facility.mobile],
              ['Email', form.facility.email],
              ['Website', form.facility.website],
              ['Timings', form.facility.timings],
              ['Closed days', form.facility.closedDays],
              ['Services', form.facility.services],
              ['Powered by', form.facility.poweredBy],
              ['Note', form.facility.note],
            ]}
          />
          <MetaBlock
            title="Doctor details"
            rows={[
              ['Doctor', form.doctor.name],
              ['Qualification', form.doctor.qualification],
              ['Experience', form.doctor.experience],
              ['Registration no.', form.doctor.registrationNo],
              ['Designation', form.doctor.designation],
              ['Referred by', form.referredBy],
            ]}
          />
        </div>

        <MetaBlock
          title="Visit"
          rows={[
            ['Date', form.visit.visitDate],
            ['Time', form.visit.visitTime],
            ['Valid up to', form.visit.validUpTo],
            ['Appointment no.', form.visit.appointmentNo],
            ['Mode', form.visit.mode],
            ['Follow-up', form.visit.followUpDate],
          ]}
        />

        <MetaBlock
          title="Report"
          rows={[
            ['Report ID', form.report.reportId],
            ['Received on', form.report.receivedOn],
            ['Reported on', form.report.reportedOn],
            ['Report date', form.report.reportDate],
            ['Signed by', [form.report.signedBy, form.report.signedByDesignation].filter(Boolean).join(' — ')],
            ['Technician', form.report.technician],
          ]}
        />

        {form.abnormalFindings.length ? (
          <div className="block">
            <h4>Abnormal findings</h4>
            <ul className="banner__issues">
              {form.abnormalFindings.map((value, index) => (
                <li key={`preview-abn-${index}`}>{value}</li>
              ))}
            </ul>
          </div>
        ) : null}

        {form.warnings.length ? (
          <div className="banner banner--warn">
            <div>
              <strong>Warnings</strong>
              <ul className="banner__issues">
                {form.warnings.map((value, index) => (
                  <li key={`preview-warn-${index}`}>{value}</li>
                ))}
              </ul>
            </div>
          </div>
        ) : null}

        {form.chiefComplaints.length ? (
          <div className="block">
            <h4>Chief complaints</h4>
            <div className="tags">
              {form.chiefComplaints.map((value, index) => (
                <span className="tag" key={`preview-c/o-${index}`}>{value}</span>
              ))}
            </div>
          </div>
        ) : null}

        {form.examination.length ? (
          <div className="block">
            <h4>Examination</h4>
            <div className="tags">
              {form.examination.map((value, index) => (
                <span className="tag" key={`preview-oe-${index}`}>{value}</span>
              ))}
            </div>
          </div>
        ) : null}

        {form.diagnoses.length ? (
          <div className="block">
            <h4>Diagnoses</h4>
            <div className="tags">
              {form.diagnoses.map((diagnosis, index) => (
                <span className="tag" key={`preview-dx-${index}`}>{diagnosis}</span>
              ))}
            </div>
          </div>
        ) : null}

        {form.medicines.length ? (
          <div className="block">
            <h4>Medicines</h4>
            <table className="table table--compact">
              <thead>
                <tr><th>Name</th><th>Dose</th><th>Frequency</th><th>Duration</th><th>Instructions</th></tr>
              </thead>
              <tbody>
                {form.medicines.map((medicine, index) => (
                  <tr key={`preview-med-${index}`}>
                    <td>{medicine.name || '—'}</td>
                    <td>{medicine.dose || '—'}</td>
                    <td>{medicine.frequency || '—'}</td>
                    <td>{medicine.duration || '—'}</td>
                    <td>{medicine.instructions || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}

        {form.investigationsAdvised.length ? (
          <div className="block">
            <h4>Investigations advised</h4>
            <div className="tags">
              {form.investigationsAdvised.map((value, index) => (
                <span className="tag" key={`preview-ix-${index}`}>{value}</span>
              ))}
            </div>
          </div>
        ) : null}

        {form.advice.length ? (
          <div className="block">
            <h4>Advice</h4>
            <div className="tags">
              {form.advice.map((value, index) => (
                <span className="tag" key={`preview-adv-${index}`}>{value}</span>
              ))}
            </div>
          </div>
        ) : null}

        {form.labResults.length ? (
          <div className="block">
            <h4>Lab results</h4>
            <table className="table table--compact">
              <thead>
                <tr><th>Group</th><th>Test</th><th>Value</th><th>Unit</th><th>Reference</th><th>Flag</th><th>Remark</th></tr>
              </thead>
              <tbody>
                {form.labResults.map((lab, index) => (
                  <tr key={`preview-lab-${index}`} className={lab.isAbnormal ? 'row--danger' : ''}>
                    <td>{lab.group || '—'}</td>
                    <td>{lab.testName || '—'}</td>
                    <td>{lab.value || '—'}</td>
                    <td>{lab.unit || '—'}</td>
                    <td>{lab.referenceRange || '—'}</td>
                    <td>{lab.flag || (lab.isAbnormal ? 'ABNORMAL' : '—')}</td>
                    <td>{lab.remark || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}

        {form.radiology.examination || form.radiology.impression || form.radiology.observations.length ? (
          <div className="block">
            <h4>Radiology</h4>
            {form.radiology.examination ? <p><strong>Examination:</strong> {form.radiology.examination}</p> : null}
            {form.radiology.protocol ? <p><strong>Protocol:</strong> {form.radiology.protocol}</p> : null}
            {form.radiology.observations.length ? (
              <ul className="banner__issues">
                {form.radiology.observations.map((value, index) => (
                  <li key={`preview-rad-${index}`}>{value}</li>
                ))}
              </ul>
            ) : null}
            {form.radiology.impression ? <p><strong>Impression:</strong> {form.radiology.impression}</p> : null}
          </div>
        ) : null}

        {form.visit.notes ? <p className="visit__notes"><strong>Notes:</strong> {form.visit.notes}</p> : null}

        {showJson ? (
          <div className="block">
            <h4>Raw JSON <span className="muted small">(as saved to the database)</span></h4>
            <pre className="json-preview">{rawJsonText}</pre>
          </div>
        ) : null}
      </section>

      <div className="review-form">
          <section className="card">
            <h2 className="card__title">Patient</h2>
            <div className="field-row">
              <Field label="Name" value={form.patient.name} onChange={(e) => patch('patient', 'name', e.target.value)} disabled={verified} />
              <Field label="Patient ID" value={form.patient.pid} onChange={(e) => patch('patient', 'pid', e.target.value)} disabled={verified} />
              <Field label="Ref no." value={form.patient.patientRefNo} onChange={(e) => patch('patient', 'patientRefNo', e.target.value)} disabled={verified} />
              <Field label="Gender" value={form.patient.gender} onChange={(e) => patch('patient', 'gender', e.target.value)} disabled={verified} />
              <Field label="Age" value={form.patient.age} onChange={(e) => patch('patient', 'age', e.target.value)} disabled={verified} />
              <Field label="Age (years)" type="number" value={form.patient.ageYears} onChange={(e) => patch('patient', 'ageYears', e.target.value)} disabled={verified} />
              <Field label="Marital status" value={form.patient.maritalStatus} onChange={(e) => patch('patient', 'maritalStatus', e.target.value)} disabled={verified} />
              <Field label="Pt. Regd. valid up to" value={form.patient.ptRegdValidUpto} onChange={(e) => patch('patient', 'ptRegdValidUpto', e.target.value)} disabled={verified} />
              <Field label="Lab ID" value={form.patient.labId} onChange={(e) => patch('patient', 'labId', e.target.value)} disabled={verified} />
              <Field label="Barcode text" value={form.patient.barcodeText} onChange={(e) => patch('patient', 'barcodeText', e.target.value)} disabled={verified} />
              <Field label="Phone" value={form.patient.phone} onChange={(e) => patch('patient', 'phone', e.target.value)} disabled={verified} />
            </div>
            <Field label="Address" wide value={form.patient.address} onChange={(e) => patch('patient', 'address', e.target.value)} disabled={verified} />
            <Field label="Allergies" wide value={form.patient.allergies} onChange={(e) => patch('patient', 'allergies', e.target.value)} disabled={verified} />
          </section>

          <section className="card">
            <h2 className="card__title">Facility</h2>
            <div className="field-row">
              <Field label="Name" value={form.facility.name} onChange={(e) => patch('facility', 'name', e.target.value)} disabled={verified} />
              <Field label="Phone (comma separated)" value={form.facility.phone} onChange={(e) => patch('facility', 'phone', e.target.value)} disabled={verified} />
              <Field label="Mobile (comma separated)" value={form.facility.mobile} onChange={(e) => patch('facility', 'mobile', e.target.value)} disabled={verified} />
              <Field label="Email" value={form.facility.email} onChange={(e) => patch('facility', 'email', e.target.value)} disabled={verified} />
              <Field label="Website" value={form.facility.website} onChange={(e) => patch('facility', 'website', e.target.value)} disabled={verified} />
              <Field label="Timings" value={form.facility.timings} onChange={(e) => patch('facility', 'timings', e.target.value)} disabled={verified} />
              <Field label="Closed days" value={form.facility.closedDays} onChange={(e) => patch('facility', 'closedDays', e.target.value)} disabled={verified} />
              <Field label="Services (comma separated)" value={form.facility.services} onChange={(e) => patch('facility', 'services', e.target.value)} disabled={verified} />
              <Field label="Powered by" value={form.facility.poweredBy} onChange={(e) => patch('facility', 'poweredBy', e.target.value)} disabled={verified} />
            </div>
            <Field label="Address" wide value={form.facility.address} onChange={(e) => patch('facility', 'address', e.target.value)} disabled={verified} />
            <Field label="Note" wide value={form.facility.note} onChange={(e) => patch('facility', 'note', e.target.value)} disabled={verified} />
          </section>

          <section className="card">
            <h2 className="card__title">Doctor &amp; referral</h2>
            <div className="field-row">
              <Field label="Doctor name" value={form.doctor.name} onChange={(e) => patch('doctor', 'name', e.target.value)} disabled={verified} />
              <Field label="Qualification" value={form.doctor.qualification} onChange={(e) => patch('doctor', 'qualification', e.target.value)} disabled={verified} />
              <Field label="Experience" value={form.doctor.experience} onChange={(e) => patch('doctor', 'experience', e.target.value)} disabled={verified} />
              <Field label="Registration no." value={form.doctor.registrationNo} onChange={(e) => patch('doctor', 'registrationNo', e.target.value)} disabled={verified} />
              <Field label="Designation" value={form.doctor.designation} onChange={(e) => patch('doctor', 'designation', e.target.value)} disabled={verified} />
              <Field label="Referred by" value={form.referredBy} onChange={(e) => patchTop('referredBy', e.target.value)} disabled={verified} />
            </div>
          </section>

          <section className="card">
            <h2 className="card__title">Visit</h2>
            <div className="field-row">
              <Field label="Visit date" type="date" value={toDateInputValue(form.visit.visitDate)} onChange={(e) => patch('visit', 'visitDate', e.target.value)} disabled={verified} />
              <Field label="Time" value={form.visit.visitTime} onChange={(e) => patch('visit', 'visitTime', e.target.value)} disabled={verified} placeholder="e.g. 10:30 AM" />
              <Field label="Valid up to" type="date" value={toDateInputValue(form.visit.validUpTo)} onChange={(e) => patch('visit', 'validUpTo', e.target.value)} disabled={verified} />
              <Field label="Appointment no." value={form.visit.appointmentNo} onChange={(e) => patch('visit', 'appointmentNo', e.target.value)} disabled={verified} />
              <Field label="Mode" value={form.visit.mode} onChange={(e) => patch('visit', 'mode', e.target.value)} disabled={verified} />
              <Field label="Follow-up date" type="date" value={toDateInputValue(form.visit.followUpDate)} onChange={(e) => patch('visit', 'followUpDate', e.target.value)} disabled={verified} />
            </div>
            <label className="field field--wide">
              <span>Notes (handwriting)</span>
              <textarea rows={6} value={form.visit.notes} onChange={(e) => patch('visit', 'notes', e.target.value)} disabled={verified} />
            </label>
          </section>

          <section className="card">
            <h2 className="card__title">Report details</h2>
            <div className="field-row">
              <Field label="Report ID" value={form.report.reportId} onChange={(e) => patch('report', 'reportId', e.target.value)} disabled={verified} />
              <Field label="Received on" value={form.report.receivedOn} onChange={(e) => patch('report', 'receivedOn', e.target.value)} disabled={verified} />
              <Field label="Reported on" value={form.report.reportedOn} onChange={(e) => patch('report', 'reportedOn', e.target.value)} disabled={verified} />
              <Field label="Report date" value={form.report.reportDate} onChange={(e) => patch('report', 'reportDate', e.target.value)} disabled={verified} />
              <Field label="Signed by" value={form.report.signedBy} onChange={(e) => patch('report', 'signedBy', e.target.value)} disabled={verified} />
              <Field label="Signed by designation" value={form.report.signedByDesignation} onChange={(e) => patch('report', 'signedByDesignation', e.target.value)} disabled={verified} />
              <Field label="Technician" value={form.report.technician} onChange={(e) => patch('report', 'technician', e.target.value)} disabled={verified} />
            </div>
          </section>

          <ListEditor
            title="Chief complaints"
            items={form.chiefComplaints}
            verified={verified}
            onPatch={(i, v) => patchStringList('chiefComplaints', i, v)}
            onAdd={() => addToList('chiefComplaints', '')}
            onRemove={(i) => removeFromList('chiefComplaints', i)}
            placeholder="e.g. Fever x 3 days"
          />

          <ListEditor
            title="Examination"
            items={form.examination}
            verified={verified}
            onPatch={(i, v) => patchStringList('examination', i, v)}
            onAdd={() => addToList('examination', '')}
            onRemove={(i) => removeFromList('examination', i)}
            placeholder="O/E finding"
          />

          <section className="card">
            <div className="card__head">
              <h2 className="card__title">Diagnoses</h2>
              {!verified ? (
                <button type="button" className="btn btn--ghost btn--sm" onClick={() => addToList('diagnoses', '')}>
                  + Add
                </button>
              ) : null}
            </div>
            {form.diagnoses.length === 0 ? <p className="muted">None captured.</p> : null}
            {form.diagnoses.map((value, index) => (
              <div className="repeat-row" key={`dx-${index}`}>
                <input
                  value={value}
                  onChange={(e) => patchStringList('diagnoses', index, e.target.value)}
                  disabled={verified}
                  placeholder="Diagnosis"
                />
                {!verified ? (
                  <button type="button" className="icon-btn" onClick={() => removeFromList('diagnoses', index)} aria-label="Remove diagnosis">×</button>
                ) : null}
              </div>
            ))}
          </section>

          <section className="card">
            <div className="card__head">
              <h2 className="card__title">Medicines</h2>
              {!verified ? (
                <button
                  type="button"
                  className="btn btn--ghost btn--sm"
                  onClick={() => addToList('medicines', { name: '', dose: '', frequency: '', duration: '', instructions: '' })}
                >
                  + Add
                </button>
              ) : null}
            </div>
            {form.medicines.length === 0 ? <p className="muted">None captured.</p> : null}
            {form.medicines.map((medicine, index) => (
              <div className="repeat-card" key={`med-${index}`}>
                <div className="field-row">
                  <Field label="Name" value={medicine.name} onChange={(e) => patchList('medicines', index, 'name', e.target.value)} disabled={verified} />
                  <Field label="Dose" value={medicine.dose} onChange={(e) => patchList('medicines', index, 'dose', e.target.value)} disabled={verified} />
                  <Field label="Frequency" value={medicine.frequency} onChange={(e) => patchList('medicines', index, 'frequency', e.target.value)} disabled={verified} />
                  <Field label="Duration" value={medicine.duration} onChange={(e) => patchList('medicines', index, 'duration', e.target.value)} disabled={verified} />
                </div>
                <Field label="Instructions" wide value={medicine.instructions} onChange={(e) => patchList('medicines', index, 'instructions', e.target.value)} disabled={verified} />
                {!verified ? (
                  <button type="button" className="btn btn--danger btn--sm" onClick={() => removeFromList('medicines', index)}>
                    Remove
                  </button>
                ) : null}
              </div>
            ))}
          </section>

          <ListEditor
            title="Investigations advised"
            items={form.investigationsAdvised}
            verified={verified}
            onPatch={(i, v) => patchStringList('investigationsAdvised', i, v)}
            onAdd={() => addToList('investigationsAdvised', '')}
            onRemove={(i) => removeFromList('investigationsAdvised', i)}
            placeholder="e.g. CBC"
          />

          <ListEditor
            title="Advice"
            items={form.advice}
            verified={verified}
            onPatch={(i, v) => patchStringList('advice', i, v)}
            onAdd={() => addToList('advice', '')}
            onRemove={(i) => removeFromList('advice', i)}
            placeholder="Advice line"
          />

          <section className="card">
            <div className="card__head">
              <h2 className="card__title">Lab results</h2>
              {!verified ? (
                <button
                  type="button"
                  className="btn btn--ghost btn--sm"
                  onClick={() => addToList('labResults', { group: '', testName: '', value: '', unit: '', referenceRange: '', flag: '', remark: '', isAbnormal: false })}
                >
                  + Add
                </button>
              ) : null}
            </div>
            {form.labResults.length === 0 ? <p className="muted">None captured.</p> : null}
            {form.labResults.map((lab, index) => (
              <div className="repeat-card" key={`lab-${index}`}>
                <div className="field-row">
                  <Field label="Group" value={lab.group} onChange={(e) => patchList('labResults', index, 'group', e.target.value)} disabled={verified} />
                  <Field label="Test" value={lab.testName} onChange={(e) => patchList('labResults', index, 'testName', e.target.value)} disabled={verified} />
                  <Field label="Value" value={lab.value} onChange={(e) => patchList('labResults', index, 'value', e.target.value)} disabled={verified} />
                  <Field label="Unit" value={lab.unit} onChange={(e) => patchList('labResults', index, 'unit', e.target.value)} disabled={verified} />
                  <Field label="Reference range" value={lab.referenceRange} onChange={(e) => patchList('labResults', index, 'referenceRange', e.target.value)} disabled={verified} />
                  <Field label="Flag (L/H)" value={lab.flag} onChange={(e) => patchList('labResults', index, 'flag', e.target.value)} disabled={verified} />
                </div>
                <Field label="Remark" wide value={lab.remark} onChange={(e) => patchList('labResults', index, 'remark', e.target.value)} disabled={verified} />
                <label className="check">
                  <input
                    type="checkbox"
                    checked={lab.isAbnormal}
                    onChange={(e) => patchList('labResults', index, 'isAbnormal', e.target.checked)}
                    disabled={verified}
                  />
                  <span>Abnormal</span>
                </label>
                {!verified ? (
                  <button type="button" className="btn btn--danger btn--sm" onClick={() => removeFromList('labResults', index)}>
                    Remove
                  </button>
                ) : null}
              </div>
            ))}
          </section>

          <section className="card">
            <h2 className="card__title">Radiology</h2>
            <div className="field-row">
              <Field label="Examination" value={form.radiology.examination} onChange={(e) => patch('radiology', 'examination', e.target.value)} disabled={verified} />
              <Field label="Protocol" value={form.radiology.protocol} onChange={(e) => patch('radiology', 'protocol', e.target.value)} disabled={verified} />
            </div>
            <div className="card__head">
              <h3 className="card__title">Observations</h3>
              {!verified ? (
                <button type="button" className="btn btn--ghost btn--sm" onClick={addRadiologyObs}>+ Add</button>
              ) : null}
            </div>
            {form.radiology.observations.length === 0 ? <p className="muted">None captured.</p> : null}
            {form.radiology.observations.map((value, index) => (
              <div className="repeat-row" key={`rad-obs-${index}`}>
                <input value={value} onChange={(e) => patchRadiologyObs(index, e.target.value)} disabled={verified} placeholder="Observation line" />
                {!verified ? (
                  <button type="button" className="icon-btn" onClick={() => removeRadiologyObs(index)} aria-label="Remove observation">×</button>
                ) : null}
              </div>
            ))}
            <label className="field field--wide">
              <span>Impression</span>
              <textarea rows={3} value={form.radiology.impression} onChange={(e) => patch('radiology', 'impression', e.target.value)} disabled={verified} />
            </label>
          </section>

          <ListEditor
            title="Abnormal findings"
            items={form.abnormalFindings}
            verified={verified}
            onPatch={(i, v) => patchStringList('abnormalFindings', i, v)}
            onAdd={() => addToList('abnormalFindings', '')}
            onRemove={(i) => removeFromList('abnormalFindings', i)}
            placeholder="e.g. Haemoglobin 9.2 gm/dl (L)"
          />

          <ListEditor
            title="Warnings"
            items={form.warnings}
            verified={verified}
            onPatch={(i, v) => patchStringList('warnings', i, v)}
            onAdd={() => addToList('warnings', '')}
            onRemove={(i) => removeFromList('warnings', i)}
            placeholder="e.g. Name on report does not match patient"
          />

          <section className="card">
            <h2 className="card__title">Document</h2>
            <div className="field-row">
              <Field label="Document type" value={form.documentType} onChange={(e) => patchTop('documentType', e.target.value)} disabled={verified} placeholder="PRESCRIPTION / LAB_REPORT / RADIOLOGY_REPORT / OTHER" />
              <Field label="Document title" value={form.documentTitle} onChange={(e) => patchTop('documentTitle', e.target.value)} disabled={verified} />
              <Field label="Handwriting confidence" value={form.handwritingConfidence} onChange={(e) => patchTop('handwritingConfidence', e.target.value)} disabled={verified} placeholder="LOW / MEDIUM / HIGH" />
              <label className="field">
                <span>Handwritten present</span>
                <select value={form.handwrittenPresent} onChange={(e) => patchTop('handwrittenPresent', e.target.value)} disabled={verified}>
                  <option value="">—</option>
                  <option value="YES">Yes</option>
                  <option value="NO">No</option>
                </select>
              </label>
            </div>
          </section>

          {!verified ? (
            <div className="review-actions">
              <button type="button" className="btn btn--primary btn--lg" onClick={handleVerify} disabled={submitting}>
                {submitting ? (
                  <>
                    <span className="spinner spinner--sm" aria-hidden="true" /> Saving…
                  </>
                ) : (
                  'Verify & save'
                )}
              </button>
              <Link to="/" className="btn btn--ghost btn--lg">Cancel</Link>
              {doctor?.name || patient?.name ? (
                <span className="muted small">
                  Linked to {patient?.name ?? 'new patient'}
                  {doctor?.name ? ` · ${doctor.name}` : ''}
                </span>
              ) : null}
            </div>
          ) : null}
        </div>

      <section className="card">
        <h2 className="card__title">Original scan</h2>
        {visit?.hasScan ? (
          <>
            {!imgError ? (
              <img
                src={scanUrl}
                alt="Prescription scan"
                className="scan-preview"
                onError={() => setImgError(true)}
              />
            ) : (
              <p className="muted">Preview unavailable for this file type.</p>
            )}
            <a className="btn btn--ghost btn--sm" href={scanUrl} target="_blank" rel="noreferrer">
              Open original in new tab
            </a>
          </>
        ) : (
          <p className="muted">No scan stored for this draft.</p>
        )}
      </section>
    </div>
  )
}
