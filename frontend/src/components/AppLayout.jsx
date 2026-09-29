import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../lib/auth'
import { initials, titleCase } from '../lib/format'

const NAV = [
  { to: '/', label: 'Dashboard', end: true, icon: 'M3 11.5 12 4l9 7.5M5.5 10v9h13v-9' },
  { to: '/upload', label: 'New scan', icon: 'M12 5v14M5 12h14' },
  { to: '/patients', label: 'Patients', icon: 'M16 19v-1.5A3.5 3.5 0 0 0 12.5 14h-5A3.5 3.5 0 0 0 4 17.5V19M10 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7Z' },
]

const ADMIN_NAV = {
  to: '/users',
  label: 'Users',
  icon: 'M17 20v-1.5A3.5 3.5 0 0 0 13.5 15h-4A3.5 3.5 0 0 0 6 18.5V20M11.5 12a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7ZM18 8.5a2.5 2.5 0 0 1 0 5M21 20v-1.2a3 3 0 0 0-2.2-2.9',
}

function Icon({ path }) {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d={path} />
    </svg>
  )
}

export default function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const nav = user?.role === 'admin' ? [...NAV, ADMIN_NAV] : NAV

  async function handleLogout() {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="sidebar__brand">
          <span className="brand-mark" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M12 3v18M3 12h18" />
            </svg>
          </span>
          <div>
            <strong>Rx Scanner</strong>
            <small>Prescription digitisation</small>
          </div>
        </div>

        <nav className="sidebar__nav">
          {nav.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) => `nav-item${isActive ? ' nav-item--active' : ''}`}
            >
              <Icon path={item.icon} />
              <span>{item.label}</span>
            </NavLink>
          ))}
        </nav>

        <div className="sidebar__footer">
          <div className="user-chip">
            <span className="avatar">{initials(user?.name)}</span>
            <div className="user-chip__meta">
              <strong>{user?.name}</strong>
              <small>{titleCase(user?.role)}</small>
            </div>
          </div>
          <button type="button" className="btn btn--ghost btn--sm btn--block" onClick={handleLogout}>
            Sign out
          </button>
        </div>
      </aside>

      <main className="content">
        <Outlet />
      </main>
    </div>
  )
}
