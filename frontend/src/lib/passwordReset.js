// Remembers which account a password reset was requested for, so the
// "reset password" step only needs the OTP and the new password. Stored per
// browser tab (sessionStorage) and cleared once the password is changed.
const RESET_EMAIL_KEY = 'prescription-scanner:reset-email'

export function saveResetEmail(email) {
  try {
    sessionStorage.setItem(RESET_EMAIL_KEY, email)
  } catch {
    // Private mode / storage disabled: the reset page falls back to a prompt.
  }
}

export function readResetEmail() {
  try {
    return sessionStorage.getItem(RESET_EMAIL_KEY) ?? ''
  } catch {
    return ''
  }
}

export function clearResetEmail() {
  try {
    sessionStorage.removeItem(RESET_EMAIL_KEY)
  } catch {
    // Nothing to clear if storage is unavailable.
  }
}
