import { useEffect, useMemo, useRef } from 'react'

const TAU = Math.PI * 2

// Precompute a 3D double-helix: each rung sits at a height and is rotated
// around the vertical axis, which is what makes the ladder appear to twist.
function makeHelix({ rungs = 32, spacing = 23, turns = 2.4 } = {}) {
  const span = spacing * (rungs - 1)
  return Array.from({ length: rungs }, (_, i) => {
    const t = i / (rungs - 1)
    const angle = t * TAU * turns
    return {
      y: (t - 0.5) * span,
      angle,
      depth: (Math.sin(angle) + 1) / 2,
    }
  })
}

// Golden-angle distribution of nodes over a sphere, for the "molecule" scene.
function makeAtoms(count, radius) {
  const golden = Math.PI * (3 - Math.sqrt(5))
  return Array.from({ length: count }, (_, i) => {
    const yy = 1 - (i / (count - 1)) * 2
    const ring = Math.sqrt(1 - yy * yy)
    const theta = golden * i
    return {
      x: Math.cos(theta) * ring * radius,
      y: yy * radius,
      z: Math.sin(theta) * ring * radius,
      depth: (Math.sin(theta) + 1) / 2,
    }
  })
}

const CROSSES = [
  { x: '-40vw', y: '-34vh', z: '-120px', s: 1.15, dur: '13s', delay: '0s' },
  { x: '38vw', y: '-30vh', z: '-220px', s: 0.85, dur: '16s', delay: '-4s' },
  { x: '-34vw', y: '32vh', z: '-180px', s: 0.95, dur: '15s', delay: '-7s' },
  { x: '40vw', y: '34vh', z: '-90px', s: 1.1, dur: '12s', delay: '-2s' },
  { x: '10vw', y: '-40vh', z: '40px', s: 0.7, dur: '18s', delay: '-9s' },
]

const PILLS = [
  { x: '-30vw', y: '-22vh', z: '120px', s: 1, rot: '32deg', dur: '11s', delay: '-1s' },
  { x: '28vw', y: '-18vh', z: '180px', s: 0.8, rot: '-24deg', dur: '14s', delay: '-5s' },
  { x: '-26vw', y: '20vh', z: '160px', s: 0.9, rot: '58deg', dur: '12s', delay: '-3s' },
  { x: '30vw', y: '24vh', z: '90px', s: 1.05, rot: '-48deg', dur: '16s', delay: '-8s' },
  { x: '0vw', y: '31vh', z: '230px', s: 0.7, rot: '18deg', dur: '13s', delay: '-6s' },
]

function Cross({ x, y, z, s, dur, delay }) {
  return (
    <span
      className="cross-3d"
      style={{ '--x': x, '--y': y, '--z': z, '--s': s, '--dur': dur, '--delay': delay }}
    >
      <i className="cross-3d__v" />
      <i className="cross-3d__h" />
    </span>
  )
}

function Pill({ x, y, z, s, rot, dur, delay }) {
  return (
    <span
      className="capsule"
      style={{ '--x': x, '--y': y, '--z': z, '--s': s, '--rot': rot, '--dur': dur, '--delay': delay }}
    />
  )
}

function Helix({ rungs }) {
  return (
    <div className="helix">
      {rungs.map((rung, i) => (
        <div
          key={i}
          className="rung"
          style={{
            '--y': `${rung.y}px`,
            '--angle': `${-rung.angle}rad`,
            '--depth': rung.depth.toFixed(3),
          }}
        >
          <span className="rung__bond" />
          <span className="rung__node rung__node--a" />
          <span className="rung__node rung__node--b" />
        </div>
      ))}
    </div>
  )
}

function Molecule({ atoms }) {
  return (
    <div className="molecule">
      <span className="molecule__nucleus" />
      {atoms.map((atom, i) => (
        <span
          key={i}
          className="atom"
          style={{
            '--x': `${atom.x}px`,
            '--y': `${atom.y}px`,
            '--z': `${atom.z}px`,
            '--depth': atom.depth.toFixed(3),
          }}
        />
      ))}
      {[
        { size: 240, tilt: 74, yaw: 0, dur: '11s' },
        { size: 320, tilt: 66, yaw: 58, dur: '15s' },
        { size: 400, tilt: 80, yaw: -40, dur: '19s' },
      ].map((ring) => (
        <span
          key={ring.size}
          className="orbit"
          style={{
            '--size': `${ring.size}px`,
            '--tilt': `${ring.tilt}deg`,
            '--yaw': `${ring.yaw}deg`,
            '--dur': ring.dur,
          }}
        >
          <span className="orbit__spin">
            <span className="orbit__ring" />
            <span className="orbit__electron" />
          </span>
        </span>
      ))}
    </div>
  )
}

function Heartbeat() {
  return (
    <div className="heartbeat">
      <span className="pulse-ring" style={{ '--delay': '0s' }} />
      <span className="pulse-ring" style={{ '--delay': '1.1s' }} />
      <span className="pulse-ring" style={{ '--delay': '2.2s' }} />
      <svg className="heart" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
        <path d="M12 21s-6.72-4.35-9.33-8.06C.9 10.5 2.1 6.9 5.2 6.06c1.9-.51 3.8.24 4.87 1.75L12 9.8l1.93-1.99C15 6.3 16.9 5.55 18.8 6.06c3.1.84 4.3 4.44 2.53 6.88C18.72 16.65 12 21 12 21z" />
      </svg>
      <svg className="mail" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <rect x="2" y="4.5" width="20" height="15" rx="2.5" />
        <path d="m3 7 9 5.5L21 7" />
      </svg>
    </div>
  )
}

function OtpPad() {
  return (
    <div className="otp">
      {Array.from({ length: 6 }, (_, i) => (
        <span key={i} className="otp-tile" style={{ '--i': i }}>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" aria-hidden="true">
            <path d="M12 4v16M4 12h16" />
          </svg>
        </span>
      ))}
    </div>
  )
}

function Ecg() {
  return (
    <svg className="ecg" viewBox="0 0 1200 120" preserveAspectRatio="none" aria-hidden="true">
      <polyline
        className="ecg__line"
        pathLength="100"
        points="0,64 150,64 178,64 190,26 210,102 230,64 268,64 306,64 326,40 346,88 366,64 520,64 548,64 560,18 582,106 604,64 660,64 902,64 922,44 942,84 962,64 1200,64"
      />
    </svg>
  )
}

function Centerpiece({ variant, rungs, atoms }) {
  if (variant === 'register') return <Molecule atoms={atoms} />
  if (variant === 'forgot') return <Heartbeat />
  if (variant === 'reset') return <OtpPad />
  return <Helix rungs={rungs} />
}

export default function AuthScene({ variant = 'login' }) {
  const sceneRef = useRef(null)
  const rungs = useMemo(() => makeHelix(), [])
  const atoms = useMemo(() => makeAtoms(9, 118), [])

  useEffect(() => {
    const el = sceneRef.current
    if (!el) return
    if (typeof window.matchMedia === 'function' &&
        window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      return
    }
    let raf = 0
    function handleMove(event) {
      const mx = event.clientX / window.innerWidth - 0.5
      const my = event.clientY / window.innerHeight - 0.5
      cancelAnimationFrame(raf)
      raf = requestAnimationFrame(() => {
        el.style.setProperty('--mx', mx.toFixed(3))
        el.style.setProperty('--my', my.toFixed(3))
      })
    }
    window.addEventListener('pointermove', handleMove)
    return () => {
      window.removeEventListener('pointermove', handleMove)
      cancelAnimationFrame(raf)
    }
  }, [])

  return (
    <div ref={sceneRef} className={`scene scene--${variant}`} aria-hidden="true">
      <div className="scene__parallax">
        <div className="scene__layer scene__layer--back">
          {CROSSES.map((cross, i) => (
            <Cross key={i} {...cross} />
          ))}
        </div>

        <div className="scene__layer scene__layer--mid">
          <div className="scene__stage">
            <Centerpiece variant={variant} rungs={rungs} atoms={atoms} />
          </div>
        </div>

        <div className="scene__layer scene__layer--front">
          {PILLS.map((pill, i) => (
            <Pill key={i} {...pill} />
          ))}
        </div>
      </div>
      <Ecg />
    </div>
  )
}
