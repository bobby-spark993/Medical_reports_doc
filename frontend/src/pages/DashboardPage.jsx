import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { motion } from 'motion/react'
import { get } from '../lib/api'
import { useAuth } from '../lib/auth'
import { formatDate } from '../lib/format'
import { formContainer, fieldItem } from '../lib/motion'
import ErrorBanner from '../components/ErrorBanner'
import Loading from '../components/Loading'

const MotionLink = motion.create(Link)

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

      <motion.div
        className="hero-actions"
        variants={formContainer}
        initial="hidden"
        animate="show"
      >
        <MotionLink
          to="/upload"
          className="action-card action-card--primary"
          variants={fieldItem}
          whileHover={{ y: -2 }}
          whileTap={{ scale: 0.99 }}
        >
          <span className="action-card__icon">＋</span>
          <strong>Digitise a prescription</strong>
          <span>Upload a scan and let the AI extract the fields.</span>
        </MotionLink>
        <MotionLink
          to="/patients"
          className="action-card"
          variants={fieldItem}
          whileHover={{ y: -2 }}
          whileTap={{ scale: 0.99 }}
        >
          <span className="action-card__icon">🔍</span>
          <strong>Browse patients</strong>
          <span>Search records and open full visit history.</span>
        </MotionLink>
        <motion.div className="action-card action-card--stat" variants={fieldItem}>
          <span className="stat__value">{total ?? '—'}</span>
          <span className="muted">Patients on record</span>
        </motion.div>
      </motion.div>

      <section className="card">
        <div className="card__head">
          <h2 className="card__title">Recent patients</h2>
          <Link to="/patients" className="btn btn--ghost btn--sm">View all</Link>
        </div>

        {loading ? (
          <Loading label="Loading recent patients…" />
        ) : recent?.items?.length ? (
          <motion.ul
            className="list"
            variants={formContainer}
            initial="hidden"
            animate="show"
          >
            {recent.items.map((patient) => (
              <motion.li key={patient.id} className="list__item" variants={fieldItem} layout>
                <div>
                  <Link to={`/patients/${patient.id}`} className="table__link btn btn--ghost btn--sm">{patient.name}</Link>
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
              </motion.li>
            ))}
          </motion.ul>
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
