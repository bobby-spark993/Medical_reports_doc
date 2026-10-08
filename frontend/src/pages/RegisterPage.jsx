import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { motion } from 'motion/react'
import { useAuth } from '../lib/auth'
import { cardIn, fadeUp, formContainer, fieldItem, pressable } from '../lib/motion'
import Loading from '../components/Loading'
import BrandMark from '../components/BrandMark'
import AuthScene from '../components/AuthScene'
import Footer from '../components/Footer'

const ROLES = [
  { value: 'receptionist', label: 'Receptionist' },
  { value: 'doctor', label: 'Doctor' },
  { value: 'admin', label: 'Admin' },
]

export default function RegisterPage() {
  const { user, loading, register } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [role, setRole] = useState('receptionist')
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
      await register({ name: name.trim(), email: email.trim(), password, role })
      navigate(redirectTo, { replace: true })
    } catch (err) {
      setError(err.message || 'Sign up failed')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="auth auth--register">
      <AuthScene variant="register" />
      <motion.div
        className="auth__card"
        variants={cardIn}
        initial="hidden"
        animate="show"
        style={{ transformPerspective: 1000 }}
      >
        <motion.div className="auth__brand" {...fadeUp(0.05)}>
          <BrandMark size="lg" />
          <h1>Create an account</h1>
          <p>Register as clinic staff to digitise prescriptions and manage patient records.</p>
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
            <span>Full name</span>
            <input
              type="text"
              name="name"
              autoComplete="name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Dr. Ramesh Kumar"
              required
            />
          </motion.label>

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
            <span>Role</span>
            <select value={role} onChange={(e) => setRole(e.target.value)}>
              {ROLES.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </select>
          </motion.label>

          <motion.label className="field" variants={fieldItem}>
            <span>Password</span>
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
            <span>Confirm password</span>
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
            {submitting ? 'Creating account…' : 'Create account'}
          </motion.button>
        </motion.form>

        <p className="auth__hint">
          Already have an account? <Link to="/login" className="btn btn--ghost btn--sm">Sign in</Link>
        </p>
      </motion.div>
      <Footer />
    </div>
  )
}
