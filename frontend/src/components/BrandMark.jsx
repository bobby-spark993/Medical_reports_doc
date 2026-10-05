import { useEffect, useState } from 'react'
import { LOGO_SRC, logoState, subscribeLogo } from '../lib/logo'

export default function BrandMark({ size = 'sm' }) {
  const [, force] = useState(0)

  useEffect(() => subscribeLogo(() => force((v) => v + 1)), [])

  const base = size === 'lg' ? 'brand-mark brand-mark--lg' : 'brand-mark'

  return (
    <span className={base} aria-hidden="true">
      {logoState() ? (
        <img className="brand-mark__img" src={LOGO_SRC} alt="" />
      ) : (
        <svg
          viewBox="0 0 24 24"
          width={size === 'lg' ? 26 : 20}
          height={size === 'lg' ? 26 : 20}
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <path d="M12 3v18M3 12h18" />
        </svg>
      )}
    </span>
  )
}