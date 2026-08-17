import { useState } from 'react'
import toast from 'react-hot-toast'
import { BadgeCheck, KeyRound, Mail, MapPin, Phone, ShieldAlert, User } from 'lucide-react'
import { profileApi } from '../api/endpoints'
import { useAuth } from '../context/AuthContext'
import { Badge, Button, Card, CardHeader, Input } from '../components/ui'
import { formatDate, initialsOf } from '../utils/format'

export default function Profile() {
  const { user, updateUser, signOut } = useAuth()

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <header>
        <h1 className="text-2xl font-bold tracking-tight">Profile &amp; security</h1>
        <p className="mt-1 text-sm text-secondary">Manage your details and password.</p>
      </header>

      <Card className="flex flex-wrap items-center gap-5">
        <span className="brand-gradient grid h-20 w-20 shrink-0 place-items-center rounded-2xl text-2xl font-bold text-white shadow-[var(--shadow-glow)]">
          {initialsOf(user?.fullName)}
        </span>
        <div className="min-w-0 flex-1">
          <h2 className="text-xl font-bold tracking-tight">{user?.fullName}</h2>
          <p className="truncate text-sm text-secondary">{user?.email}</p>
          <div className="mt-2.5 flex flex-wrap gap-2">
            <Badge tone={user?.role === 'ADMIN' ? 'info' : 'neutral'}>{user?.role}</Badge>
            {user?.kycVerified ? (
              <Badge tone="success"><BadgeCheck size={12} /> KYC verified</Badge>
            ) : (
              <Badge tone="warning"><ShieldAlert size={12} /> KYC pending</Badge>
            )}
            <Badge tone={user?.enabled ? 'success' : 'danger'}>
              {user?.enabled ? 'Active' : 'Disabled'}
            </Badge>
          </div>
        </div>
        <div className="text-right text-xs text-muted">
          <p>Customer since</p>
          <p className="font-medium text-[color:var(--text-primary)]">{formatDate(user?.createdAt)}</p>
        </div>
      </Card>

      <div className="grid gap-5 lg:grid-cols-2">
        <ProfileForm user={user} onUpdated={updateUser} />
        <PasswordForm onChanged={signOut} />
      </div>
    </div>
  )
}

function ProfileForm({ user, onUpdated }) {
  const [form, setForm] = useState({
    fullName: user?.fullName || '',
    phone: user?.phone || '',
    address: user?.address || '',
  })
  const [errors, setErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)

  const update = (field) => (event) => {
    setForm((prev) => ({ ...prev, [field]: event.target.value }))
    setErrors((prev) => ({ ...prev, [field]: undefined }))
  }

  const submit = async (event) => {
    event.preventDefault()
    const next = {}
    if (form.fullName.trim().length < 3) next.fullName = 'Enter your full name'
    if (!/^\d{10}$/.test(form.phone)) next.phone = 'Phone must be exactly 10 digits'
    setErrors(next)
    if (Object.keys(next).length) return

    setSubmitting(true)
    try {
      const updated = await profileApi.update(form)
      onUpdated(updated)
      toast.success('Profile updated')
    } catch (error) {
      toast.error(error.message)
      setErrors({ phone: error.message })
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Card>
      <CardHeader title="Personal details" subtitle="Your email cannot be changed." icon={User} />
      <form onSubmit={submit} className="space-y-4" noValidate>
        <Input
          label="Full name" name="fullName" icon={User}
          value={form.fullName} onChange={update('fullName')} error={errors.fullName}
        />
        <Input label="Email" name="email" icon={Mail} value={user?.email || ''} disabled />
        <Input
          label="Phone" name="phone" icon={Phone} inputMode="numeric" maxLength={10}
          value={form.phone}
          onChange={(e) => update('phone')({ target: { value: e.target.value.replace(/\D/g, '') } })}
          error={errors.phone}
        />
        <Input
          label="Address" name="address" icon={MapPin} maxLength={255}
          value={form.address} onChange={update('address')}
        />
        <Button type="submit" loading={submitting} className="w-full">Save changes</Button>
      </form>
    </Card>
  )
}

function PasswordForm({ onChanged }) {
  const [form, setForm] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' })
  const [errors, setErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)

  const update = (field) => (event) => {
    setForm((prev) => ({ ...prev, [field]: event.target.value }))
    setErrors((prev) => ({ ...prev, [field]: undefined }))
  }

  const submit = async (event) => {
    event.preventDefault()
    const next = {}
    if (!form.currentPassword) next.currentPassword = 'Enter your current password'
    if (!/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/.test(form.newPassword)) {
      next.newPassword = 'At least 8 characters, with upper, lower and a digit'
    }
    if (form.newPassword !== form.confirmPassword) next.confirmPassword = 'Passwords do not match'
    setErrors(next)
    if (Object.keys(next).length) return

    setSubmitting(true)
    try {
      await profileApi.changePassword({
        currentPassword: form.currentPassword,
        newPassword: form.newPassword,
      })
      // The server revokes every refresh token on a password change, so the only correct thing
      // to do next is sign out and let the user log in again with the new credentials.
      toast.success('Password changed. Please sign in again.')
      setTimeout(onChanged, 1200)
    } catch (error) {
      setErrors({ currentPassword: error.message })
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Card>
      <CardHeader
        title="Change password"
        subtitle="All other sessions are signed out when you change it."
        icon={KeyRound}
      />
      <form onSubmit={submit} className="space-y-4" noValidate>
        <Input
          label="Current password" name="currentPassword" type="password" autoComplete="current-password"
          value={form.currentPassword} onChange={update('currentPassword')} error={errors.currentPassword}
        />
        <Input
          label="New password" name="newPassword" type="password" autoComplete="new-password"
          value={form.newPassword} onChange={update('newPassword')} error={errors.newPassword}
        />
        <Input
          label="Confirm new password" name="confirmPassword" type="password" autoComplete="new-password"
          value={form.confirmPassword} onChange={update('confirmPassword')} error={errors.confirmPassword}
        />
        <Button type="submit" loading={submitting} className="w-full">Update password</Button>
      </form>
    </Card>
  )
}
