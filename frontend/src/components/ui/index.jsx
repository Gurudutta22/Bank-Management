import { forwardRef, useEffect } from 'react'
import { AlertCircle, Loader2, X } from 'lucide-react'

/** Tiny classname joiner - avoids pulling in `clsx` for what is four lines of code. */
export const cx = (...parts) => parts.filter(Boolean).join(' ')

/* ------------------------------------------------------------------ Button */

/*
 * Hover is an ELEVATION change here, not a colour change - that is the whole point of the
 * pass. `shadow-emerald-500/20` was a hand-typed duplicate of what --shadow-glow already
 * encodes, and brightness-110 read as a flash rather than a lift, so it drops to 1.04-1.06.
 * The danger fill moves off rose-500, where white measured 3.67:1 (an AA failure on the
 * confirm button of every destructive flow), to 5.69:1.
 */
const BUTTON_VARIANTS = {
  primary:
    'brand-gradient text-white shadow-[var(--shadow-glow)] hover:brightness-[1.04] hover:shadow-[var(--elev-2),var(--shadow-glow)]',
  secondary:
    'surface-raised text-[color:var(--text-primary)] hover:bg-[color:var(--surface-hover)] hover:shadow-[var(--elev-2)]',
  ghost:
    'bg-transparent text-secondary hover:bg-[color:var(--surface-hover)] hover:text-[color:var(--text-primary)]',
  danger:
    'bg-[color:var(--accent-danger-solid)] text-white shadow-[var(--elev-1)] hover:brightness-[1.06] hover:shadow-[var(--elev-2)]',
  outline:
    'bg-transparent border border-[color:var(--surface-border-strong)] text-[color:var(--text-primary)] hover:bg-[color:var(--surface-hover)] hover:border-[color:var(--focus-ring)]',
}

const BUTTON_SIZES = {
  sm: 'h-9 px-3.5 text-sm gap-1.5',
  md: 'h-11 px-5 text-sm gap-2',
  lg: 'h-12 px-6 text-base gap-2',
  icon: 'h-10 w-10 justify-center',
}

export const Button = forwardRef(function Button(
  { variant = 'primary', size = 'md', loading = false, className, children, disabled, ...props },
  ref,
) {
  return (
    <button
      ref={ref}
      // Disabling while loading is what actually stops a double-submit from reaching the API.
      disabled={disabled || loading}
      className={cx(
        // Explicit property list rather than `transition-all`: `all` also animates transform,
        // which fights `active:scale-[0.98]` below. box-shadow is in the list so the hover
        // lift eases instead of snapping.
        'inline-flex items-center justify-center rounded-xl font-semibold',
        'transition-[background-color,border-color,color,box-shadow,filter,opacity] duration-200',
        'disabled:cursor-not-allowed disabled:opacity-55 disabled:shadow-none active:scale-[0.98]',
        BUTTON_VARIANTS[variant],
        BUTTON_SIZES[size],
        className,
      )}
      {...props}
    >
      {loading && <Loader2 size={16} className="animate-spin" aria-hidden="true" />}
      {children}
    </button>
  )
})

/* ------------------------------------------------------------------- Card */

export function Card({ className, children, ...props }) {
  return (
    <div className={cx('surface p-5 sm:p-6', className)} {...props}>
      {children}
    </div>
  )
}

export function CardHeader({ title, subtitle, action, icon: Icon }) {
  return (
    <div className="mb-5 flex items-start justify-between gap-4">
      <div className="flex items-start gap-3">
        {Icon && (
          <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-[color:var(--accent-positive-chip)] text-[color:var(--accent-positive)]">
            <Icon size={18} />
          </span>
        )}
        <div>
          <h2 className="text-base font-semibold tracking-tight">{title}</h2>
          {subtitle && <p className="mt-0.5 text-sm text-secondary">{subtitle}</p>}
        </div>
      </div>
      {action}
    </div>
  )
}

/* ------------------------------------------------------------------ Input */

export const Input = forwardRef(function Input(
  { label, error, hint, icon: Icon, className, id, ...props },
  ref,
) {
  const inputId = id || props.name

  return (
    <div className="w-full">
      {label && (
        <label htmlFor={inputId} className="mb-1.5 block text-sm font-medium text-secondary">
          {label}
        </label>
      )}
      <div className="relative">
        {Icon && (
          <Icon
            size={17}
            className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[color:var(--text-muted)]"
            aria-hidden="true"
          />
        )}
        <input
          ref={ref}
          id={inputId}
          // aria-invalid lets a screen reader announce the field as erroneous, not just show red.
          aria-invalid={Boolean(error)}
          aria-describedby={error ? `${inputId}-error` : undefined}
          className={cx(
            'h-11 w-full rounded-xl border bg-[color:var(--surface-sunken)] px-3.5 text-sm',
            'text-[color:var(--text-primary)] placeholder:text-[color:var(--text-muted)]',
            'outline-none focus:border-[color:var(--focus-ring)] focus:ring-2 focus:ring-[color:var(--focus-halo)]',
            'disabled:cursor-not-allowed disabled:opacity-60',
            Icon && 'pl-10',
            // An input is identified by its border, so this is the one place a boundary must
            // stay strong: --surface-border-strong clears 3:1 on every surface an input can
            // land on (light 3.16-3.86, dark 3.40-4.69) while the decorative hairline goes soft.
            error ? 'border-[color:var(--accent-danger)]' : 'border-[color:var(--surface-border-strong)]',
            className,
          )}
          {...props}
        />
      </div>
      {error && (
        // rose-400 on white measured 2.69:1 - the least readable text in the app was the text
        // that most needed reading. --accent-danger is 6.14:1 light / 6.79:1 dark.
        <p
          id={`${inputId}-error`}
          className="mt-1.5 flex items-center gap-1.5 text-xs text-[color:var(--accent-danger)]"
        >
          <AlertCircle size={13} /> {error}
        </p>
      )}
      {hint && !error && <p className="mt-1.5 text-xs text-muted">{hint}</p>}
    </div>
  )
})

export const Select = forwardRef(function Select({ label, error, children, className, id, ...props }, ref) {
  const selectId = id || props.name
  return (
    <div className="w-full">
      {label && (
        <label htmlFor={selectId} className="mb-1.5 block text-sm font-medium text-secondary">
          {label}
        </label>
      )}
      <select
        ref={ref}
        id={selectId}
        className={cx(
          'h-11 w-full appearance-none rounded-xl border bg-[color:var(--surface-sunken)] px-3.5 text-sm',
          'text-[color:var(--text-primary)] outline-none',
          'focus:border-[color:var(--focus-ring)] focus:ring-2 focus:ring-[color:var(--focus-halo)]',
          error ? 'border-[color:var(--accent-danger)]' : 'border-[color:var(--surface-border-strong)]',
          className,
        )}
        {...props}
      >
        {children}
      </select>
      {error && <p className="mt-1.5 text-xs text-[color:var(--accent-danger)]">{error}</p>}
    </div>
  )
})

/* ------------------------------------------------------------------ Badge */

/*
 * The one tinted-chip vocabulary for the whole app: status badges, dashboard stat tiles and
 * admin metric tiles all read it, so a token change reaches every one of them at once. It used
 * to be three near-verbatim copies (here, Dashboard.jsx and AdminDashboard.jsx) that no token
 * could touch.
 *
 * Each chip is a named surface mixed with its accent rather than `accent/12` alpha over
 * whatever happens to be behind it, so a badge no longer changes appearance when its row is
 * hovered. Accent-on-its-own-chip measures 4.82-5.30:1 in light and 5.57-7.40:1 in dark -
 * the case the old alpha syntax quietly hid, where emerald read 2.54:1 and amber 2.15:1.
 */
export const TONE_CHIP = {
  positive: 'bg-[color:var(--accent-positive-chip)] text-[color:var(--accent-positive)]',
  negative: 'bg-[color:var(--accent-negative-chip)] text-[color:var(--accent-negative)]',
  warning: 'bg-[color:var(--accent-warning-chip)] text-[color:var(--accent-warning)]',
  info: 'bg-[color:var(--accent-info-chip)] text-[color:var(--accent-info)]',
  danger: 'bg-[color:var(--accent-danger-chip)] text-[color:var(--accent-danger)]',
  neutral: 'bg-[color:var(--surface-hover)] text-[color:var(--text-secondary)]',
}

const BADGE_TONES = {
  success: `${TONE_CHIP.positive} ring-[color:var(--accent-positive-ring)]`,
  warning: `${TONE_CHIP.warning} ring-[color:var(--accent-warning-ring)]`,
  danger: `${TONE_CHIP.danger} ring-[color:var(--accent-danger-ring)]`,
  info: `${TONE_CHIP.info} ring-[color:var(--accent-info-ring)]`,
  neutral: `${TONE_CHIP.neutral} ring-[color:var(--surface-border)]`,
}

export function Badge({ tone = 'neutral', children, className }) {
  return (
    <span
      className={cx(
        'inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-semibold ring-1 ring-inset',
        BADGE_TONES[tone] || BADGE_TONES.neutral,
        className,
      )}
    >
      {children}
    </span>
  )
}

/** Maps a domain status onto a colour, so the mapping lives in one place. */
export function StatusBadge({ status }) {
  const tone =
    { ACTIVE: 'success', ENABLED: 'success', SUCCESS: 'success', FROZEN: 'warning', CLOSED: 'danger', FAILED: 'danger', DISABLED: 'danger', REVERSED: 'info' }[status] ||
    'neutral'
  return <Badge tone={tone}>{status}</Badge>
}

/* ------------------------------------------------------------------ Modal */

export function Modal({ open, onClose, title, subtitle, children, size = 'md' }) {
  // Escape-to-close and background scroll-lock are what separate a real dialog from a floating div.
  useEffect(() => {
    if (!open) return undefined
    const onKey = (e) => e.key === 'Escape' && onClose?.()
    document.addEventListener('keydown', onKey)
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = previousOverflow
    }
  }, [open, onClose])

  if (!open) return null

  const widths = { sm: 'max-w-md', md: 'max-w-lg', lg: 'max-w-2xl' }

  return (
    <div className="fixed inset-0 z-50 grid place-items-center p-4">
      {/* bg-slate-950/60 resolved to a mid-grey slab over a near-white page - the most abrupt
          tonal jump in light mode - and to almost nothing over the dark one. One --scrim token,
          two mode-appropriate values, with the blur and --elev-3 doing the rest of the work. */}
      <div
        className="absolute inset-0"
        style={{ background: 'var(--scrim)', backdropFilter: 'blur(10px)' }}
        onClick={onClose}
        aria-hidden="true"
      />
      {/* The dialog sits on the real overlay tier, so it is above cards by construction rather
          than by a stock pure-black shadow that barely registers in dark mode. */}
      <div
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className={cx(
          'surface-overlay relative w-full rounded-2xl p-6 animate-fade-up',
          widths[size] || widths.md,
        )}
      >
        <div className="mb-5 flex items-start justify-between gap-4">
          <div>
            <h3 className="text-lg font-semibold tracking-tight">{title}</h3>
            {subtitle && <p className="mt-1 text-sm text-secondary">{subtitle}</p>}
          </div>
          <button
            onClick={onClose}
            aria-label="Close dialog"
            className="rounded-lg p-1.5 text-muted hover:bg-[color:var(--surface-hover)] hover:text-[color:var(--text-primary)]"
          >
            <X size={18} />
          </button>
        </div>
        {children}
      </div>
    </div>
  )
}

/* ----------------------------------------------------------- Loading / empty */

export function Spinner({ size = 20, className }) {
  return <Loader2 size={size} className={cx('animate-spin text-[color:var(--accent-positive)]', className)} />
}

export function PageLoader({ label = 'Loading' }) {
  return (
    <div className="grid min-h-[60vh] place-items-center">
      <div className="flex flex-col items-center gap-3">
        <Spinner size={30} />
        <p className="text-sm text-secondary">{label}…</p>
      </div>
    </div>
  )
}

export function Skeleton({ className }) {
  return <div className={cx('skeleton rounded-lg', className)} />
}

export function EmptyState({ icon: Icon, title, description, action }) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 px-6 py-14 text-center">
      {Icon && (
        <span className="grid h-14 w-14 place-items-center rounded-2xl bg-[color:var(--surface-sunken)] text-[color:var(--text-muted)]">
          <Icon size={24} />
        </span>
      )}
      <div>
        <p className="font-semibold">{title}</p>
        {description && <p className="mt-1 max-w-sm text-sm text-secondary">{description}</p>}
      </div>
      {action}
    </div>
  )
}
