import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { upload } from '../lib/api'
import ErrorBanner from '../components/ErrorBanner'
import FolderIcon from '../components/FolderIcon'

const ACCEPTED = ['image/jpeg', 'image/png', 'image/webp', 'application/pdf']
const MAX_MB = 20

function humanSize(bytes) {
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function pageThumb(page, previews) {
  if (page.thumbnail) return `data:image/png;base64,${page.thumbnail}`
  const local = previews[page.sourceIndex]
  return local || null
}

function saveBatchToSession(batchId, pages) {
  try {
    const record = {
      batchId,
      pages: pages
        .filter((page) => page.draftId)
        .map((page) => ({
          id: page.draftId,
          page: page.page,
          pageCount: page.pageCount,
          documentType: page.documentType,
          sourceName: page.sourceName,
          name: page.extracted?.patient?.name || page.matchedPatient?.name || '',
        })),
    }
    sessionStorage.setItem(`rx_batch_${batchId}`, JSON.stringify(record))
  } catch {
    // sessionStorage is best-effort navigation sugar only.
  }
}

export default function UploadPage() {
  const navigate = useNavigate()
  const inputRef = useRef(null)

  const [files, setFiles] = useState([])
  const [previews, setPreviews] = useState({})
  const [dragging, setDragging] = useState(false)
  const [consent, setConsent] = useState(false)
  const [contextName, setContextName] = useState('')
  const [contextPhone, setContextPhone] = useState('')
  const [contextPid, setContextPid] = useState('')
  const [error, setError] = useState(null)
  const [scanning, setScanning] = useState(false)
  const [scanned, setScanned] = useState(false)
  const [scanPages, setScanPages] = useState([])
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState(null)

  useEffect(() => {
    return () => {
      Object.values(previews).forEach((url) => URL.revokeObjectURL(url))
    }
  }, [previews])

  function addFiles(fileList) {
    setError(null)
    setScanned(false)
    setScanPages([])
    setResult(null)

    const accepted = []
    const nextPreviews = { ...previews }
    for (const file of fileList) {
      if (!ACCEPTED.includes(file.type)) {
        setError({ message: `Unsupported file "${file.name}". Use a JPEG, PNG, WebP image or a PDF.` })
        continue
      }
      if (file.size > MAX_MB * 1024 * 1024) {
        setError({ message: `"${file.name}" is too large. The limit is ${MAX_MB} MB per file.` })
        continue
      }
      accepted.push(file)
    }
    if (!accepted.length) return

    const startIndex = files.length
    accepted.forEach((file, offset) => {
      if (file.type.startsWith('image/')) {
        nextPreviews[startIndex + offset] = URL.createObjectURL(file)
      }
    })
    setPreviews(nextPreviews)
    setFiles((prev) => [...prev, ...accepted])
  }

  function removeFile(index) {
    setScanned(false)
    setScanPages([])
    setResult(null)
    if (previews[index]) {
      URL.revokeObjectURL(previews[index])
    }
    setPreviews((prev) => {
      const next = {}
      Object.entries(prev)
        .filter(([key]) => Number(key) !== index)
        .forEach(([key, value]) => {
          next[Number(key) > index ? Number(key) - 1 : Number(key)] = value
        })
      return next
    })
    setFiles((prev) => prev.filter((_, i) => i !== index))
  }

  function clearFiles() {
    Object.values(previews).forEach((url) => URL.revokeObjectURL(url))
    setPreviews({})
    setFiles([])
    setScanned(false)
    setScanPages([])
    setResult(null)
    if (inputRef.current) inputRef.current.value = ''
  }

  function handleDrop(event) {
    event.preventDefault()
    setDragging(false)
    addFiles(event.dataTransfer.files ?? [])
  }

  function applyScanContext(pages) {
    const first = pages.find((page) => page.matchedPatient) || pages[0]
    const match = first?.matchedPatient
    const info = first?.extracted?.patient ?? {}
    if (match) {
      if (match.name) setContextName(match.name)
      if (match.pid) setContextPid(match.pid)
      if (match.phone) setContextPhone(match.phone)
    } else {
      if (info.name) setContextName(info.name)
      if (info.pid) setContextPid(info.pid)
    }
  }

  async function handleScan() {
    if (!files.length) return
    setError(null)
    setScanning(true)
    const form = new FormData()
    files.forEach((file) => form.append('files', file))
    try {
      const res = await upload('/api/prescriptions/scan-pages', form)
      const pages = res.pages ?? []
      setScanPages(pages)
      applyScanContext(pages)
      setScanned(true)
    } catch (err) {
      setError(err)
    } finally {
      setScanning(false)
    }
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (!files.length || !consent) return

    setError(null)
    setSubmitting(true)
    const form = new FormData()
    files.forEach((file) => form.append('files', file))
    form.append('consent', 'true')
    if (contextName.trim()) form.append('contextName', contextName.trim())
    if (contextPhone.trim()) form.append('contextPhone', contextPhone.trim())
    if (contextPid.trim()) form.append('contextPid', contextPid.trim())

    try {
      const res = await upload('/api/prescriptions/upload-pages', form)
      saveBatchToSession(res.batchId, res.pages ?? [])
      setResult(res)
      setSubmitting(false)
    } catch (err) {
      setError(err)
      setSubmitting(false)
    }
  }

  function resetScan() {
    setResult(null)
    clearFiles()
  }

  const savedPages = result?.pages ?? []
  const savedCount = savedPages.filter((page) => page.draftId).length
  const failedPages = savedPages.filter((page) => page.error)

  return (
    <div className="page">
      <header className="page__head">
        <div>
          <h1>New scan</h1>
          <p className="muted">
            Upload prescription photos or PDFs. Each page is scanned on its own — a page with a PID
            (<code>SNP</code> + 12 digits) is treated as a prescription, everything else as a report.
          </p>
        </div>
      </header>

      <ErrorBanner error={error} />

      {result ? (
        <section className="card">
          <div className="card__head">
            <h2 className="card__title">
              Scan complete — {savedCount} page{savedCount === 1 ? '' : 's'} saved
            </h2>
            <button type="button" className="btn btn--ghost btn--sm" onClick={resetScan}>New scan</button>
          </div>

          <ul className="list">
            {savedPages.map((page, index) => (
              <li key={`saved-${index}`} className="list__item">
                <div className="table__patient">
                  <span className="table__folder" aria-hidden="true"><FolderIcon size={18} /></span>
                  <div>
                    <strong>
                      {page.documentType === 'PRESCRIPTION' ? 'Prescription' : 'Report'} · page {page.page}
                      {page.pageCount > 1 ? ` of ${page.pageCount}` : ''}
                    </strong>
                    <div className="muted small">
                      {[page.extracted?.patient?.name || page.matchedPatient?.name,
                        page.extracted?.patient?.pid,
                        page.sourceName].filter(Boolean).join(' · ') || 'No details'}
                    </div>
                  </div>
                </div>
                <div className="list__right">
                  {page.error ? (
                    <span className="pill pill--danger">Failed</span>
                  ) : (
                    <button
                      type="button"
                      className="btn btn--primary btn--sm"
                      onClick={() => navigate(`/review/${page.draftId}`)}
                    >
                      Review
                    </button>
                  )}
                </div>
              </li>
            ))}
          </ul>

          {failedPages.length ? (
            <div className="banner banner--warn banner--inline">
              {failedPages.length} page{failedPages.length === 1 ? '' : 's'} could not be scanned and were skipped.
            </div>
          ) : null}
        </section>
      ) : null}

      <form className="grid grid--upload" onSubmit={handleSubmit}>
        <section className="card">
          <h2 className="card__title">1. Prescription files</h2>

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
              multiple
              onChange={(e) => addFiles(Array.from(e.target.files ?? []))}
              hidden
            />
            <svg viewBox="0 0 24 24" width="34" height="34" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M12 16V4m0 0L8 8m4-4 4 4" />
              <path d="M4 16v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2" />
            </svg>
            <strong>Drag &amp; drop files here</strong>
            <span className="muted">
              or click to browse — JPEG, PNG, WebP or PDF (multi-page) up to {MAX_MB} MB each
            </span>
          </label>

          {files.length ? (
            <ul className="list">
              {files.map((file, index) => (
                <li key={`${file.name}-${index}`} className="list__item">
                  <div className="table__patient">
                    {previews[index] ? (
                      <img src={previews[index]} alt="" className="table__thumb" />
                    ) : (
                      <span className="file-card__icon">PDF</span>
                    )}
                    <div>
                      <strong>{file.name}</strong>
                      <div className="muted small">{humanSize(file.size)}</div>
                    </div>
                  </div>
                  <button type="button" className="btn btn--ghost btn--sm" onClick={() => removeFile(index)}>
                    Remove
                  </button>
                </li>
              ))}
            </ul>
          ) : null}

          {files.length ? (
            <div className="scan-actions">
              <button type="button" className="btn btn--primary" onClick={handleScan} disabled={scanning}>
                {scanning ? (
                  <>
                    <span className="spinner spinner--sm" aria-hidden="true" /> Scanning…
                  </>
                ) : scanned ? (
                  'Re-scan pages'
                ) : (
                  'Scan pages'
                )}
              </button>
              <button type="button" className="btn btn--ghost btn--sm" onClick={clearFiles}>
                Clear all
              </button>
            </div>
          ) : null}

          {scanPages.length ? (
            <div className="block">
              <h4>Pages found ({scanPages.length})</h4>
              <ul className="list">
                {scanPages.map((page, index) => {
                  const thumb = pageThumb(page, previews)
                  const isPrescription = page.documentType === 'PRESCRIPTION'
                  return (
                    <li key={`scan-${index}`} className="list__item">
                      <div className="table__patient">
                        {thumb ? (
                          <img src={thumb} alt="" className="table__thumb" />
                        ) : (
                          <span className="table__folder" aria-hidden="true"><FolderIcon size={18} /></span>
                        )}
                        <div>
                          <strong>
                            Page {page.page}
                            {page.pageCount > 1 ? ` of ${page.pageCount}` : ''}
                          </strong>
                          <div className="muted small">
                            {[page.extracted?.patient?.name || page.matchedPatient?.name,
                              page.extracted?.patient?.pid].filter(Boolean).join(' · ') || 'No details'}
                          </div>
                        </div>
                      </div>
                      <span className={`pill ${isPrescription ? 'pill--ok' : 'pill--info'}`}>
                        {isPrescription ? 'Prescription' : 'Report'}
                      </span>
                    </li>
                  )
                })}
              </ul>
            </div>
          ) : null}
        </section>

        <section className="card">
          <h2 className="card__title">2. Patient context <span className="muted">(optional)</span></h2>
          <p className="muted small">
            Hints help the AI when handwriting is unclear. You can review every page before saving.
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
              <input value={contextPid} onChange={(e) => setContextPid(e.target.value)} placeholder="e.g. SNP260404071826" />
            </label>
          </div>

          <label className="check">
            <input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} />
            <span>
              I confirm the patient has consented to this scan being processed for their clinical record.
            </span>
          </label>

          <button type="submit" className="btn btn--primary" disabled={!files.length || !consent || !scanned || submitting}>
            {submitting ? (
              <>
                <span className="spinner spinner--sm" aria-hidden="true" /> Analysing…
              </>
            ) : (
              'Save & analyse all pages'
            )}
          </button>
          {!scanned ? (
            <p className="muted small">Scan the pages first, then save.</p>
          ) : submitting ? (
            <p className="muted small">This can take a minute or more while the AI reads every page.</p>
          ) : (
            <p className="muted small">
              {scanPages.length} page{scanPages.length === 1 ? '' : 's'} will be saved as separate records.
            </p>
          )}
        </section>
      </form>
    </div>
  )
}
