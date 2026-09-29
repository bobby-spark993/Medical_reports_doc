import { useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { upload } from '../lib/api'
import ErrorBanner from '../components/ErrorBanner'
import FolderIcon from '../components/FolderIcon'

const ACCEPTED = ['image/jpeg', 'image/png', 'image/webp', 'application/pdf']
const MAX_MB = 10

function humanSize(bytes) {
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export default function UploadPage() {
  const navigate = useNavigate()
  const inputRef = useRef(null)

  const [file, setFile] = useState(null)
  const [preview, setPreview] = useState(null)
  const [dragging, setDragging] = useState(false)
  const [consent, setConsent] = useState(false)
  const [contextName, setContextName] = useState('')
  const [contextPhone, setContextPhone] = useState('')
  const [contextPid, setContextPid] = useState('')
  const [error, setError] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState(null)

  function selectFile(next) {
    setError(null)
    if (!next) return
    if (!ACCEPTED.includes(next.type)) {
      setError({ message: 'Unsupported file. Use a JPEG, PNG, WebP image or a PDF.' })
      return
    }
    if (next.size > MAX_MB * 1024 * 1024) {
      setError({ message: `File is too large. The limit is ${MAX_MB} MB.` })
      return
    }
    setFile(next)
    if (next.type.startsWith('image/')) {
      setPreview(URL.createObjectURL(next))
    } else {
      setPreview(null)
    }
  }

  function clearFile() {
    setFile(null)
    if (preview) URL.revokeObjectURL(preview)
    setPreview(null)
    if (inputRef.current) inputRef.current.value = ''
  }

  function handleDrop(event) {
    event.preventDefault()
    setDragging(false)
    selectFile(event.dataTransfer.files?.[0])
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (!file || !consent) return

    setError(null)
    setSubmitting(true)
    const form = new FormData()
    form.append('file', file)
    form.append('consent', 'true')
    if (contextName.trim()) form.append('contextName', contextName.trim())
    if (contextPhone.trim()) form.append('contextPhone', contextPhone.trim())
    if (contextPid.trim()) form.append('contextPid', contextPid.trim())

    try {
      const res = await upload('/api/prescriptions/upload', form)
      // Auto-fill section 2 (patient context) from what the scan produced.
      const scanned = res.extracted?.patient ?? {}
      if (scanned.name) setContextName(scanned.name)
      if (scanned.pid) setContextPid(scanned.pid)
      if (scanned.phone) setContextPhone(scanned.phone)
      setResult(res)
      setSubmitting(false)
    } catch (err) {
      setError(err)
      setSubmitting(false)
    }
  }

  function resetScan() {
    setResult(null)
    clearFile()
  }

  return (
    <div className="page">
      <header className="page__head">
        <div>
          <h1>New scan</h1>
          <p className="muted">Upload a prescription photo or PDF. The AI extracts the fields for you to verify.</p>
        </div>
      </header>

      <ErrorBanner error={error} />

      {result ? (
        <section className="card">
          <div className="card__head">
            <h2 className="card__title">Scan complete — data DB me save ho gaya</h2>
            <button type="button" className="btn btn--ghost btn--sm" onClick={resetScan}>New scan</button>
          </div>

          <div className={`folder-banner folder-banner--${result.matchedPatient ? 'existing' : 'new'}`}>
            <span className="folder-banner__icon"><FolderIcon size={30} /></span>
            <div className="folder-banner__body">
              <strong>{result.extracted?.patient?.name || 'Unknown patient'}</strong>
              <div className="profile__tags">
                <span className="tag">PID: {result.extracted?.patient?.pid || '—'}</span>
                <span className="tag">Age: {result.extracted?.patient?.age || '—'}</span>
                <span className="tag">Gender: {result.extracted?.patient?.gender || '—'}</span>
              </div>
            </div>
            <span className={`pill ${result.matchedPatient ? 'pill--ok' : 'pill--info'}`}>
              {result.matchedPatient ? 'Existing folder' : 'New folder'}
            </span>
          </div>

          {result.matchedPatient ? (
            <div className="banner banner--info banner--inline">
              <span>
                DB me match mila — ye document <strong>{result.matchedPatient.name}</strong>
                {result.matchedPatient.pid ? ` (PID ${result.matchedPatient.pid})` : ''} wale patient folder me add hoga.{' '}
                <Link to={`/patients/${result.matchedPatient.id}`}>Folder kholo →</Link>
              </span>
            </div>
          ) : (
            <div className="banner banner--warn banner--inline">
              Name + age + gender se koi match nahi mila — naya patient folder banega.
            </div>
          )}

          <div className="visit__actions">
            <button type="button" className="btn btn--primary" onClick={() => navigate(`/review/${result.id}`)}>
              Review &amp; save →
            </button>
          </div>
        </section>
      ) : null}

      <form className="grid grid--upload" onSubmit={handleSubmit}>
        <section className="card">
          <h2 className="card__title">1. Prescription file</h2>

          {!file ? (
            <label
              className={`dropzone${dragging ? ' dropzone--active' : ''}`}
              onDragOver={(e) => {
                e.preventDefault()
                setDragging(true)
              }}
              onDragLeave={() => setDragging(false)}
              onDrop={handleDrop}
            >
              <input
                ref={inputRef}
                type="file"
                accept={ACCEPTED.join(',')}
                onChange={(e) => selectFile(e.target.files?.[0])}
                hidden
              />
              <svg viewBox="0 0 24 24" width="34" height="34" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M12 16V4m0 0L8 8m4-4 4 4" />
                <path d="M4 16v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2" />
              </svg>
              <strong>Drag &amp; drop a file here</strong>
              <span className="muted">or click to browse — JPEG, PNG, WebP or PDF up to {MAX_MB} MB</span>
            </label>
          ) : (
            <div className="file-card">
              {preview ? (
                <img src={preview} alt="Selected prescription" className="file-card__preview" />
              ) : (
                <span className="file-card__icon">PDF</span>
              )}
              <div className="file-card__meta">
                <strong>{file.name}</strong>
                <span className="muted">{humanSize(file.size)}</span>
              </div>
              <button type="button" className="btn btn--ghost btn--sm" onClick={clearFile}>
                Remove
              </button>
            </div>
          )}
        </section>

        <section className="card">
          <h2 className="card__title">2. Patient context <span className="muted">(optional)</span></h2>
          <p className="muted small">
            Hints help the AI when handwriting is unclear. You can review everything before saving.
          </p>
          <div className="field-row">
            <label className="field">
              <span>Patient name</span>
              <input value={contextName} onChange={(e) => setContextName(e.target.value)} placeholder="e.g. Ramesh Kumar" />
            </label>
            <label className="field">
              <span>Phone</span>
              <input value={contextPhone} onChange={(e) => setContextPhone(e.target.value)} placeholder="e.g. 98765 43210" />
            </label>
            <label className="field">
              <span>Patient ID</span>
              <input value={contextPid} onChange={(e) => setContextPid(e.target.value)} placeholder="e.g. PID-1002" />
            </label>
          </div>

          <label className="check">
            <input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} />
            <span>
              I confirm the patient has consented to this scan being processed for their clinical record.
            </span>
          </label>

          <button type="submit" className="btn btn--primary" disabled={!file || !consent || submitting}>
            {submitting ? (
              <>
                <span className="spinner spinner--sm" aria-hidden="true" /> Analysing…
              </>
            ) : (
              'Upload & analyse'
            )}
          </button>
          {submitting ? <p className="muted small">This can take up to a minute while the AI reads the scan.</p> : null}
        </section>
      </form>
    </div>
  )
}
