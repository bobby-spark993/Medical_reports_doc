import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { API_BASE, get, post } from '../lib/api'
import { buildForm, buildVerifyRequest } from '../lib/extraction'
import { formatDateTime } from '../lib/format'
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
        setForm(buildForm(res.extracted))
      })
      .catch((err) => active && setError(err))
      .finally(() => active && setLoading(false))
    return () => {
      active = false
    }
  }, [id])

  const uncertain = draft?.extracted?.uncertainFields ?? []

  const scanUrl = useMemo(() => `${API_BASE}/api/files/${id}`, [id])

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

  function patchList(section, index, key, value) {
    setForm((prev) => {
      const list = prev[section].map((item, i) => (i === index ? { ...item, [key]: value } : item))
      return { ...prev, [section]: list }
    })
  }

  function patchDiagnosis(index, value) {
    setForm((prev) => ({
      ...prev,
      diagnoses: prev.diagnoses.map((item, i) => (i === index ? value : item)),
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

      {verified ? (
        <div className="banner banner--info">
          This prescription has already been verified.{' '}
          {patient?.id ? <Link to={`/patients/${patient.id}`}>View the patient record →</Link> : null}
        </div>
      ) : null}

      {uncertain.length ? (
        <div className="banner banner--warn">
          <div>
            <strong>The AI was unsure about:</strong>
            <ul className="banner__issues">
              {uncertain.map((field) => (
                <li key={field}>{field}</li>
              ))}
            </ul>
          </div>
        </div>
      ) : null}

      {/* Read-only, clean preview of everything the scan produced. */}
      <section className="card">
        <div className="card__head">
          <h2 className="card__title">Preview — scan se nikla data</h2>
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
            <Link to={`/patients/${draft.matchedPatient.id}`} className="btn btn--ghost btn--sm">Folder kholo</Link>
          </div>
        ) : (
          <div className="folder-banner folder-banner--new">
            <span className="folder-banner__icon"><FolderIcon size={30} /></span>
            <div className="folder-banner__body">
              <strong>New patient folder banega</strong>
              <div className="muted small">DB me name + age + gender se koi match nahi mila.</div>
            </div>
            <span className="pill pill--info">New folder</span>
          </div>
        )}

        <div className="profile">
          <div className="profile__avatar" aria-hidden="true">
            {(form.patient.name || '?').trim().charAt(0).toUpperCase()}
          </div>
          <div className="profile__info">
            <strong className="profile__name">{form.patient.name || 'Unknown patient'}</strong>
            <div className="profile__tags">
              <span className="tag">PID: {form.patient.pid || '—'}</span>
              <span className="tag">Age: {form.patient.age || '—'}</span>
              <span className="tag">Gender: {form.patient.gender || '—'}</span>
              {form.patient.maritalStatus ? <span className="tag">{form.patient.maritalStatus}</span> : null}
            </div>
            {form.patient.address ? <p className="muted small">{form.patient.address}</p> : null}
          </div>
        </div>

        <div className="grid grid--detail">
          <div className="block">
            <h4>Doctor &amp; clinic</h4>
            <dl className="meta meta--stacked">
              <div><dt>Doctor</dt><dd>{form.doctor.name || '—'}</dd></div>
              <div><dt>Qualification</dt><dd>{form.doctor.qualification || '—'}</dd></div>
              <div><dt>Registration no.</dt><dd>{form.doctor.registrationNo || '—'}</dd></div>
              <div><dt>Clinic</dt><dd>{form.doctor.clinic || '—'}</dd></div>
            </dl>
          </div>
          <div className="block">
            <h4>Visit</h4>
            <dl className="meta meta--stacked">
              <div><dt>Date</dt><dd>{form.visit.visitDate || '—'}</dd></div>
              <div><dt>Valid up to</dt><dd>{form.visit.validUpTo || '—'}</dd></div>
              <div><dt>Appointment no.</dt><dd>{form.visit.appointmentNo || '—'}</dd></div>
              <div><dt>Follow-up</dt><dd>{form.visit.followUpDate || '—'}</dd></div>
            </dl>
          </div>
        </div>

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

        {form.labResults.length ? (
          <div className="block">
            <h4>Lab results</h4>
            <table className="table table--compact">
              <thead>
                <tr><th>Test</th><th>Value</th><th>Unit</th><th>Reference</th><th /></tr>
              </thead>
              <tbody>
                {form.labResults.map((lab, index) => (
                  <tr key={`preview-lab-${index}`} className={lab.isAbnormal ? 'row--danger' : ''}>
                    <td>{lab.testName || '—'}</td>
                    <td>{lab.value || '—'}</td>
                    <td>{lab.unit || '—'}</td>
                    <td>{lab.referenceRange || '—'}</td>
                    <td>{lab.isAbnormal ? <span className="pill pill--danger">Abnormal</span> : null}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}

        {form.visit.notes ? <p className="visit__notes"><strong>Notes:</strong> {form.visit.notes}</p> : null}

        {showJson ? (
          <div className="block">
            <h4>Raw JSON <span className="muted small">(isi roop mein DB me save hua)</span></h4>
            <pre className="json-preview">{rawJsonText}</pre>
          </div>
        ) : null}
      </section>

      <div className="grid grid--review">
        <section className="card card--scan">
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

        <div className="review-form">
          <section className="card">
            <h2 className="card__title">Patient</h2>
            <div className="field-row">
              <Field label="Name" value={form.patient.name} onChange={(e) => patch('patient', 'name', e.target.value)} disabled={verified} />
              <Field label="Patient ID" value={form.patient.pid} onChange={(e) => patch('patient', 'pid', e.target.value)} disabled={verified} />
              <Field label="Gender" value={form.patient.gender} onChange={(e) => patch('patient', 'gender', e.target.value)} disabled={verified} />
              <Field label="Age" value={form.patient.age} onChange={(e) => patch('patient', 'age', e.target.value)} disabled={verified} />
              <Field label="Marital status" value={form.patient.maritalStatus} onChange={(e) => patch('patient', 'maritalStatus', e.target.value)} disabled={verified} />
              <Field label="Phone" value={form.patient.phone} onChange={(e) => patch('patient', 'phone', e.target.value)} disabled={verified} />
            </div>
            <Field label="Address" wide value={form.patient.address} onChange={(e) => patch('patient', 'address', e.target.value)} disabled={verified} />
            <Field label="Allergies" wide value={form.patient.allergies} onChange={(e) => patch('patient', 'allergies', e.target.value)} disabled={verified} />
          </section>

          <section className="card">
            <h2 className="card__title">Doctor &amp; clinic</h2>
            <div className="field-row">
              <Field label="Doctor name" value={form.doctor.name} onChange={(e) => patch('doctor', 'name', e.target.value)} disabled={verified} />
              <Field label="Qualification" value={form.doctor.qualification} onChange={(e) => patch('doctor', 'qualification', e.target.value)} disabled={verified} />
              <Field label="Registration no." value={form.doctor.registrationNo} onChange={(e) => patch('doctor', 'registrationNo', e.target.value)} disabled={verified} />
              <Field label="Designation" value={form.doctor.designation} onChange={(e) => patch('doctor', 'designation', e.target.value)} disabled={verified} />
              <Field label="Clinic" value={form.doctor.clinic} onChange={(e) => patch('doctor', 'clinic', e.target.value)} disabled={verified} />
            </div>
          </section>

          <section className="card">
            <h2 className="card__title">Visit</h2>
            <div className="field-row">
              <Field label="Visit date" type="date" value={form.visit.visitDate} onChange={(e) => patch('visit', 'visitDate', e.target.value)} disabled={verified} />
              <Field label="Time" value={form.visit.visitTime} onChange={(e) => patch('visit', 'visitTime', e.target.value)} disabled={verified} placeholder="e.g. 10:30 AM" />
              <Field label="Valid up to" type="date" value={form.visit.validUpTo} onChange={(e) => patch('visit', 'validUpTo', e.target.value)} disabled={verified} />
              <Field label="Appointment no." value={form.visit.appointmentNo} onChange={(e) => patch('visit', 'appointmentNo', e.target.value)} disabled={verified} />
              <Field label="Mode" value={form.visit.mode} onChange={(e) => patch('visit', 'mode', e.target.value)} disabled={verified} />
              <Field label="Follow-up date" type="date" value={form.visit.followUpDate} onChange={(e) => patch('visit', 'followUpDate', e.target.value)} disabled={verified} />
            </div>
            <label className="field field--wide">
              <span>Notes</span>
              <textarea rows={3} value={form.visit.notes} onChange={(e) => patch('visit', 'notes', e.target.value)} disabled={verified} />
            </label>
          </section>

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
                  onChange={(e) => patchDiagnosis(index, e.target.value)}
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

          <section className="card">
            <div className="card__head">
              <h2 className="card__title">Lab results</h2>
              {!verified ? (
                <button
                  type="button"
                  className="btn btn--ghost btn--sm"
                  onClick={() => addToList('labResults', { testName: '', value: '', unit: '', referenceRange: '', isAbnormal: false })}
                >
                  + Add
                </button>
              ) : null}
            </div>
            {form.labResults.length === 0 ? <p className="muted">None captured.</p> : null}
            {form.labResults.map((lab, index) => (
              <div className="repeat-card" key={`lab-${index}`}>
                <div className="field-row">
                  <Field label="Test" value={lab.testName} onChange={(e) => patchList('labResults', index, 'testName', e.target.value)} disabled={verified} />
                  <Field label="Value" value={lab.value} onChange={(e) => patchList('labResults', index, 'value', e.target.value)} disabled={verified} />
                  <Field label="Unit" value={lab.unit} onChange={(e) => patchList('labResults', index, 'unit', e.target.value)} disabled={verified} />
                  <Field label="Reference range" value={lab.referenceRange} onChange={(e) => patchList('labResults', index, 'referenceRange', e.target.value)} disabled={verified} />
                </div>
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
      </div>
    </div>
  )
}
