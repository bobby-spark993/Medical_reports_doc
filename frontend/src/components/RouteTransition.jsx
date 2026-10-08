import { useLocation, useOutlet } from 'react-router-dom'
import { AnimatePresence, motion } from 'motion/react'
import { pageVariants, pageTransition } from '../lib/motion'

export default function RouteTransition({ fullHeight = false }) {
  const location = useLocation()
  // Capture the outlet element so the exiting page keeps its own content
  // while it animates out (rather than re-rendering with the next route).
  const element = useOutlet()

  return (
    <AnimatePresence mode="wait" initial={false}>
      <motion.div
        key={location.pathname}
        className="route-transition"
        variants={pageVariants}
        initial="initial"
        animate="animate"
        exit="exit"
        transition={pageTransition}
        style={fullHeight ? { minHeight: '100%' } : undefined}
      >
        {element}
      </motion.div>
    </AnimatePresence>
  )
}
