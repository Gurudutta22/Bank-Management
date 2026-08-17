import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import toast from 'react-hot-toast'
import { Eye, EyeOff, KeyRound, Mail } from 'lucide-react'
import AuthLayout from '../components/layout/AuthLayout'
import { Button, Input } from '../components/ui'
import { useAuth } from '../context/AuthContext'

const DEMO_ACCOUNTS = [
  { label: 'Customer', email: 'priya@novabank.io', password: 'Customer@123' },
  { label: 'Administrator', email: 'admin@novabank.io', password: 'Admin@123' },
]

export default function Login() {
  const { signIn } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const [form, setForm] = useState({ email: '', password: '' })
  const [showPassword, setShowPassword] = useState(false)
  const [errors, setErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)

  const update = (field) => (event) => {
    setForm((prev) => ({ ...prev, [field]: event.target.value }))
    setErrors((prev) => ({ ...prev, [field]: undefined }))
  }

  const validate = () => {
    const next = {}
    if (!form.email.trim()) next.email = 'Email is required'
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) next.email = 'Enter a valid email address'
    if (!form.password) next.password = 'Password is required'
    setErrors(next)
    return Object.keys(next).length === 0
  }

  const handleSubmit = async (event) => {
    event.preventDefault()
    if (!validate()) return

    setSubmitting(true)
    try {
      const user = await signIn({ email: form.email.trim(), password: form.password })
      toast.success(`Welcome back, ${user.fullName.split(' ')[0]}`)
      // Send admins to the back office, everyone else to their dashboard - unless they were
      // deep-linked somewhere, in which case honour that.
      const fallback = user.role === 'ADMIN' ? '/admin' : '/dashboard'
      navigate(location.state?.from || fallback, { replace: true })
    } catch (error) {
      toast.error(error.message)
      setErrors({ password: ' ' })
    } finally {
      setSubmitting(false)
    }
  }

  const useDemo = (account) => {
    setForm({ email: account.email, password: account.password })
    setErrors({})
  }

  return (
    <AuthLayout
      title="Sign in"
      subtitle="Access your accounts, move money and download statements."
      footer={
        <>
          New to NovaBank?{' '}
          <Link to="/register" className="font-semibold text-[color:var(--accent-positive)] hover:underline">
            Open an account
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        <Input
          label="Email address"
          name="email"
          type="email"
          autoComplete="email"
          icon={Mail}
          placeholder="you@example.com"
          value={form.email}
          onChange={update('email')}
          error={errors.email}
        />

        <div className="relative">
          <Input
            label="Password"
            name="password"
            type={showPassword ? 'text' : 'password'}
            autoComplete="current-password"
            icon={KeyRound}
            placeholder="••••••••"
            value={form.password}
            onChange={update('password')}
            error={errors.password?.trim() ? errors.password : undefined}
          />
          <button
            type="button"
            onClick={() => setShowPassword((v) => !v)}
            aria-label={showPassword ? 'Hide password' : 'Show password'}
            className="absolute right-3 top-[38px] rounded-md p-1 text-muted transition-colors hover:text-[color:var(--text-primary)]"
          >
            {showPassword ? <EyeOff size={17} /> : <Eye size={17} />}
          </button>
        </div>

        <Button type="submit" size="lg" loading={submitting} className="w-full">
          {submitting ? 'Signing in' : 'Sign in'}
        </Button>
      </form>

      <div className="mt-8">
        <div className="mb-3 flex items-center gap-3">
          <span className="h-px flex-1 bg-[color:var(--surface-border)]" />
          <span className="text-xs font-medium uppercase tracking-wider text-muted">Demo logins</span>
          <span className="h-px flex-1 bg-[color:var(--surface-border)]" />
        </div>

        <div className="grid gap-2 sm:grid-cols-2">
          {DEMO_ACCOUNTS.map((account) => (
            <button
              key={account.email}
              type="button"
              onClick={() => useDemo(account)}
              className="surface-raised rounded-xl px-3 py-2.5 text-left transition-colors hover:border-[color:var(--surface-border-strong)]"
            >
              <p className="text-sm font-semibold">{account.label}</p>
              <p className="truncate text-xs text-muted">{account.email}</p>
            </button>
          ))}
        </div>
      </div>
    </AuthLayout>
  )
}
