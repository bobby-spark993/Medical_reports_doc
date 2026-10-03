import { useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { upload } from '../lib/api'
import ErrorBanner from '../components/ErrorBanner'
import FolderIcon from '../components/FolderIcon'

const ACCEPTED = ['image/jpeg', 'image/png', 'image/webp', 'application/pdf']
const MAX_MB = 20

function humanSize(bytes) {
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function newBatchId() {
  return crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function typeLabel(documentType) {
  return documentType === 'PRESCRIPTION' ? 'Prescription' : 'Report'
}

// Name/age/gender come from the extracted page, enriched by any matched folder.
function patientSummary(entry) {
  const info = entry.extracted?.patient ?? {}
  const match = entry.matchedPatient ?? {}
  return {
    name: match.name || info.name || '',
    age: match.age || info.age || info.age_years || '',
    gender: match.gender || info.gender || '',
    id: match.pid || info.pid || info.patient_ref_no || '',
  }
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
  const batchRef = useRef(newBatchId())

  const [files, setFiles] = useState([])
  const [previews, setPreviews] = useState({})
  const [dragging, setDragging] = useState(false)
  const [pages, setPages] = useState([])
  const [preparing, setPreparing] = useState(false)
  const [busy, setBusy] = useState(false)
  const [consent, setConsent] = useState(false)
  const [contextName, setContextName] = useState('')
  const [contextPhone, setContextPhone] = useState('')
  const [contextPid, setContextPid] = useState('')
  const [error, setError] = useState(null)

  function patchPage(key, patch) {
    setPages((prev) => {
      const next = prev.map((page) => (page.key === key ? { ...page, ...patch } : page))
      saveBatchToSession(batchRef.current, next)
      return next
    })
  }

  function mergeContext(extracted, matched) {
    const info = extracted?.patient ?? {}
    if (!contextName && (matched?.name || info.name)) setContextName(matched?.name || info.name)
    if (!contextPid && (matched?.pid || info.pid)) setContextPid(matched?.pid || info.pid)
    if (!contextPhone && matched?.phone) setContextPhone(matched.phone)
  }

  function buildEntries(fileList, infos) {
    const entries = []
    for (const info of infos) {
      for (let page = 1; page <= info.pageCount; page++) {
        entries.push({
          key: `${info.sourceIndex}-${page}`,
          sourceIndex: info.sourceIndex,
          sourceName: info.sourceName,
          page,
          pageCount: info.pageCount,
          status: 'idle',
          documentType: null,
          extracted: null,
          matchedPatient: null,
          thumbnail: null,
          draftId: null,
          error: null,
        })
      }
    }
    return entries
  }

  async function preparePages(fileList) {
    if (!fileList.length) {
      setPages([])
      return
    }
    setPreparing(true)
    setError(null)
    const form = new FormData()
    fileList.forEach((file) => form.append('files', file))
    try {
      const res = await upload('/api/prescriptions/page-info', form)
      setPages(buildEntries(fileList, res.files ?? []))
    } catch (err) {
      setError(err)
      setPages([])
    } finally {
      setPreparing(false)
    }
  }

  function addFiles(fileList) {
    setError(null)
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
    const nextFiles = [...files, ...accepted]
    setPreviews(nextPreviews)
    setFiles(nextFiles)
    preparePages(nextFiles)
  }

  function removeFile(index) {
    if (previews[index]) URL.revokeObjectURL(previews[index])
    const nextPreviews = {}
    Object.entries(previews)
      .filter(([key]) => Number(key) !== index)
      .forEach(([key, value]) => {
        nextPreviews[Number(key) > index ? Number(key) - 1 : Number(key)] = value
      })
    const nextFiles = files.filter((_, i) => i !== index)
    setPreviews(nextPreviews)
    setFiles(nextFiles)
    preparePages(nextFiles)
  }

  function clearFiles() {
    Object.values(previews).forEach((url) => URL.revokeObjectURL(url))
    setPreviews({})
    setFiles([])
    setPages([])
    batchRef.current = newBatchId()
    if (inputRef.current) inputRef.current.value = ''
  }

  function handleDrop(event) {
    event.preventDefault()
    setDragging(false)
    addFiles(event.dataTransfer.files ?? [])
  }

  async function scanEntry(entry) {
    const file = files[entry.sourceIndex]
    if (!file) return
    patchPage(entry.key, { status: 'scanning', error: null })
    const form = new FormData()
    form.append('file', file)
    form.append('page', String(entry.page))
    try {
      const res = await upload('/api/prescriptions/scan-page', form)
      const item = res.pages?.[0]
      patchPage(entry.key, {
        status: 'scanned',
        documentType: item?.documentType ?? null,
        extracted: item?.extracted ?? null,
        matchedPatient: item?.matchedPatient ?? null,
        thumbnail: item?.thumbnail ?? null,
      })
      mergeContext(item?.extracted, item?.matchedPatient)
    } catch (err) {
      patchPage(entry.key, { status: 'error', error: err.message || 'Scan failed' })
    }
  }

  async function saveEntry(entry) {
    const file = files[entry.sourceIndex]
    if (!file || !consent) return
    patchPage(entry.key, { status: 'saving', error: null })
    const form = new FormData()
    form.append('file', file)
    form.append('page', String(entry.page))
    form.append('pageCount', String(entry.pageCount))
    form.append('batchId', batchRef.current)
    form.append('consent', 'true')
    if (contextName.trim()) form.append('contextName', contextName.trim())
    if (contextPhone.trim()) form.append('contextPhone', contextPhone.trim())
    if (contextPid.trim()) form.append('contextPid', contextPid.trim())
    if (entry.extracted) form.append('rawAiJson', JSON.stringify(entry.extracted))
    try {
      const res = await upload('/api/prescriptions/save-page', form)
      patchPage(entry.key, { status: 'saved', draftId: res.id })
    } catch (err) {
      patchPage(entry.key, { status: 'error', error: err.message || 'Save failed' })
    }
  }

  async function scanAll() {
    setBusy(true)
    for (const entry of pages) {
      if (entry.status === 'idle' || entry.status === 'error') {
        await scanEntry(entry)
      }
    }
    setBusy(false)
  }

  async function saveAll() {
    setBusy(true)
    for (const entry of pages) {
      if (entry.status === 'scanned') {
        await saveEntry(entry)
      }
    }
    setBusy(false)
  }

  const scannedCount = pages.filter((page) => page.status === 'scanned').length
  const savedCount = pages.filter((page) => page.status === 'saved').length
  const totalPages = pages.length

  return (
    <div className="page">
      <header className="page__head">
        <div>
          <h1>New scan</h1>
          <p className="muted">
            Upload prescription photos or PDFs. Pages are scanned one at a time — a page with a PID
            (<code>SNP</code> + 12 digits) is a prescription, everything else is a report.
          </p>
        </div>
      </header>

      <ErrorBanner error={error} />

      <div className="grid grid--upload">
        <section className="card">
          <h2 className="card__title">1. Files &amp; pages</h2>

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

          {preparing ? <p className="muted small">Reading page counts…</p> : null}

          {pages.length ? (
            <>
              <div className="scan-actions">
                <button
                  type="button"
                  className="btn btn--primary"
                  onClick={scanAll}
                  disabled={busy || !pages.some((p) => p.status === 'idle' || p.status === 'error')}
                >
                  Scan all pages
                </button>
                <button
                  type="button"
                  className="btn btn--ghost btn--sm"
                  onClick={saveAll}
                  disabled={busy || !scannedCount || !consent}
                >
                  Save {scannedCount ? `scanned (${scannedCount})` : 'all'}
                </button>
                <button type="button" className="btn btn--ghost btn--sm" onClick={clearFiles}>
                  Clear all
                </button>
              </div>

              <div className="block">
                <h4>
                  Pages ({totalPages}) · {savedCount} saved
                </h4>
                <ul className="list">
                  {pages.map((entry) => {
                    const thumb = entry.thumbnail
                      ? `data:image/png;base64,${entry.thumbnail}`
                      : previews[entry.sourceIndex]
                    return (
                      <li key={entry.key} className="list__item">
                        <div className="table__patient">
                          {thumb ? (
                            <img src={thumb} alt="" className="table__thumb" />
                          ) : (
                            <span className="table__folder" aria-hidden="true"><FolderIcon size={18} /></span>
                          )}
                          <div>
                            <strong>
                              Page {entry.page}
                              {entry.pageCount > 1 ? ` of ${entry.pageCount}` : ''}
                              {entry.documentType ? ` · ${typeLabel(entry.documentType)}` : ''}
                            </strong>
                            {entry.documentType ? (
                              <>
                                <div className="muted small">
                                  {(() => {
                                    const who = patientSummary(entry)
                                    return [who.name, who.age, who.gender, who.id].filter(Boolean).join(' · ') || 'No patient details on this page'
                                  })()}
                                </div>
                                <div className="muted small">
                                  {entry.matchedPatient
                                    ? `Links to: ${entry.matchedPatient.name}${entry.matchedPatient.pid ? ` (${entry.matchedPatient.pid})` : ''}`
                                    : 'Will create a new patient folder (matched by name + age + gender)'}
                                </div>
                              </>
                            ) : (
                              <div className="muted small">{entry.error || entry.sourceName}</div>
                            )}
                          </div>
                        </div>
                        <div className="list__right">
                          {entry.status === 'idle' || entry.status === 'error' ? (
                            <button type="button" className="btn btn--ghost btn--sm" onClick={() => scanEntry(entry)} disabled={busy}>
                              Scan
                            </button>
                          ) : entry.status === 'scanning' ? (
                            <span className="muted small"><span className="spinner spinner--sm" aria-hidden="true" /> Scanning…</span>
                          ) : entry.status === 'scanned' ? (
                            <>
                              <span className={`pill ${entry.documentType === 'PRESCRIPTION' ? 'pill--ok' : 'pill--info'}`}>
                                {typeLabel(entry.documentType)}
                              </span>
                              {entry.matchedPatient ? (
                                <Link to={`/patients/${entry.matchedPatient.id}`} className="btn btn--ghost btn--sm">Folder</Link>
                              ) : null}
                              <button type="button" className="btn btn--primary btn--sm" onClick={() => saveEntry(entry)} disabled={busy || !consent}>
                                Save
                              </button>
                            </>
                          ) : entry.status === 'saving' ? (
                            <span className="muted small"><span className="spinner spinner--sm" aria-hidden="true" /> Saving…</span>
                          ) : entry.status === 'saved' ? (
                            <>
                              <span className={`pill ${entry.documentType === 'PRESCRIPTION' ? 'pill--ok' : 'pill--info'}`}>
                                {typeLabel(entry.documentType)}
                              </span>
                              {entry.matchedPatient ? (
                                <Link to={`/patients/${entry.matchedPatient.id}`} className="btn btn--ghost btn--sm">Folder</Link>
                              ) : null}
                              <button type="button" className="btn btn--primary btn--sm" onClick={() => navigate(`/review/${entry.draftId}`)}>
                                Review →
                              </button>
                            </>
                          ) : null}
                        </div>
                      </li>
                    )
                  })}
                </ul>
              </div>
            </>
          ) : null}
        </section>

        <section className="card">
          <h2 className="card__title">2. Patient context <span className="muted">(optional)</span></h2>
          <p className="muted small">
            Auto-filled from the scanned pages when available. You can review every page before saving.
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

          <p className="muted small">
            {totalPages
              ? `${totalPages} page${totalPages === 1 ? '' : 's'} · ${savedCount} saved. Consent is required before saving.`
              : 'Add files to see the page list.'}
          </p>
        </section>
      </div>
    </div>
  )
}
