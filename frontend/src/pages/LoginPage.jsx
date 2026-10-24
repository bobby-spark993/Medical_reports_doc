import { useEffect, useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { motion } from 'motion/react'
import { useAuth } from '../lib/auth'
import { cardIn, fadeUp, formContainer, fieldItem, pressable } from '../lib/motion'
import Loading from '../components/Loading'
import BrandMark from '../components/BrandMark'
import AuthScene from '../components/AuthScene'
import Footer from '../components/Footer'
import LockedClock from '../components/LockedClock'
import PasswordInput from '../components/PasswordInput'

// Mirrors the server-side limit (app.rate-limit.login-max / -login-window-
// minutes). The server is the real gate; this keeps the page in step with it.
const MAX_ATTEMPTS = 5
const LOCKOUT_MINUTES = 15

function formatCountdown(seconds) {
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  return `${mins}:${String(secs).padStart(2, '0')}`
}

export default function LoginPage() {
  const { user, loading, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  // Failed attempts, kept per email so one mistyped account does not lock
  // out another.
  const [attempts, setAttempts] = useState({ email: '', count: 0 })
  // Timestamp until which the form is locked.
  const [lockedUntil, setLockedUntil] = useState(0)
  const [now, setNow] = useState(() => Date.now())

  const redirectTo = location.state?.from?.pathname ?? '/'

  const locked = lockedUntil > now
  const secondsLeft = Math.max(0, Math.ceil((lockedUntil - now) / 1000))
  const usedAttempts = attempts.email === email.trim().toLowerCase() ? attempts.count : 0
  const attemptsLeft = Math.max(0, MAX_ATTEMPTS - usedAttempts)

  // Tick the clock while the form is locked so the countdown stays live, and
  // release the lock once it runs out.
  useEffect(() => {
    if (!locked) return undefined
    const timer = setInterval(() => {
      const current = Date.now()
      setNow(current)
      if (current >= lockedUntil) {
        // The lock has expired - start fresh.
        setLockedUntil(0)
        setAttempts({ email: '', count: 0 })
        setError(null)
      }
    }, 1000)
    return () => clearInterval(timer)
  }, [locked, lockedUntil])

  if (loading) return <Loading fullscreen label="Checking your session…" />
  if (user) return <Navigate to={redirectTo} replace />

  async function handleSubmit(event) {
    event.preventDefault()
    if (locked) return
    setError(null)
    setSubmitting(true)
    try {
      await login(email.trim(), password)
      navigate(redirectTo, { replace: true })
    } catch (err) {
      if (err.status === 429) {
        // The server says this account/IP is over its limit - follow it.
        const wait = err.retryAfter ?? LOCKOUT_MINUTES * 60
        setLockedUntil(Date.now() + wait * 1000)
        setNow(Date.now())
        setError(err.message || 'Too many sign-in attempts. Please wait and try again.')
      } else {
        const key = email.trim().toLowerCase()
        const count = (attempts.email === key ? attempts.count : 0) + 1
        setAttempts({ email: key, count })
        if (count >= MAX_ATTEMPTS) {
          setLockedUntil(Date.now() + LOCKOUT_MINUTES * 60 * 1000)
          setNow(Date.now())
          setError(`Too many failed attempts. Sign-in is locked for ${LOCKOUT_MINUTES} minutes.`)
        } else {
          setError(err.message || 'Sign in failed')
        }
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="auth auth--login">
      <AuthScene variant="login" />
      {locked ? (
        <LockedClock secondsLeft={secondsLeft} totalSeconds={LOCKOUT_MINUTES * 60} />
      ) : null}
      <motion.div
        className="auth__card"
        variants={cardIn}
        initial="hidden"
        animate="show"
        style={{ transformPerspective: 1000 }}
      >
        <motion.div className="auth__brand" {...fadeUp(0.05)}>
          <BrandMark size="lg" />
          <h1>Prescription Scanner</h1>
          <p>Sign in to digitise prescriptions and manage patient records.</p>
        </motion.div>

        <motion.form
          className="auth__form"
          onSubmit={handleSubmit}
          noValidate
          variants={formContainer}
          initial="hidden"
          animate="show"
        >
          <motion.label className="field" variants={fieldItem}>
            <span>Email</span>
            <input
              type="email"
              name="email"
              autoComplete="username"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="admin@prescriptionscanner.local"
              disabled={locked}
              required
            />
          </motion.label>

          <motion.label className="field" variants={fieldItem}>
            <span>Password</span>
            <PasswordInput
              name="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              disabled={locked}
              required
            />
          </motion.label>

          {error ? (
            <p className="form-error" role="alert">
              {error}
            </p>
          ) : null}

          {locked ? (
            <div className="lock-scene">
              <p className="lock-scene__note">
                Sign-in locked for {formatCountdown(secondsLeft)}. Try again after the timer ends.
              </p>
              <span className="sr-only" role="status">
                Sign-in is locked. Try again in {LOCKOUT_MINUTES} minutes.
              </span>
            </div>
          ) : attemptsLeft < MAX_ATTEMPTS ? (
            <p className="form-note">
              {attemptsLeft} attempt{attemptsLeft === 1 ? '' : 's'} left before sign-in is locked for{' '}
              {LOCKOUT_MINUTES} minutes.
            </p>
          ) : null}

          <motion.button
            type="submit"
            className="btn btn--primary btn--block"
            disabled={submitting || locked}
            variants={fieldItem}
            {...pressable}
          >
            {locked
              ? `Locked · ${formatCountdown(secondsLeft)}`
              : submitting
                ? 'Signing in…'
                : 'Sign in'}
          </motion.button>
        </motion.form>

        <p className="auth__hint">
          New here? <Link to="/register" className="btn btn--ghost btn--sm">Create an account</Link>
        </p>
        <p className="auth__forgot">
          <Link to="/forgot-password" className="btn btn--ghost btn--sm">Forgot password?</Link>
        </p>
      </motion.div>
      <Footer />
    </div>
  )
}
