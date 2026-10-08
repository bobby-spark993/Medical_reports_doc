import { motion } from 'motion/react'

export default function ErrorBanner({ error, onRetry }) {
  if (!error) return null

  return (
    <motion.div
      className="banner banner--error"
      role="alert"
      initial={{ opacity: 0, y: -8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.25, ease: [0.22, 1, 0.36, 1] }}
    >
      <div>
        <strong>{error.message}</strong>
        {error.issues?.length ? (
          <ul className="banner__issues">
            {error.issues.map((issue) => (
              <li key={`${issue.path}-${issue.message}`}>
                {issue.path ? <code>{issue.path}</code> : null} {issue.message}
              </li>
            ))}
          </ul>
        ) : null}
      </div>
      {onRetry ? (
        <button type="button" className="btn btn--ghost btn--sm" onClick={onRetry}>
          Retry
        </button>
      ) : null}
    </motion.div>
  )
}
