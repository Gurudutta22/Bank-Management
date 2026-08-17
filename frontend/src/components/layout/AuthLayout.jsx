import { ShieldCheck, TrendingUp, Wallet, Zap } from 'lucide-react'

const HIGHLIGHTS = [
  { icon: ShieldCheck, title: 'Bank-grade security', body: 'BCrypt hashing, JWT sessions and a full audit trail on every action.' },
  { icon: Zap, title: 'Atomic transfers', body: 'Row-level locking guarantees money is never created or lost in a race.' },
  { icon: TrendingUp, title: 'Real-time insight', body: 'Live balances, categorised spending and downloadable statements.' },
]

/**
 * Split-screen wrapper for the login and register screens.
 *
 * The marketing panel is hidden below `lg` rather than stacked - on a phone it would push the
 * form itself below the fold, which is the one thing a sign-in page must never do.
 */
export default function AuthLayout({ title, subtitle, children, footer }) {
  return (
    <div className="grid min-h-screen lg:grid-cols-[1.05fr_1fr]">
      <aside className="relative hidden overflow-hidden lg:block">
        {/* Its own token, not .brand-gradient under a darkening layer. A flat 35% scrim over a
            gradient flattens the very depth the gradient exists to provide; this sweep is built
            deep enough (L .400-.460, chroma held at .085) to carry white copy unaided. */}
        <div className="auth-gradient absolute inset-0" />

        {/* Decorative light blooms; aria-hidden so screen readers skip the noise. */}
        <div
          aria-hidden="true"
          className="absolute -left-24 top-1/4 h-96 w-96 rounded-full bg-white/12 blur-3xl"
        />
        <div
          aria-hidden="true"
          className="absolute -right-16 bottom-0 h-80 w-80 rounded-full bg-[color:var(--color-iris-400)]/22 blur-3xl"
        />

        <div className="relative flex h-full flex-col justify-between p-12 text-white">
          <div className="flex items-center gap-3">
            <span className="grid h-11 w-11 place-items-center rounded-2xl bg-white/15 backdrop-blur">
              <Wallet size={22} />
            </span>
            <span className="text-2xl font-bold tracking-tight">NovaBank</span>
          </div>

          <div className="max-w-md">
            <h2 className="text-4xl font-bold leading-tight tracking-tight">
              Banking that keeps every rupee accounted for.
            </h2>
            <p className="mt-4 text-white/85">
              A full-stack demonstration of secure, concurrent, double-entry banking — built on
              Spring Boot and React.
            </p>

            <ul className="mt-10 space-y-5">
              {HIGHLIGHTS.map(({ icon: Icon, title: heading, body }) => (
                <li key={heading} className="flex gap-4">
                  <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-white/15 backdrop-blur">
                    <Icon size={18} />
                  </span>
                  <div>
                    <p className="font-semibold">{heading}</p>
                    <p className="text-sm text-white/80">{body}</p>
                  </div>
                </li>
              ))}
            </ul>
          </div>

          <p className="text-sm text-white/75">
            Demo environment · No real accounts, no real money.
          </p>
        </div>
      </aside>

      <main className="flex items-center justify-center px-5 py-10 sm:px-10">
        <div className="w-full max-w-md animate-fade-up">
          <div className="mb-8 flex items-center gap-2.5 lg:hidden">
            <span className="brand-gradient grid h-10 w-10 place-items-center rounded-xl text-white">
              <Wallet size={19} />
            </span>
            <span className="text-xl font-bold tracking-tight">
              Nova<span className="brand-text-gradient">Bank</span>
            </span>
          </div>

          <h1 className="text-3xl font-bold tracking-tight">{title}</h1>
          <p className="mt-2 text-sm text-secondary">{subtitle}</p>

          <div className="mt-8">{children}</div>

          {footer && <div className="mt-6 text-center text-sm text-secondary">{footer}</div>}
        </div>
      </main>
    </div>
  )
}
