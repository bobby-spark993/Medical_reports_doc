import { Navigate } from 'react-router-dom'
import { useAuth } from '../lib/auth'

/** Renders children only for the given role, otherwise bounces to the dashboard. */
export default function RequireRole({ role, children }) {
  const { user } = useAuth()
  if (user?.role !== role) return <Navigate to="/" replace />
  return children
}
