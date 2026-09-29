export default function ErrorBanner({ error, onRetry }) {
  if (!error) return null

  return (
    <div className="banner banner--error" role="alert">
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
    </div>
  )
}
