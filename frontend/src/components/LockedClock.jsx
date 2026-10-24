import { motion } from 'motion/react'

/**
 * The character that shows up when sign-in is locked: someone standing in the
 * scene, waiting it out with a watch on their wrist.
 *
 * It is built as a CSS 3D rig (perspective + preserve-3d, every limb on its own
 * plane) so the head genuinely turns, the arms sit in front of the torso and
 * the watch catches the light. The amber ring on the dial empties as the lock
 * counts down.
 */
export default function LockedClock({ secondsLeft = 0, totalSeconds = 900 }) {
  const remaining = totalSeconds > 0 ? Math.max(0, Math.min(1, secondsLeft / totalSeconds)) : 0

  return (
    <motion.div
      className="lock-figure"
      style={{ '--lock-progress': remaining }}
      initial={{ opacity: 0, x: -30, y: 24, scale: 0.92 }}
      animate={{ opacity: 1, x: 0, y: 0, scale: 1 }}
      transition={{ type: 'spring', stiffness: 190, damping: 17 }}
      aria-hidden="true"
    >
      <div className="lock-figure__rig">
        <div className="lock-figure__shadow" />

        <div className="lock-figure__leg lock-figure__leg--back">
          <i className="lock-figure__shoe" />
        </div>
        <div className="lock-figure__leg lock-figure__leg--front">
          <i className="lock-figure__shoe" />
        </div>

        <div className="lock-figure__torso">
          <i className="lock-figure__collar" />
          <i className="lock-figure__pocket" />
        </div>

        <div className="lock-figure__arm lock-figure__arm--far">
          <i className="lock-figure__forearm lock-figure__forearm--far" />
          <i className="lock-figure__hand lock-figure__hand--far" />
        </div>

        <div className="lock-figure__arm lock-figure__arm--watch">
          <i className="lock-figure__forearm lock-figure__forearm--watch" />
          <i className="lock-figure__hand lock-figure__hand--watch" />
          <i className="lock-figure__watch">
            <i className="lock-figure__watch-ring" />
            <i className="lock-figure__watch-hand" />
          </i>
        </div>

        <div className="lock-figure__neck" />

        <div className="lock-figure__head">
          <i className="lock-figure__hair" />
          <i className="lock-figure__brow lock-figure__brow--left" />
          <i className="lock-figure__brow lock-figure__brow--right" />
          <i className="lock-figure__eye lock-figure__eye--left" />
          <i className="lock-figure__eye lock-figure__eye--right" />
          <i className="lock-figure__nose" />
          <i className="lock-figure__mouth" />
        </div>

        <div className="lock-figure__dots">
          <i />
          <i />
          <i />
        </div>
      </div>
    </motion.div>
  )
}
