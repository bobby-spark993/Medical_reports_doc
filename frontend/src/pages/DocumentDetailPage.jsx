import { useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { API_BASE, get } from '../lib/api'
import { buildForm } from '../lib/extraction'
import { formatDateTime } from '../lib/format'
import ErrorBanner from '../components/ErrorBanner'
import Loading from '../components/Loading'

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

function TagList({ title, items }) {
  if (!items?.length) return null
  return (
    <div className="block">
      <h4>{title}</h4>
      <div className="tags">
        {items.map((value, index) => (
          <span className="tag" key={`${title}-${index}`}>{value}</span>
        ))}
      </div>
    </div>
  )
}

function StringList({ title, items }) {
  if (!items?.length) return null
  return (
    <div className="block">
      <h4>{title}</h4>
      <ul className="banner__issues">
        {items.map((value, index) => (
          <li key={`${title}-${index}`}>{value}</li>
        ))}
      </ul>
    </div>
  )
}

export default function DocumentDetailPage() {
  const { id } = useParams()

  const [draft, setDraft] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [imgError, setImgError] = useState(false)

  useEffect(() => {
    let active = true
    setLoading(true)
    get(`/api/prescriptions/${id}`)
      .then((res) => active && setDraft(res))
      .catch((err) => active && setError(err))
      .finally(() => active && setLoading(false))
    return () => {
      active = false
    }
  }, [id])

  const form = useMemo(() => (draft ? buildForm(draft.extracted) : null), [draft])
  const scanUrl = `${API_BASE}/api/files/${id}`

  if (loading) return <Loading fullscreen label="Loading document…" />
  if (!draft || !form) {
    return (
      <div className="page">
        <ErrorBanner error={error ?? { message: 'Document not found' }} />
        <Link to="/patients" className="btn btn--ghost">← Back to patients</Link>
      </div>
    )
  }

  const { visit, patient } = draft
  const documentType = (form.documentType || '').trim().toUpperCase()
  const isPrescription = documentType !== 'LAB_REPORT' && documentType !== 'RADIOLOGY_REPORT'
  const typeLabel = isPrescription ? 'Prescription' : 'Report'
  const backTo = patient?.id ? `/patients/${patient.id}` : '/patients'

  return (
    <div className="page">
      <header className="page__head">
        <div>
          <Link to={backTo} className="back-link btn btn--ghost btn--sm">← Back to patient folder</Link>
          <h1>{typeLabel} · Document #{visit?.id}</h1>
          <p className="muted">
            {patient?.name ? `${patient.name} · ` : ''}
            {visit?.visitDate ? `Dated ${visit.visitDate} · ` : ''}
            saved {formatDateTime(visit?.createdAt)}
          </p>
        </div>
        <span className={`pill ${visit?.isVerified ? 'pill--ok' : 'pill--warn'}`}>
          {visit?.isVerified ? 'Verified' : 'Draft'}
        </span>
      </header>

      <ErrorBanner error={error} />

      <div className="profile">
        <div className="profile__avatar" aria-hidden="true">
          {(form.patient.name || '?').trim().charAt(0).toUpperCase()}
        </div>
        <div className="profile__info">
          <strong className="profile__name">{form.patient.name || patient?.name || 'Unknown patient'}</strong>
          <div className="profile__tags">
            {form.documentType ? <span className="tag">Type: {form.documentType}</span> : null}
            {hasValue(form.patient.pid) ? <span className="tag">PID: {form.patient.pid}</span> : null}
            {hasValue(form.patient.patientRefNo) ? <span className="tag">Ref: {form.patient.patientRefNo}</span> : null}
            {hasValue(form.patient.age) ? <span className="tag">Age: {form.patient.age}</span> : null}
            {hasValue(form.patient.gender) ? <span className="tag">Gender: {form.patient.gender}</span> : null}
            {visit?.pageCount > 1 ? <span className="tag">Page {visit.pageNo} of {visit.pageCount}</span> : null}
          </div>
          {hasValue(form.patient.address) ? <p className="muted small">{form.patient.address}</p> : null}
        </div>
      </div>

      <div className="grid grid--detail">
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
          title="Doctor & referral"
          rows={[
            ['Doctor', form.doctor.name],
            ['Qualification', form.doctor.qualification],
            ['Registration no.', form.doctor.registrationNo],
            ['Designation', form.doctor.designation],
            ['Referred by', form.referredBy],
          ]}
        />
      </div>

      <MetaBlock
        title="Facility"
        rows={[
          ['Name', form.facility.name],
          ['Address', form.facility.address],
          ['Phone', form.facility.phone],
          ['Email', form.facility.email],
          ['Website', form.facility.website],
          ['Note', form.facility.note],
        ]}
      />

      {isReport ? (
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
      ) : null}

      {isReport ? <StringList title="Abnormal findings" items={form.abnormalFindings} /> : null}
      {isReport && form.warnings.length ? (
        <div className="banner banner--warn">
          <div>
            <strong>Warnings</strong>
            <ul className="banner__issues">
              {form.warnings.map((value, index) => (
                <li key={`warn-${index}`}>{value}</li>
              ))}
            </ul>
          </div>
        </div>
      ) : null}

      {isPrescription ? <TagList title="Chief complaints" items={form.chiefComplaints} /> : null}
      {isPrescription ? <TagList title="Examination" items={form.examination} /> : null}
      {isPrescription ? <TagList title="Diagnoses" items={form.diagnoses} /> : null}

      {isPrescription && form.medicines.length ? (
        <div className="block">
          <h4>Medicines</h4>
          <table className="table table--compact">
            <thead>
              <tr><th>Name</th><th>Dose</th><th>Frequency</th><th>Duration</th><th>Instructions</th></tr>
            </thead>
            <tbody>
              {form.medicines.map((medicine, index) => (
                <tr key={`med-${index}`}>
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

      {isPrescription ? <TagList title="Investigations advised" items={form.investigationsAdvised} /> : null}
      {isPrescription ? <TagList title="Advice" items={form.advice} /> : null}

      {isReport && form.labResults.length ? (
        <div className="block">
          <h4>Lab results</h4>
          <table className="table table--compact">
            <thead>
              <tr><th>Group</th><th>Test</th><th>Value</th><th>Unit</th><th>Reference</th><th>Flag</th><th>Remark</th></tr>
            </thead>
            <tbody>
              {form.labResults.map((lab, index) => (
                <tr key={`lab-${index}`} className={lab.isAbnormal ? 'row--danger' : ''}>
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

      {isReport && (form.radiology.examination || form.radiology.impression || form.radiology.observations.length) ? (
        <div className="block">
          <h4>Radiology</h4>
          {form.radiology.examination ? <p><strong>Examination:</strong> {form.radiology.examination}</p> : null}
          {form.radiology.protocol ? <p><strong>Protocol:</strong> {form.radiology.protocol}</p> : null}
          {form.radiology.observations.length ? (
            <ul className="banner__issues">
              {form.radiology.observations.map((value, index) => (
                <li key={`rad-${index}`}>{value}</li>
              ))}
            </ul>
          ) : null}
          {form.radiology.impression ? <p><strong>Impression:</strong> {form.radiology.impression}</p> : null}
        </div>
      ) : null}

      {form.visit.notes ? <p className="visit__notes"><strong>Notes:</strong> {form.visit.notes}</p> : null}

      <section className="card">
        <h2 className="card__title">Original scan</h2>
        {visit?.hasScan ? (
          <>
            {!imgError ? (
              <img
                src={scanUrl}
                alt={`${typeLabel} scan`}
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
          <p className="muted">No scan stored for this document.</p>
        )}
      </section>
    </div>
  )
}
