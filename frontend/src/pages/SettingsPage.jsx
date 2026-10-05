import { useRef, useState } from 'react'
import { del, upload } from '../lib/api'
import { LOGO_SRC, refreshLogo } from '../lib/logo'
import ErrorBanner from '../components/ErrorBanner'

const ACCEPTED = ['image/png', 'image/jpeg', 'image/webp']
const MAX_MB = 2

export default function SettingsPage() {
  const inputRef = useRef(null)
  const [selected, setSelected] = useState(null)
  const [preview, setPreview] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const [notice, setNotice] = useState(null)
  const [imgError, setImgError] = useState(false)

  function pickFile(file) {
    setError(null)
    setNotice(null)
    setImgError(false)
    if (!file) return
    if (!ACCEPTED.includes(file.type)) {
      setError({ message: 'Unsupported file. Use a PNG, JPEG or WebP image.' })
      return
    }
    if (file.size > MAX_MB * 1024 * 1024) {
      setError({ message: `Image is too large. The limit is ${MAX_MB} MB.` })
      return
    }
    setSelected(file)
    setPreview(URL.createObjectURL(file))
  }

  async function handleUpload(event) {
    event.preventDefault()
    if (!selected) return
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      const form = new FormData()
      form.append('file', selected)
      await upload('/api/settings/logo', form)
      refreshLogo()
      setNotice('Logo uploaded.')
      setSelected(null)
      if (preview) URL.revokeObjectURL(preview)
      setPreview(null)
      if (inputRef.current) inputRef.current.value = ''
      setImgError(false)
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  async function handleRemove() {
    if (!window.confirm('Delete the clinic logo? This cannot be undone.')) return
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      await del('/api/settings/logo')
      refreshLogo()
      setImgError(true)
      setNotice('Logo deleted.')
      setSelected(null)
      if (preview) URL.revokeObjectURL(preview)
      setPreview(null)
      if (inputRef.current) inputRef.current.value = ''
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="page">
      <header className="page__head">
        <div>
          <h1>Settings</h1>
          <p className="muted">Manage the clinic brand shown across the app.</p>
        </div>
      </header>

      <ErrorBanner error={error} />

      {notice ? (
        <div className="banner banner--info">{notice}</div>
      ) : null}

      <div className="grid grid--detail">
        <section className="card">
          <h2 className="card__title">Logo</h2>
          <p className="muted small">
            The logo appears in the sidebar and on the sign-in / registration pages.
            PNG, JPEG or WebP, up to {MAX_MB} MB.
          </p>

          <div className="profile" style={{ marginTop: 14 }}>
            <span className="brand-mark brand-mark--lg">
              {!imgError ? (
                <img
                  className="brand-mark__img"
                  src={LOGO_SRC}
                  alt="Current logo"
                  onError={() => setImgError(true)}
                />
              ) : (
                <span className="muted small">No logo</span>
              )}
            </span>
            <div className="profile__info">
              <strong className="profile__name">Current logo</strong>
              <div className="muted small">
                {imgError ? 'No logo has been uploaded yet.' : 'This is what everyone sees.'}
              </div>
            </div>
          </div>

          <form onSubmit={handleUpload}>
            <label
              className="dropzone"
              onDragOver={(e) => e.preventDefault()}
              onDrop={(e) => {
                e.preventDefault()
                pickFile(e.dataTransfer.files?.[0])
              }}
            >
              <input
                ref={inputRef}
                type="file"
                accept={ACCEPTED.join(',')}
                onChange={(e) => pickFile(e.target.files?.[0])}
                hidden
              />
              <svg viewBox="0 0 24 24" width="30" height="30" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M12 16V4m0 0L8 8m4-4 4 4" />
                <path d="M4 16v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2" />
              </svg>
              <strong>{selected ? selected.name : 'Choose a logo image'}</strong>
              <span className="muted">or drag &amp; drop here</span>
            </label>

            <div className="scan-actions">
              <button type="submit" className="btn btn--primary" disabled={busy || !selected}>
                {busy ? 'Saving…' : 'Upload logo'}
              </button>
              <button
                type="button"
                className="btn btn--danger"
                onClick={handleRemove}
                disabled={busy || imgError}
                title={imgError ? 'No logo to delete' : 'Delete the clinic logo'}
              >
                <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <path d="M3 6h18M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6M10 11v6M14 11v6" />
                </svg>
                Delete logo
              </button>
            </div>
          </form>
        </section>

        <section className="card">
          <h2 className="card__title">About</h2>
          <p className="muted small">
            More clinic settings (name, address, contact details, printer
            defaults) can be added here later.
          </p>
        </section>
      </div>
    </div>
  )
}