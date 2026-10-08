import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { motion } from 'motion/react'
import { useAuth } from '../lib/auth'
import { cardIn, fadeUp, formContainer, fieldItem, pressable } from '../lib/motion'
import Loading from '../components/Loading'
import BrandMark from '../components/BrandMark'
import AuthScene from '../components/AuthScene'
import Footer from '../components/Footer'

export default function ResetPasswordPage() {
  const { user, loading, resetPassword } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [otp, setOtp] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  const redirectTo = location.state?.from?.pathname ?? '/'

  if (loading) return <Loading fullscreen label="Checking your session…" />
  if (user) return <Navigate to={redirectTo} replace />

  async function handleSubmit(event) {
    event.preventDefault()
    setError(null)

    if (password.length < 8) {
      setError('Password must be at least 8 characters.')
      return
    }
    if (password !== confirm) {
      setError('Passwords do not match.')
      return
    }

    setSubmitting(true)
    try {
      await resetPassword(email.trim(), otp.trim(), password)
      navigate('/login', { replace: true })
    } catch (err) {
      setError(err.message || 'Reset failed')
      setSubmitting(false)
    }
  }

  return (
    <div className="auth">
      <AuthScene variant="reset" />
      <motion.div
        className="auth__card"
        variants={cardIn}
        initial="hidden"
        animate="show"
        style={{ transformPerspective: 1000 }}
      >
        <motion.div className="auth__brand" {...fadeUp(0.05)}>
          <BrandMark size="lg" />
          <h1>Set a new password</h1>
          <p>Enter the OTP sent to your inbox and choose a new password.</p>
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
              placeholder="you@clinic.com"
              required
            />
          </motion.label>

          <motion.label className="field" variants={fieldItem}>
            <span>One-time code (OTP)</span>
            <input
              type="text"
              name="otp"
              inputMode="numeric"
              autoComplete="one-time-code"
              value={otp}
              onChange={(e) => setOtp(e.target.value)}
              placeholder="6-digit code"
              maxLength={6}
              required
            />
          </motion.label>

          <motion.label className="field" variants={fieldItem}>
            <span>New password</span>
            <input
              type="password"
              name="password"
              autoComplete="new-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="At least 8 characters"
              required
            />
          </motion.label>

          <motion.label className="field" variants={fieldItem}>
            <span>Confirm new password</span>
            <input
              type="password"
              name="confirm"
              autoComplete="new-password"
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              placeholder="••••••••"
              required
            />
          </motion.label>

          {error ? (
            <p className="form-error" role="alert">
              {error}
            </p>
          ) : null}

          <motion.button
            type="submit"
            className="btn btn--primary btn--block"
            disabled={submitting}
            variants={fieldItem}
            {...pressable}
          >
            {submitting ? 'Resetting…' : 'Reset password'}
          </motion.button>
        </motion.form>

        <p className="auth__hint">
          <Link to="/forgot-password" className="btn btn--ghost btn--sm">Request a new code</Link>{' '}
          <Link to="/login" className="btn btn--ghost btn--sm">Back to sign in</Link>
        </p>
      </motion.div>
      <Footer />
    </div>
  )
}