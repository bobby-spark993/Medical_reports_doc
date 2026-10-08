export const EASE = [0.22, 1, 0.36, 1]

export const pageVariants = {
  initial: { opacity: 0, y: 10 },
  animate: { opacity: 1, y: 0 },
  exit: { opacity: 0, y: -8 },
}

export const pageTransition = { duration: 0.25, ease: EASE }

export const cardIn = {
  hidden: { opacity: 0, y: 30, scale: 0.97, rotateX: -6 },
  show: {
    opacity: 1,
    y: 0,
    scale: 1,
    rotateX: 0,
    transition: { duration: 0.55, ease: EASE },
  },
}

export const formContainer = {
  hidden: {},
  show: { transition: { staggerChildren: 0.06, delayChildren: 0.18 } },
}

export const fieldItem = {
  hidden: { opacity: 0, y: 12 },
  show: { opacity: 1, y: 0, transition: { duration: 0.4, ease: EASE } },
}

export function fadeUp(delay = 0) {
  return {
    initial: { opacity: 0, y: 16 },
    animate: { opacity: 1, y: 0 },
    transition: { duration: 0.5, ease: EASE, delay },
  }
}

export const pressable = {
  whileHover: { y: -2 },
  whileTap: { scale: 0.98 },
}
