import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { get } from '../lib/api'
import { useAuth } from '../lib/auth'
import { formatDate } from '../lib/format'
import ErrorBanner from '../components/ErrorBanner'
import Loading from '../components/Loading'

export default function DashboardPage() {
  const { user } = useAuth()
  const [recent, setRecent] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let active = true
    get('/api/patients?page=1&limit=5')
      .then((res) => active && setRecent(res))
      .catch((err) => active && setError(err))
      .finally(() => active && setLoading(false))
    return () => {
      active = false
    }
  }, [])

  const total = recent?.pagination?.total

  return (
    <div className="page">
      <header className="page__head">
        <div>
          <h1>Welcome back, {user?.name?.split(' ')[0]}</h1>
          <p className="muted">Digitise handwritten prescriptions, then verify the AI extraction before saving.</p>
        </div>
      </header>

      <ErrorBanner error={error} />

      <div className="hero-actions">
        <Link to="/upload" className="action-card action-card--primary">
          <span className="action-card__icon">＋</span>
          <strong>Digitise a prescription</strong>
          <span>Upload a scan and let the AI extract the fields.</span>
        </Link>
        <Link to="/patients" className="action-card">
          <span className="action-card__icon">🔍</span>
          <strong>Browse patients</strong>
          <span>Search records and open full visit history.</span>
        </Link>
        <div className="action-card action-card--stat">
          <span className="stat__value">{total ?? '—'}</span>
          <span className="muted">Patients on record</span>
        </div>
      </div>

      <section className="card">
        <div className="card__head">
          <h2 className="card__title">Recent patients</h2>
          <Link to="/patients" className="btn btn--ghost btn--sm">View all</Link>
        </div>

        {loading ? (
          <Loading label="Loading recent patients…" />
        ) : recent?.items?.length ? (
          <ul className="list">
            {recent.items.map((patient) => (
              <li key={patient.id} className="list__item">
                <div>
                  <Link to={`/patients/${patient.id}`} className="table__link">{patient.name}</Link>
                  <div className="muted small">
                    {[patient.pid, patient.age, patient.gender, patient.phone].filter(Boolean).join(' · ') || 'No details'}
                  </div>
                </div>
                <div className="list__right">
                  <span className="muted small">{patient.visitCount} visit{patient.visitCount === 1 ? '' : 's'}</span>
                  {patient.nextFollowUp ? (
                    <span className="pill pill--info">Follow-up {formatDate(patient.nextFollowUp)}</span>
                  ) : null}
                </div>
              </li>
            ))}
          </ul>
        ) : (
          <div className="empty">
            <p>No patients yet.</p>
            <Link to="/upload" className="btn btn--primary">Digitise the first prescription</Link>
          </div>
        )}
      </section>
    </div>
  )
}
