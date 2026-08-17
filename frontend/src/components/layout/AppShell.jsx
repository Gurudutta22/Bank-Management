import { useEffect, useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import {
  ArrowLeftRight,
  ClipboardList,
  LayoutDashboard,
  LogOut,
  Menu,
  Moon,
  Receipt,
  Settings,
  Shield,
  Sun,
  Users,
  Wallet,
  X,
} from 'lucide-react'
import { useAuth } from '../../context/AuthContext'
import { useTheme } from '../../context/ThemeContext'
import { initialsOf } from '../../utils/format'
import { Badge, cx } from '../ui'

const CUSTOMER_LINKS = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/accounts', label: 'Accounts', icon: Wallet },
  { to: '/transfer', label: 'Transfer', icon: ArrowLeftRight },
  { to: '/transactions', label: 'Transactions', icon: Receipt },
  { to: '/beneficiaries', label: 'Payees', icon: Users },
  { to: '/profile', label: 'Profile', icon: Settings },
]

const ADMIN_LINKS = [
  { to: '/admin', label: 'Overview', icon: Shield, end: true },
  { to: '/admin/users', label: 'Customers', icon: Users },
  { to: '/admin/accounts', label: 'Accounts', icon: Wallet },
  { to: '/admin/audit', label: 'Audit trail', icon: ClipboardList },
]

export default function AppShell() {
  const { user, signOut, isAdmin } = useAuth()
  const { isDark, toggle } = useTheme()
  const navigate = useNavigate()
  const location = useLocation()
  const [mobileOpen, setMobileOpen] = useState(false)

  // Close the drawer whenever the route changes, otherwise it stays open over the new page.
  useEffect(() => setMobileOpen(false), [location.pathname])

  const handleSignOut = async () => {
    await signOut()
    navigate('/login', { replace: true })
  }

  return (
    <div className="flex min-h-screen">
      {/* Backdrop for the mobile drawer */}
      {mobileOpen && (
        <div
          className="fixed inset-0 z-30 lg:hidden"
          style={{ background: 'var(--scrim)', backdropFilter: 'blur(10px)' }}
          onClick={() => setMobileOpen(false)}
          aria-hidden="true"
        />
      )}

      <aside
        className={cx(
          'glass fixed inset-y-0 left-0 z-40 flex w-[264px] flex-col border-r transition-transform duration-300',
          'lg:translate-x-0',
          mobileOpen ? 'translate-x-0' : '-translate-x-full',
        )}
      >
        <div className="flex h-16 items-center justify-between px-5">
          <Link to="/dashboard" className="flex items-center gap-2.5">
            <span className="brand-gradient grid h-9 w-9 place-items-center rounded-xl text-white shadow-[var(--shadow-glow)]">
              <Wallet size={18} />
            </span>
            <span className="text-lg font-bold tracking-tight">
              Nova<span className="brand-text-gradient">Bank</span>
            </span>
          </Link>
          <button
            onClick={() => setMobileOpen(false)}
            className="rounded-lg p-1.5 text-muted hover:text-[color:var(--text-primary)] lg:hidden"
            aria-label="Close navigation"
          >
            <X size={18} />
          </button>
        </div>

        <nav className="flex-1 space-y-1 overflow-y-auto px-3 py-4">
          <SectionLabel>Banking</SectionLabel>
          {CUSTOMER_LINKS.map((link) => (
            <SidebarLink key={link.to} {...link} />
          ))}

          {isAdmin && (
            <>
              <SectionLabel className="pt-5">Administration</SectionLabel>
              {ADMIN_LINKS.map((link) => (
                <SidebarLink key={link.to} {...link} />
              ))}
            </>
          )}
        </nav>

        <div className="border-t p-3">
          <div className="flex items-center gap-3 rounded-xl px-2 py-2">
            <span className="brand-gradient grid h-9 w-9 shrink-0 place-items-center rounded-full text-sm font-bold text-white">
              {initialsOf(user?.fullName)}
            </span>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-semibold">{user?.fullName}</p>
              <p className="truncate text-xs text-muted">{user?.email}</p>
            </div>
          </div>
          <button
            onClick={handleSignOut}
            className="mt-1 flex w-full items-center gap-2.5 rounded-xl px-3 py-2.5 text-sm font-medium text-secondary transition-colors hover:bg-[color:var(--accent-danger-chip)] hover:text-[color:var(--accent-danger)]"
          >
            <LogOut size={17} /> Sign out
          </button>
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col lg:pl-[264px]">
        <header className="glass sticky top-0 z-20 flex h-16 items-center gap-3 border-b px-4 sm:px-6">
          <button
            onClick={() => setMobileOpen(true)}
            className="rounded-lg p-2 text-secondary transition-colors hover:bg-[color:var(--surface-hover)] lg:hidden"
            aria-label="Open navigation"
          >
            <Menu size={20} />
          </button>

          <div className="min-w-0 flex-1">
            <p className="truncate text-sm text-secondary">
              Welcome back, <span className="font-semibold text-[color:var(--text-primary)]">{user?.fullName?.split(' ')[0]}</span>
            </p>
          </div>

          {isAdmin && (
            <Badge tone="info" className="hidden sm:inline-flex">
              <Shield size={12} /> Administrator
            </Badge>
          )}

          <button
            onClick={toggle}
            aria-label={isDark ? 'Switch to light theme' : 'Switch to dark theme'}
            className="rounded-xl border border-[color:var(--surface-border-strong)] p-2.5 text-secondary hover:bg-[color:var(--surface-hover)] hover:text-[color:var(--text-primary)]"
          >
            {isDark ? <Sun size={17} /> : <Moon size={17} />}
          </button>
        </header>

        <main className="flex-1 px-4 py-6 sm:px-6 lg:px-8">
          <Outlet />
        </main>

        <footer className="px-6 py-5 text-center text-xs text-muted">
          NovaBank · Built with Spring Boot &amp; React · Demo environment — no real money moves here
        </footer>
      </div>
    </div>
  )
}

function SectionLabel({ children, className }) {
  return (
    <p className={cx('px-3 pb-1.5 text-[11px] font-semibold uppercase tracking-wider text-muted', className)}>
      {children}
    </p>
  )
}

function SidebarLink({ to, label, icon: Icon, end }) {
  return (
    <NavLink
      to={to}
      end={end}
      className={({ isActive }) =>
        cx(
          'group relative flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition-all',
          isActive
            // brand-500 as text measured 2.54:1 on the light card. The chip + --accent-positive
            // pair reads 4.82:1 light / 7.40:1 dark, and the left marker still carries the state
            // without relying on colour.
            ? 'bg-[color:var(--accent-positive-chip)] text-[color:var(--accent-positive)]'
            : 'text-secondary hover:bg-[color:var(--surface-hover)] hover:text-[color:var(--text-primary)]',
        )
      }
    >
      {({ isActive }) => (
        <>
          {/* Active marker: a colour change alone is easy to miss at a glance. */}
          {isActive && (
            <span className="absolute left-0 top-1/2 h-5 w-1 -translate-y-1/2 rounded-r-full bg-[color:var(--accent-positive)]" />
          )}
          <Icon size={18} />
          {label}
        </>
      )}
    </NavLink>
  )
}
