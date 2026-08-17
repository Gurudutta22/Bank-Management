/** Presentation helpers. Kept pure and framework-free so they are trivially testable. */

const currencyFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

const compactFormatter = new Intl.NumberFormat('en-IN', {
  notation: 'compact',
  maximumFractionDigits: 1,
})

/** Full precision, for anything the user might reconcile against a real statement. */
export function formatCurrency(value) {
  const number = Number(value ?? 0)
  return currencyFormatter.format(Number.isFinite(number) ? number : 0)
}

/** Short form for chart axes and stat tiles, where precision costs more than it gives. */
export function formatCompact(value) {
  const number = Number(value ?? 0)
  return `₹${compactFormatter.format(Number.isFinite(number) ? number : 0)}`
}

export function formatNumber(value) {
  return new Intl.NumberFormat('en-IN').format(Number(value ?? 0))
}

export function formatDate(iso, withTime = false) {
  if (!iso) return '—'
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return '—'

  return date.toLocaleString('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    ...(withTime ? { hour: '2-digit', minute: '2-digit' } : {}),
  })
}

/** "3 hours ago" style label for activity feeds. */
export function formatRelative(iso) {
  if (!iso) return '—'
  const then = new Date(iso).getTime()
  const seconds = Math.round((Date.now() - then) / 1000)

  if (seconds < 60) return 'just now'
  const units = [
    ['minute', 60],
    ['hour', 60],
    ['day', 24],
    ['month', 30],
    ['year', 12],
  ]

  let value = seconds / 60
  let unit = 'minute'
  for (let i = 0; i < units.length - 1; i += 1) {
    if (Math.abs(value) < units[i + 1][1]) {
      unit = units[i][0]
      break
    }
    value /= units[i + 1][1]
    unit = units[i + 1][0]
  }

  const rounded = Math.round(value)
  return `${rounded} ${unit}${rounded === 1 ? '' : 's'} ago`
}

/** Groups a 12-digit account number for readability: 9001 0010 0101. */
export function formatAccountNumber(accountNumber) {
  if (!accountNumber) return '—'
  return accountNumber.replace(/(.{4})/g, '$1 ').trim()
}

export function initialsOf(name) {
  if (!name) return '?'
  return name
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0].toUpperCase())
    .join('')
}

/** Today, as YYYY-MM-DD, which is what <input type="date"> and the API both expect. */
export function isoDate(date = new Date()) {
  return date.toISOString().slice(0, 10)
}

export function daysAgo(days) {
  const date = new Date()
  date.setDate(date.getDate() - days)
  return isoDate(date)
}

/**
 * A stable per-request key so a double-submitted form cannot move money twice.
 * Falls back to a timestamp+random string on browsers without crypto.randomUUID.
 */
export function newIdempotencyKey() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return `key-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}
