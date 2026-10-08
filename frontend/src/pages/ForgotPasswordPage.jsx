import { useState } from 'react'
import { Link, Navigate, useLocation } from 'react-router-dom'
import { motion } from 'motion/react'
import { useAuth } from '../lib/auth'
import { cardIn, fadeUp, formContainer, fieldItem, pressable } from '../lib/motion'
import Loading from '../components/Loading'
import BrandMark from '../components/BrandMark'
import AuthScene from '../components/AuthScene'
import Footer from '../components/Footer'

export default function ForgotPasswordPage() {
  const { user, loading, requestPasswordReset } = useAuth()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [sent, setSent] = useState(false)
  const [error, setError] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  const redirectTo = location.state?.from?.pathname ?? '/'

  if (loading) return <Loading fullscreen label="Checking your session…" />
  if (user) return <Navigate to={redirectTo} replace />

  async function handleSubmit(event) {
    event.preventDefault()
    setError(null)

    const value = email.trim()
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
      setError('Enter a valid email address.')
      return
    }

    setSubmitting(true)
    try {
      await requestPasswordReset(value)
      setSent(true)
    } catch (err) {
      setError(err.message || 'Request failed')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="auth auth--forgot">
      <AuthScene variant="forgot" />
      <motion.div
        className="auth__card"
        variants={cardIn}
        initial="hidden"
        animate="show"
        style={{ transformPerspective: 1000 }}
      >
        <motion.div className="auth__brand" {...fadeUp(0.05)}>
          <BrandMark size="lg" />
          <h1>Reset your password</h1>
          <p>Enter your account email and we will issue a one-time code.</p>
        </motion.div>

        {sent ? (
          <motion.div {...fadeUp(0.12)}>
            <div className="banner banner--info">
              If that email is registered, a 6-digit OTP has been sent to its
              inbox. Use it on the next screen to set a new password.
            </div>
            <div className="user-form__actions">
              <Link to="/reset-password" className="btn btn--primary btn--block">
                Enter OTP →
              </Link>
            </div>
            <p className="auth__hint">
              <Link to="/login" className="btn btn--ghost btn--sm">Back to sign in</Link>
            </p>
          </motion.div>
        ) : (
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
              {submitting ? 'Requesting…' : 'Request OTP'}
            </motion.button>
          </motion.form>
        )}

        <p className="auth__hint">
          <Link to="/login" className="btn btn--ghost btn--sm">Back to sign in</Link>
        </p>
      </motion.div>
      <Footer />
    </div>
  )
}