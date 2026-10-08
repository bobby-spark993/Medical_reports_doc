import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { motion } from 'motion/react'
import { useAuth } from '../lib/auth'
import { cardIn, fadeUp, formContainer, fieldItem, pressable } from '../lib/motion'
import Loading from '../components/Loading'
import BrandMark from '../components/BrandMark'
import AuthScene from '../components/AuthScene'
import Footer from '../components/Footer'

export default function LoginPage() {
  const { user, loading, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  const redirectTo = location.state?.from?.pathname ?? '/'

  if (loading) return <Loading fullscreen label="Checking your session…" />
  if (user) return <Navigate to={redirectTo} replace />

  async function handleSubmit(event) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await login(email.trim(), password)
      navigate(redirectTo, { replace: true })
    } catch (err) {
      setError(err.message || 'Sign in failed')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="auth auth--login">
      <AuthScene variant="login" />
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
              required
            />
          </motion.label>

          <motion.label className="field" variants={fieldItem}>
            <span>Password</span>
            <input
              type="password"
              name="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
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
            {submitting ? 'Signing in…' : 'Sign in'}
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
