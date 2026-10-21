const ECG_POINTS =
  '0,24 26,24 36,24 42,10 48,38 54,24 70,24 84,17 98,24 128,24 138,24 144,8 150,40 156,24 178,24 190,19 202,24 240,24'

/**
 * Pulse-rate loading indicator: a heart-monitor ECG line with a bright pulse
 * travelling along it. Used while pages are being scanned or saved.
 */
export default function PulseLoader({ label }) {
  return (
    <div className="pulse-loader" role="status" aria-live="polite">
      <svg
        className="pulse-loader__ecg"
        viewBox="0 0 240 48"
        preserveAspectRatio="none"
        aria-hidden="true"
      >
        <polyline className="pulse-loader__base" pathLength="100" points={ECG_POINTS} />
        <polyline className="pulse-loader__line" pathLength="100" points={ECG_POINTS} />
      </svg>
      {label ? <span className="pulse-loader__label">{label}</span> : null}
    </div>
  )
}
