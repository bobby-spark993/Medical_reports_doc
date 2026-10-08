import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { API_BASE, del, get } from '../lib/api'
import { useAuth } from '../lib/auth'
import { formatDate, formatDateTime, formatDay, formatTime, formatValue, titleCase } from '../lib/format'
import ErrorBanner from '../components/ErrorBanner'
import Loading from '../components/Loading'
import FolderIcon from '../components/FolderIcon'

// The printed time on a document ("10:30 AM") as minutes past midnight.
function timeToMinutes(raw) {
  if (!raw) return 0
  const match = /(\d{1,2}):(\d{2})\s*(AM|PM)?/i.exec(raw)
  if (!match) return 0
  let hours = Number(match[1])
  const minutes = Number(match[2])
  const meridiem = (match[3] || '').toUpperCase()
  if (meridiem === 'PM' && hours !== 12) hours += 12
  if (meridiem === 'AM' && hours === 12) hours = 0
  return hours * 60 + minutes
}

// A document's own date/time (prescription = appointment date, report =
// received-on). Scan time is only a last resort when the document has no date.
function documentTime(visit) {
  if (visit.visitDate) {
    const base = new Date(`${visit.visitDate}T00:00:00`).getTime()
    if (!Number.isNaN(base)) return base + timeToMinutes(visit.visitTime) * 60000
  }
  return visit.createdAt ? new Date(visit.createdAt).getTime() : 0
}

function VisitCard({ visit, canReport, onDelete, deleting }) {
  const [open, setOpen] = useState(true)
  const scanUrl = visit.hasScan ? `${API_BASE}/api/files/${visit.id}` : null

  return (
    <div className="visit">
      <button type="button" className="visit__head" onClick={() => setOpen((v) => !v)} aria-expanded={open}>
        <span className={`chevron${open ? ' chevron--open' : ''}`} aria-hidden="true">▸</span>
        <span className="visit__date">{formatDate(visit.visitDate)}{visit.visitTime ? ` · ${visit.visitTime}` : ''}</span>
        <span className="muted small">
          {visit.doctor?.name ? `${visit.doctor.name}${visit.doctor.qualification ? `, ${visit.doctor.qualification}` : ''}` : 'No doctor recorded'}
        </span>
        <span className="visit__spacer" />
        {visit.followUpDate ? <span className="pill pill--info">Follow-up {formatDate(visit.followUpDate)}</span> : null}
        <span className={`pill ${visit.isVerified ? 'pill--ok' : 'pill--warn'}`}>{visit.isVerified ? 'Verified' : 'Draft'}</span>
      </button>

      {open ? (
        <div className="visit__body">
          <dl className="meta">
            <div><dt>Time</dt><dd>{formatValue(visit.visitTime)}</dd></div>
            <div><dt>Appointment no.</dt><dd>{formatValue(visit.appointmentNo)}</dd></div>
            <div><dt>Mode</dt><dd>{formatValue(visit.mode)}</dd></div>
            <div><dt>Valid up to</dt><dd>{formatValue(visit.validUpTo && formatDate(visit.validUpTo))}</dd></div>
          </dl>

          {visit.notes ? (
            <p className="visit__notes"><strong>Notes:</strong> {visit.notes}</p>
          ) : null}

          {visit.diagnoses?.length ? (
            <div className="block">
              <h4>Diagnoses</h4>
              <div className="tags">
                {visit.diagnoses.map((diagnosis, index) => (
                  <span className="tag" key={`${diagnosis}-${index}`}>{diagnosis}</span>
                ))}
              </div>
            </div>
          ) : null}

          {visit.medicines?.length ? (
            <div className="block">
              <h4>Medicines</h4>
              <table className="table table--compact">
                <thead>
                  <tr><th>Medicine</th><th>Dose</th><th>Frequency</th><th>Duration</th><th>Instructions</th></tr>
                </thead>
                <tbody>
                  {visit.medicines.map((medicine) => (
                    <tr key={medicine.id}>
                      <td>{medicine.name}</td>
                      <td>{formatValue(medicine.dose)}</td>
                      <td>{formatValue(medicine.frequency)}</td>
                      <td>{formatValue(medicine.duration)}</td>
                      <td>{formatValue(medicine.instructions)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : null}

          {visit.labResults?.length ? (
            <div className="block">
              <h4>Lab results</h4>
              <table className="table table--compact">
                <thead>
                  <tr><th>Test</th><th>Value</th><th>Unit</th><th>Reference</th><th /></tr>
                </thead>
                <tbody>
                  {visit.labResults.map((lab) => (
                    <tr key={lab.id} className={lab.isAbnormal ? 'row--danger' : ''}>
                      <td>{lab.testName}</td>
                      <td>{formatValue(lab.value)}</td>
                      <td>{formatValue(lab.unit)}</td>
                      <td>{formatValue(lab.referenceRange)}</td>
                      <td>{lab.isAbnormal ? <span className="pill pill--danger">Abnormal</span> : null}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : null}

          <div className="visit__actions">
            <Link to={`/documents/${visit.id}`} className="btn btn--primary btn--sm">Open document</Link>
            {scanUrl ? (
              <a className="btn btn--ghost btn--sm" href={scanUrl} target="_blank" rel="noreferrer">View scan</a>
            ) : null}
            {canReport ? (
              <a className="btn btn--ghost btn--sm" href={`${API_BASE}/api/patients/${visit.patientId}/report`} target="_blank" rel="noreferrer">
                Report PDF
              </a>
            ) : null}
            {onDelete ? (
              <button
                type="button"
                className="btn btn--danger btn--sm"
                onClick={() => onDelete(visit)}
                disabled={deleting}
              >
                {deleting ? 'Deleting…' : 'Delete'}
              </button>
            ) : null}
          </div>
        </div>
      ) : null}
    </div>
  )
}

export default function PatientDetailPage() {
  const { id } = useParams()
  const { user } = useAuth()
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [reload, setReload] = useState(0)
  const [deletingId, setDeletingId] = useState(null)

  const canReport = user?.role === 'admin' || user?.role === 'doctor'

  useEffect(() => {
    let active = true
    setLoading(true)
    setError(null)
    get(`/api/patients/${id}`)
      .then((res) => active && setData(res))
      .catch((err) => active && setError(err))
      .finally(() => active && setLoading(false))
    return () => {
      active = false
    }
  }, [id, reload])

  async function handleDelete(visit) {
    const label = visit.visitDate ? formatDate(visit.visitDate) : `#${visit.id}`
    if (!window.confirm(`Delete the document dated ${label}? This cannot be undone.`)) {
      return
    }
    setDeletingId(visit.id)
    setError(null)
    try {
      await del(`/api/prescriptions/${visit.id}`)
      setReload((n) => n + 1)
    } catch (err) {
      setError(err)
    } finally {
      setDeletingId(null)
    }
  }

  if (loading) return <Loading fullscreen label="Loading patient…" />
  if (!data) {
    return (
      <div className="page">
        <ErrorBanner error={error ?? { message: 'Patient not found' }} />
        <Link to="/patients" className="btn btn--ghost">← Back to patients</Link>
      </div>
    )
  }

  const { patient, visits = [], appointments = [], followUps = [] } = data

  // Newest first, by each document's own date/time — never by scan time.
  const orderedVisits = [...visits].sort((a, b) => documentTime(b) - documentTime(a))

  // Newest first, by the document date (falling back to when it was recorded).
  const docTime = (doc) => {
    const raw = doc.documentDate || doc.createdAt
    const time = raw ? new Date(raw).getTime() : 0
    return Number.isNaN(time) ? 0 : time
  }
  const documents = [...(data.documents ?? [])].sort((a, b) => docTime(b) - docTime(a))

  return (
    <div className="page">
      <header className="page__head">
        <div>
          <Link to="/patients" className="back-link btn btn--ghost btn--sm">← Back to patients</Link>
          <h1>{patient.name}</h1>
          <p className="muted">
            {[patient.pid && `ID ${patient.pid}`, patient.age, patient.gender && titleCase(patient.gender), patient.phone]
              .filter(Boolean)
              .join(' · ') || 'No demographic details recorded'}
          </p>
        </div>
        {canReport ? (
          <a className="btn btn--primary" href={`${API_BASE}/api/patients/${patient.id}/report`} target="_blank" rel="noreferrer">
            Download report PDF
          </a>
        ) : null}
      </header>

      <ErrorBanner error={error} />

      <div className="grid grid--detail">
        <section className="card">
          <h2 className="card__title">Profile</h2>
          <dl className="meta meta--stacked">
            <div><dt>Patient ID</dt><dd>{formatValue(patient.pid)}</dd></div>
            <div><dt>Gender</dt><dd>{formatValue(patient.gender)}</dd></div>
            <div><dt>Age</dt><dd>{formatValue(patient.age)}</dd></div>
            <div><dt>Marital status</dt><dd>{formatValue(patient.maritalStatus)}</dd></div>
            <div><dt>Phone</dt><dd>{formatValue(patient.phone)}</dd></div>
            <div><dt>Address</dt><dd>{formatValue(patient.address)}</dd></div>
            <div><dt>Registered</dt><dd>{formatDateTime(patient.createdAt)}</dd></div>
          </dl>
          {patient.allergies ? (
            <div className="banner banner--warn banner--inline">
              <strong>Allergies:</strong> {patient.allergies}
            </div>
          ) : null}
        </section>

        <section className="card">
          <h2 className="card__title">Appointments &amp; follow-ups</h2>
          {appointments.length ? (
            <table className="table table--compact">
              <thead>
                <tr>
                  <th>Registration date</th>
                  <th>Day</th>
                  <th>Time</th>
                  <th>Doctor</th>
                  <th>Patient</th>
                  <th>Reg. no.</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {appointments.map((appointment) => (
                  <tr key={appointment.id}>
                    <td>{formatDate(patient.createdAt)}</td>
                    <td>{formatDay(appointment.scheduledAt)}</td>
                    <td>{formatTime(appointment.scheduledAt)}</td>
                    <td>{formatValue(appointment.doctorName)}</td>
                    <td>{formatValue(patient.name)}</td>
                    <td>{formatValue(patient.pid)}</td>
                    <td>
                      <span className={`pill ${appointment.isUpcoming ? 'pill--info' : 'pill--muted'}`}>
                        {titleCase(appointment.status)}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <p className="muted">No appointments recorded.</p>
          )}

          {followUps.length ? (
            <>
              <h3 className="subheading">Upcoming follow-ups</h3>
              <ul className="list">
                {followUps.map((followUp) => (
                  <li key={followUp.visitId} className="list__item">
                    <span>{formatDate(followUp.followUpDate)}</span>
                    <span className="muted small">Visit #{followUp.visitId}</span>
                  </li>
                ))}
              </ul>
            </>
          ) : null}
        </section>
      </div>

      <section className="card">
        <h2 className="card__title">Documents by date ({orderedVisits.length})</h2>
        {orderedVisits.length ? (
          <div className="visits">
            {orderedVisits.map((visit) => (
              <VisitCard
                key={visit.id}
                visit={visit}
                canReport={canReport}
                onDelete={handleDelete}
                deleting={deletingId === visit.id}
              />
            ))}
          </div>
        ) : (
          <p className="muted">No documents yet.</p>
        )}
      </section>

      <section className="card">
        <h2 className="card__title">Documents ({documents.length})</h2>
        {documents.length ? (
          <ul className="list">
            {documents.map((doc) => (
              <li key={doc.id} className="list__item">
                <div className="table__patient">
                  <span className="table__folder" aria-hidden="true"><FolderIcon size={18} /></span>
                  <div>
                    <strong>{titleCase((doc.documentType || 'OTHER').replace('_', ' '))}</strong>
                    <div className="muted small">
                      {[doc.documentDate && formatDate(doc.documentDate), doc.labName, doc.labReportId]
                        .filter(Boolean)
                        .join(' · ') || 'No date reported'}
                    </div>
                  </div>
                </div>
                <div className="list__right">
                  {doc.documentDate ? <span className="muted small">{formatDate(doc.documentDate)}</span> : null}
                  <a className="btn btn--ghost btn--sm" href={`${API_BASE}/api/documents/${doc.id}/file`} target="_blank" rel="noreferrer">
                    View
                  </a>
                </div>
              </li>
            ))}
          </ul>
        ) : (
          <p className="muted">No documents yet.</p>
        )}
      </section>
    </div>
  )
}
