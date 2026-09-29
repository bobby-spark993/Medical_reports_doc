import { useEffect, useState } from 'react'
import { del, get, post, put } from '../lib/api'
import { useAuth } from '../lib/auth'
import { titleCase } from '../lib/format'
import ErrorBanner from '../components/ErrorBanner'
import Loading from '../components/Loading'

const ROLES = ['admin', 'doctor', 'receptionist']
const EMPTY_FORM = { name: '', email: '', role: 'receptionist', password: '' }

export default function UsersPage() {
  const { user: me } = useAuth()

  const [users, setUsers] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [search, setSearch] = useState('')
  const [debounced, setDebounced] = useState('')

  const [form, setForm] = useState(EMPTY_FORM)
  const [editingId, setEditingId] = useState(null)
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState(null)

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(search.trim()), 300)
    return () => clearTimeout(timer)
  }, [search])

  function load() {
    setLoading(true)
    setError(null)
    const query = debounced ? `?search=${encodeURIComponent(debounced)}` : ''
    get(`/api/users${query}`)
      .then(setUsers)
      .catch(setError)
      .finally(() => setLoading(false))
  }

  useEffect(load, [debounced])

  function resetForm() {
    setEditingId(null)
    setForm(EMPTY_FORM)
    setFormError(null)
  }

  function startEdit(user) {
    setEditingId(user.id)
    setForm({ name: user.name, email: user.email, role: user.role, password: '' })
    setFormError(null)
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setSaving(true)
    setFormError(null)
    try {
      if (editingId) {
        await put(`/api/users/${editingId}`, form)
      } else {
        await post('/api/users', form)
      }
      resetForm()
      load()
    } catch (err) {
      setFormError(err)
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete(user) {
    if (!window.confirm(`Delete ${user.name} (${user.email})? This cannot be undone.`)) return
    setError(null)
    try {
      await del(`/api/users/${user.id}`)
      if (editingId === user.id) resetForm()
      load()
    } catch (err) {
      setError(err)
    }
  }

  return (
    <div className="page">
      <header className="page__head">
        <div>
          <h1>Users</h1>
          <p className="muted">Create staff accounts and control who can access the clinic records.</p>
        </div>
      </header>

      <ErrorBanner error={error} onRetry={load} />

      <div className="grid grid--detail">
        <section className="card">
          <h2 className="card__title">{editingId ? 'Edit user' : 'Add user'}</h2>

          <ErrorBanner error={formError} />

          <form onSubmit={handleSubmit} className="user-form">
            <label className="field">
              <span>Full name</span>
              <input
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                placeholder="e.g. Dr. Asha Verma"
                required
              />
            </label>

            <label className="field">
              <span>Email</span>
              <input
                type="email"
                value={form.email}
                onChange={(e) => setForm({ ...form, email: e.target.value })}
                placeholder="name@clinic.example"
                required
              />
            </label>

            <label className="field">
              <span>Role</span>
              <select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })}>
                {ROLES.map((role) => (
                  <option key={role} value={role}>
                    {titleCase(role)}
                  </option>
                ))}
              </select>
            </label>

            <label className="field">
              <span>{editingId ? 'New password (leave blank to keep)' : 'Password'}</span>
              <input
                type="password"
                value={form.password}
                onChange={(e) => setForm({ ...form, password: e.target.value })}
                placeholder={editingId ? '••••••••' : 'At least 8 characters'}
                autoComplete="new-password"
                required={!editingId}
              />
            </label>

            <div className="user-form__actions">
              <button type="submit" className="btn btn--primary" disabled={saving}>
                {saving ? 'Saving…' : editingId ? 'Save changes' : 'Create user'}
              </button>
              {editingId ? (
                <button type="button" className="btn btn--ghost" onClick={resetForm}>
                  Cancel
                </button>
              ) : null}
            </div>
          </form>
        </section>

        <section className="card card--table">
          <div className="toolbar toolbar--pad">
            <input
              className="input search"
              type="search"
              placeholder="Search by name or email…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>

          {loading && users.length === 0 ? (
            <Loading label="Loading users…" />
          ) : users.length === 0 ? (
            <div className="empty">
              <p>No users found.</p>
            </div>
          ) : (
            <table className="table">
              <thead>
                <tr>
                  <th>User</th>
                  <th>Role</th>
                  <th aria-label="Actions" />
                </tr>
              </thead>
              <tbody>
                {users.map((user) => (
                  <tr key={user.id}>
                    <td>
                      <strong>{user.name}</strong>
                      {user.id === me?.id ? <span className="pill pill--info pill--gap">You</span> : null}
                      <div className="muted small">{user.email}</div>
                    </td>
                    <td>
                      <span className="pill pill--muted">{titleCase(user.role)}</span>
                    </td>
                    <td className="num">
                      <div className="row-actions">
                        <button type="button" className="btn btn--ghost btn--sm" onClick={() => startEdit(user)}>
                          Edit
                        </button>
                        <button
                          type="button"
                          className="btn btn--danger btn--sm"
                          onClick={() => handleDelete(user)}
                          disabled={user.id === me?.id}
                          title={user.id === me?.id ? 'You cannot delete your own account' : 'Delete user'}
                        >
                          Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      </div>
    </div>
  )
}
