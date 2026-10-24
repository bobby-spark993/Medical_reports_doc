import { useState } from 'react'

function EyeIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M2.06 12.35a1 1 0 0 1 0-.7 10.75 10.75 0 0 1 19.88 0 1 1 0 0 1 0 .7 10.75 10.75 0 0 1-19.88 0" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  )
}

function EyeOffIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M10.73 5.08a10.75 10.75 0 0 1 11.21 6.58 1 1 0 0 1 0 .69 10.75 10.75 0 0 1-1.44 2.49" />
      <path d="M14.08 14.16a3 3 0 0 1-4.24-4.24" />
      <path d="M17.48 17.5a10.75 10.75 0 0 1-15.42-5.15 1 1 0 0 1 0-.7 10.75 10.75 0 0 1 4.45-5.14" />
      <path d="m2 2 20 20" />
    </svg>
  )
}

/**
 * Password input with a show/hide eye toggle.
 * Drop-in replacement for an <input>: pass the same props (name, value,
 * onChange, autoComplete, placeholder, required, ...).
 */
export default function PasswordInput({
  value,
  onChange,
  className,
  ...props
}) {
  const [visible, setVisible] = useState(false)
  const label = visible ? 'Hide password' : 'Show password'

  return (
    <span className="field__control">
      <input
        {...props}
        type={visible ? 'text' : 'password'}
        className={className}
        value={value}
        onChange={onChange}
      />
      <button
        type="button"
        className="field__toggle"
        onClick={() => setVisible((current) => !current)}
        onMouseDown={(event) => event.preventDefault()}
        disabled={props.disabled}
        aria-label={label}
        aria-pressed={visible}
        title={label}
      >
        {visible ? <EyeOffIcon /> : <EyeIcon />}
      </button>
    </span>
  )
}
