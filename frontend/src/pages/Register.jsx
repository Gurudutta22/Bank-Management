import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import toast from 'react-hot-toast'
import { Eye, EyeOff, KeyRound, Mail, MapPin, Phone, User } from 'lucide-react'
import AuthLayout from '../components/layout/AuthLayout'
import { Button, Input, cx } from '../components/ui'
import { useAuth } from '../context/AuthContext'

/** Mirrors the server's @Pattern rules so the user gets feedback before a round trip. */
const RULES = [
  { label: 'At least 8 characters', test: (v) => v.length >= 8 },
  { label: 'One uppercase letter', test: (v) => /[A-Z]/.test(v) },
  { label: 'One lowercase letter', test: (v) => /[a-z]/.test(v) },
  { label: 'One number', test: (v) => /\d/.test(v) },
]

export default function Register() {
  const { register } = useAuth()
  const navigate = useNavigate()

  const [form, setForm] = useState({
    fullName: '', email: '', phone: '', password: '', dateOfBirth: '', address: '',
  })
  const [showPassword, setShowPassword] = useState(false)
  const [errors, setErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)

  const passed = useMemo(() => RULES.map((rule) => rule.test(form.password)), [form.password])
  const strength = passed.filter(Boolean).length

  const update = (field) => (event) => {
    setForm((prev) => ({ ...prev, [field]: event.target.value }))
    setErrors((prev) => ({ ...prev, [field]: undefined }))
  }

  const validate = () => {
    const next = {}
    if (form.fullName.trim().length < 3) next.fullName = 'Enter your full name'
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) next.email = 'Enter a valid email address'
    if (!/^\d{10}$/.test(form.phone)) next.phone = 'Phone must be exactly 10 digits'
    if (strength < RULES.length) next.password = 'Password does not meet all requirements'
    if (form.dateOfBirth) {
      const age = (Date.now() - new Date(form.dateOfBirth).getTime()) / (365.25 * 24 * 3600 * 1000)
      if (age < 18) next.dateOfBirth = 'You must be at least 18 to open an account'
    }
    setErrors(next)
    return Object.keys(next).length === 0
  }

  const handleSubmit = async (event) => {
    event.preventDefault()
    if (!validate()) return

    setSubmitting(true)
    try {
      const user = await register({
        fullName: form.fullName.trim(),
        email: form.email.trim(),
        phone: form.phone,
        password: form.password,
        dateOfBirth: form.dateOfBirth || null,
        address: form.address.trim() || null,
      })
      toast.success(`Welcome to NovaBank, ${user.fullName.split(' ')[0]}`)
      navigate('/accounts', { replace: true })
    } catch (error) {
      toast.error(error.message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout
      title="Open an account"
      subtitle="A few details and your first account is ready in seconds."
      footer={
        <>
          Already registered?{' '}
          <Link to="/login" className="font-semibold text-[color:var(--accent-positive)] hover:underline">
            Sign in
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        <Input
          label="Full name" name="fullName" icon={User} placeholder="Priya Sharma"
          value={form.fullName} onChange={update('fullName')} error={errors.fullName}
        />

        <div className="grid gap-4 sm:grid-cols-2">
          <Input
            label="Email" name="email" type="email" autoComplete="email" icon={Mail}
            placeholder="you@example.com"
            value={form.email} onChange={update('email')} error={errors.email}
          />
          <Input
            label="Phone" name="phone" inputMode="numeric" maxLength={10} icon={Phone}
            placeholder="9800000000"
            value={form.phone}
            onChange={(e) =>
              // Strip non-digits as the user types so the field can never hold invalid input.
              update('phone')({ target: { value: e.target.value.replace(/\D/g, '') } })
            }
            error={errors.phone}
          />
        </div>

        <div className="relative">
          <Input
            label="Password" name="password" type={showPassword ? 'text' : 'password'}
            autoComplete="new-password" icon={KeyRound} placeholder="Create a strong password"
            value={form.password} onChange={update('password')} error={errors.password}
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

        {form.password && (
          <div className="surface-raised rounded-xl p-3.5">
            <div className="mb-2.5 flex gap-1.5" aria-hidden="true">
              {RULES.map((rule, index) => (
                <span
                  key={rule.label}
                  className={cx(
                    'h-1.5 flex-1 rounded-full transition-colors',
                    index < strength
                      ? strength <= 2
                        ? 'bg-[color:var(--meter-weak)]'
                        : strength === 3
                          ? 'bg-[color:var(--meter-medium)]'
                          : 'bg-[color:var(--meter-strong)]'
                      : 'bg-[color:var(--surface-sunken)]',
                  )}
                />
              ))}
            </div>
            <ul className="grid gap-1 sm:grid-cols-2">
              {RULES.map((rule, index) => (
                <li
                  key={rule.label}
                  className={cx('flex items-center gap-1.5 text-xs', passed[index] ? 'text-[color:var(--accent-positive)]' : 'text-muted')}
                >
                  <span className={cx('h-1.5 w-1.5 rounded-full', passed[index] ? 'bg-[color:var(--accent-positive)]' : 'bg-current')} />
                  {rule.label}
                </li>
              ))}
            </ul>
          </div>
        )}

        <div className="grid gap-4 sm:grid-cols-2">
          <Input
            label="Date of birth" name="dateOfBirth" type="date"
            max={new Date().toISOString().slice(0, 10)}
            value={form.dateOfBirth} onChange={update('dateOfBirth')} error={errors.dateOfBirth}
          />
          <Input
            label="Address" name="address" icon={MapPin} placeholder="City, State"
            value={form.address} onChange={update('address')}
          />
        </div>

        <Button type="submit" size="lg" loading={submitting} className="w-full">
          {submitting ? 'Creating your account' : 'Create account'}
        </Button>

        <p className="text-center text-xs text-muted">
          By continuing you agree to NovaBank&apos;s demo terms. No real financial data is collected.
        </p>
      </form>
    </AuthLayout>
  )
}
