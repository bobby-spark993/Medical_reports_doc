// Tracks whether a clinic logo has been uploaded, shared by every BrandMark.
// The endpoint is public, so this works on the login page before auth.

import { apiUrl } from './api'

export const LOGO_SRC = apiUrl('/api/settings/logo')

let state = null // null = unknown, true = has logo, false = no logo
const listeners = new Set()

export function logoState() {
  return state
}

export function subscribeLogo(fn) {
  listeners.add(fn)
  return () => listeners.delete(fn)
}

/** Re-check the backend (call after uploading/removing a logo). */
export function refreshLogo() {
  state = null
  listeners.forEach((fn) => fn())
  probe()
}

/** Point the browser tab icon at the given image (default favicon otherwise). */
function setFavicon(href) {
  if (typeof document === 'undefined') return
  let link = document.querySelector("link[rel~='icon']")
  if (!link) {
    link = document.createElement('link')
    link.rel = 'icon'
    document.head.appendChild(link)
  }
  link.removeAttribute('type')
  link.href = href
}

function probe() {
  if (state !== null) return
  const img = new Image()
  img.onload = () => {
    state = true
    setFavicon(LOGO_SRC)
    listeners.forEach((fn) => fn())
  }
  img.onerror = () => {
    state = false
    setFavicon('/favicon.svg')
    listeners.forEach((fn) => fn())
  }
  img.src = LOGO_SRC
}

probe()