const dateFmt = new Intl.DateTimeFormat('en-IN', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
})

const dateTimeFmt = new Intl.DateTimeFormat('en-IN', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
})

const dayFmt = new Intl.DateTimeFormat('en-IN', { weekday: 'long' })

const timeFmt = new Intl.DateTimeFormat('en-IN', {
  hour: '2-digit',
  minute: '2-digit',
})

function toDate(value) {
  if (value === null || value === undefined || value === '') return null
  const date = value instanceof Date ? value : new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

export function formatDate(value) {
  const date = toDate(value)
  return date ? dateFmt.format(date) : '—'
}

export function formatDateTime(value) {
  const date = toDate(value)
  return date ? dateTimeFmt.format(date) : '—'
}

export function formatDay(value) {
  const date = toDate(value)
  return date ? dayFmt.format(date) : '—'
}

export function formatTime(value) {
  const date = toDate(value)
  return date ? timeFmt.format(date) : '—'
}

export function formatValue(value) {
  if (value === null || value === undefined || value === '') return '—'
  return value
}

// Converts a scanned date ("04 Apr 2026", "04/04/2026", ISO, ...) into the
// yyyy-mm-dd value an <input type="date"> requires. Returns '' when unknown.
export function toDateInputValue(value) {
  if (value === null || value === undefined || value === '') return ''
  const text = String(value).trim()

  const iso = text.match(/^(\d{4})-(\d{2})-(\d{2})/)
  if (iso) return `${iso[1]}-${iso[2]}-${iso[3]}`

  const dmy = text.match(/^(\d{1,2})[/.-](\d{1,2})[/.-](\d{2,4})$/)
  if (dmy) {
    const day = dmy[1].padStart(2, '0')
    const month = dmy[2].padStart(2, '0')
    const year = (dmy[3].length === 2 ? `20${dmy[3]}` : dmy[3]).padStart(4, '0')
    return `${year}-${month}-${day}`
  }

  const date = toDate(text)
  if (!date) return ''
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function initials(name) {
  if (!name) return '?'
  return name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('')
}

export function titleCase(value) {
  if (!value) return ''
  return value.charAt(0).toUpperCase() + value.slice(1).toLowerCase()
}
