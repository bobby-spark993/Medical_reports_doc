import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { get } from '../lib/api'
import { formatDate, formatDateTime } from '../lib/format'
import ErrorBanner from '../components/ErrorBanner'
import Loading from '../components/Loading'
import FolderIcon from '../components/FolderIcon'

const LIMIT = 20

export default function PatientsPage() {
  const [search, setSearch] = useState('')
  const [debounced, setDebounced] = useState('')
  const [page, setPage] = useState(1)
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    const timer = setTimeout(() => {
      setDebounced(search.trim())
      setPage(1)
    }, 300)
    return () => clearTimeout(timer)
  }, [search])

  function load() {
    setLoading(true)
    setError(null)
    const params = new URLSearchParams({ page: String(page), limit: String(LIMIT) })
    if (debounced) params.set('search', debounced)
    get(`/api/patients?${params.toString()}`)
      .then(setData)
      .catch(setError)
      .finally(() => setLoading(false))
  }

  useEffect(load, [debounced, page])

  const pagination = data?.pagination

  return (
    <div className="page">
      <header className="page__head">
        <div>
          <h1>Patients</h1>
          <p className="muted">
            {pagination ? `${pagination.total} patient${pagination.total === 1 ? '' : 's'}` : 'Search patient records'}
          </p>
        </div>
        <Link to="/upload" className="btn btn--primary">+ New scan</Link>
      </header>

      <div className="toolbar">
        <input
          className="input search"
          type="search"
          placeholder="Search by name, patient ID or phone…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </div>

      <ErrorBanner error={error} onRetry={load} />

      <div className="card card--table">
        {loading && !data ? (
          <Loading label="Loading patients…" />
        ) : data?.items?.length ? (
          <table className="table">
            <thead>
              <tr>
                <th>Patient</th>
                <th>Patient ID</th>
                <th>Age / Gender</th>
                <th>Phone</th>
                <th className="num">Visits</th>
                <th>Next follow-up</th>
                <th>Updated</th>
                <th aria-label="Actions" />
              </tr>
            </thead>
            <tbody>
              {data.items.map((patient) => (
                <tr key={patient.id}>
                  <td>
                    <div className="table__patient">
                      <span className="table__folder" aria-hidden="true"><FolderIcon size={18} /></span>
                      <div>
                        <Link to={`/patients/${patient.id}`} className="table__link">{patient.name}</Link>
                        {patient.address ? <div className="muted small">{patient.address}</div> : null}
                      </div>
                    </div>
                  </td>
                  <td>{patient.pid || '—'}</td>
                  <td>{[patient.age, patient.gender].filter(Boolean).join(' / ') || '—'}</td>
                  <td>{patient.phone || '—'}</td>
                  <td className="num">{patient.visitCount}</td>
                  <td>{patient.nextFollowUp ? formatDate(patient.nextFollowUp) : '—'}</td>
                  <td className="muted small">{patient.updatedAt ? formatDateTime(patient.updatedAt) : '—'}</td>
                  <td className="num">
                    <Link to={`/patients/${patient.id}`} className="btn btn--ghost btn--sm">Open</Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <div className="empty">
            <p>No patients found{debounced ? ` for “${debounced}”` : ''}.</p>
            <Link to="/upload" className="btn btn--primary">Digitise the first prescription</Link>
          </div>
        )}

        {pagination && pagination.totalPages > 1 ? (
          <div className="pager">
            <button type="button" className="btn btn--ghost btn--sm" disabled={!pagination.hasPrev} onClick={() => setPage((p) => p - 1)}>
              ← Previous
            </button>
            <span className="muted small">
              Page {pagination.page} of {pagination.totalPages}
            </span>
            <button type="button" className="btn btn--ghost btn--sm" disabled={!pagination.hasNext} onClick={() => setPage((p) => p + 1)}>
              Next →
            </button>
          </div>
        ) : null}
      </div>
    </div>
  )
}
