// Thin fetch wrapper around the Spring Boot API.
//
// Auth is a httpOnly cookie (`ps_token`) issued by POST /api/auth/login, so
// every request must be sent with `credentials: 'include'`. In development the
// Vite proxy forwards /api to :8080, which keeps the cookie same-origin.

const API_BASE = import.meta.env.VITE_API_BASE ?? ''

export { API_BASE }

export class ApiError extends Error {
  constructor(message, status, issues = []) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.issues = issues
  }
}

/** Absolute URL for a GET endpoint that must be opened directly (PDF, scan). */
export function apiUrl(path) {
  return `${API_BASE}${path}`
}

async function readBody(response) {
  const contentType = response.headers.get('content-type') ?? ''
  if (!contentType.includes('application/json')) return null
  try {
    return await response.json()
  } catch {
    return null
  }
}

export async function apiFetch(path, { body, headers, ...options } = {}) {
  const isFormData = body instanceof FormData

  const response = await fetch(apiUrl(path), {
    credentials: 'include',
    ...options,
    body,
    headers: {
      Accept: 'application/json',
      ...(isFormData || body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...headers,
    },
  })

  const data = await readBody(response)

  if (!response.ok) {
    const message = data?.error || `Request failed (${response.status})`
    throw new ApiError(message, response.status, data?.issues ?? [])
  }

  return data
}

export function get(path) {
  return apiFetch(path)
}

export function post(path, body) {
  return apiFetch(path, { method: 'POST', body: JSON.stringify(body) })
}

export function put(path, body) {
  return apiFetch(path, { method: 'PUT', body: JSON.stringify(body) })
}

export function del(path) {
  return apiFetch(path, { method: 'DELETE' })
}

export function upload(path, formData) {
  return apiFetch(path, { method: 'POST', body: formData })
}
