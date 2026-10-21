import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { motion } from 'motion/react'
import { useAuth } from '../lib/auth'
import { cardIn, fadeUp, formContainer, fieldItem, pressable } from '../lib/motion'
import Loading from '../components/Loading'
import BrandMark from '../components/BrandMark'
import AuthScene from '../components/AuthScene'
import Footer from '../components/Footer'
import PasswordInput from '../components/PasswordInput'
import { readResetEmail, clearResetEmail } from '../lib/passwordReset'

export default function ResetPasswordPage() {
  const { user, loading, resetPassword } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email] = useState(() => readResetEmail())
  const [otp, setOtp] = useState('')
  const [password, setPassword] = useState('')
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

    setSubmitting(true)
    try {
      await resetPassword(email, otp.trim(), password)
      clearResetEmail()
      navigate('/login', { replace: true })
    } catch (err) {
      setError(err.message || 'Reset failed')
      setSubmitting(false)
    }
  }

  return (
    <div className="auth auth--reset">
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
          <p>
            {email
              ? <>Enter the code sent to <strong>{email}</strong> and choose a new password.</>
              : 'Enter the OTP sent to your inbox and choose a new password.'}
          </p>
        </motion.div>

        {email ? (
          <motion.form
            className="auth__form"
            onSubmit={handleSubmit}
            noValidate
            variants={formContainer}
            initial="hidden"
            animate="show"
          >
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
              <PasswordInput
                name="password"
                autoComplete="new-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="At least 8 characters"
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
        ) : (
          <motion.div {...fadeUp(0.12)}>
            <div className="banner banner--info">
              We don't know which account to reset. Request a new code to continue.
            </div>
            <div className="user-form__actions">
              <Link to="/forgot-password" className="btn btn--primary btn--block">
                Request a new code
              </Link>
            </div>
          </motion.div>
        )}

        <p className="auth__hint">
          <Link to="/forgot-password" className="btn btn--ghost btn--sm">Request a new code</Link>{' '}
          <Link to="/login" className="btn btn--ghost btn--sm">Back to sign in</Link>
        </p>
      </motion.div>
      <Footer />
    </div>
  )
}
