import { useEffect, useState } from 'react'
import toast from 'react-hot-toast'
import { BadgeCheck, ChevronLeft, ChevronRight, Search, ShieldAlert, Users } from 'lucide-react'
import { adminApi } from '../../api/endpoints'
import { useApi, useDebounced } from '../../hooks/useApi'
import {
  Badge, Button, Card, EmptyState, Input, Modal, Select, Skeleton, cx,
} from '../../components/ui'
import { formatDate, initialsOf } from '../../utils/format'

export default function AdminUsers() {
  const [search, setSearch] = useState('')
  const [role, setRole] = useState('')
  const [page, setPage] = useState(0)
  const [target, setTarget] = useState(null)
  const [reason, setReason] = useState('')
  const [saving, setSaving] = useState(false)

  const debouncedSearch = useDebounced(search, 400)

  const { data, loading, refetch } = useApi(
    () => adminApi.users({ search: debouncedSearch || undefined, role: role || undefined, page, size: 15 }),
    [debouncedSearch, role, page],
  )

  useEffect(() => setPage(0), [debouncedSearch, role])

  const toggleStatus = async () => {
    setSaving(true)
    try {
      await adminApi.setUserStatus(target.id, {
        status: target.enabled ? 'DISABLED' : 'ENABLED',
        reason: reason || undefined,
      })
      toast.success(`${target.fullName} ${target.enabled ? 'disabled' : 'enabled'}`)
      setTarget(null)
      setReason('')
      refetch({ quiet: true })
    } catch (error) {
      toast.error(error.message)
    } finally {
      setSaving(false)
    }
  }

  const toggleKyc = async (user) => {
    try {
      await adminApi.setKyc(user.id, !user.kycVerified)
      toast.success(`KYC ${user.kycVerified ? 'revoked for' : 'verified for'} ${user.fullName}`)
      refetch({ quiet: true })
    } catch (error) {
      toast.error(error.message)
    }
  }

  return (
    <div className="mx-auto max-w-6xl space-y-5">
      <header>
        <h1 className="text-2xl font-bold tracking-tight">Customers</h1>
        <p className="mt-1 text-sm text-secondary">
          {data ? `${data.totalElements} user${data.totalElements === 1 ? '' : 's'}` : 'Loading…'}
        </p>
      </header>

      <Card className="grid gap-4 sm:grid-cols-[1fr_200px]">
        <Input
          name="search" icon={Search} placeholder="Search by name, email or phone…"
          value={search} onChange={(e) => setSearch(e.target.value)}
        />
        <Select name="role" value={role} onChange={(e) => setRole(e.target.value)}>
          <option value="">All roles</option>
          <option value="CUSTOMER">Customers</option>
          <option value="ADMIN">Administrators</option>
        </Select>
      </Card>

      <Card className="overflow-hidden p-0">
        {loading ? (
          <div className="space-y-2 p-5">
            {Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-14" />)}
          </div>
        ) : data?.content?.length ? (
          <>
            {/* Horizontal scroll rather than squeezing columns: a cramped table is unreadable. */}
            <div className="overflow-x-auto">
              <table className="w-full min-w-[720px] text-sm">
                <thead>
                  <tr className="border-b text-left text-xs uppercase tracking-wider text-muted">
                    <th className="px-5 py-3 font-semibold">Customer</th>
                    <th className="px-5 py-3 font-semibold">Contact</th>
                    <th className="px-5 py-3 font-semibold">Status</th>
                    <th className="px-5 py-3 font-semibold">Joined</th>
                    <th className="px-5 py-3 text-right font-semibold">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[color:var(--surface-border)]">
                  {data.content.map((user) => (
                    <tr key={user.id} className="transition-colors hover:bg-[color:var(--surface-hover)]">
                      <td className="px-5 py-3.5">
                        <div className="flex items-center gap-3">
                          <span className="brand-gradient grid h-9 w-9 shrink-0 place-items-center rounded-full text-xs font-bold text-white">
                            {initialsOf(user.fullName)}
                          </span>
                          <div className="min-w-0">
                            <p className="truncate font-medium">{user.fullName}</p>
                            <p className="truncate text-xs text-muted">{user.role}</p>
                          </div>
                        </div>
                      </td>
                      <td className="px-5 py-3.5">
                        <p className="truncate">{user.email}</p>
                        <p className="tnum text-xs text-muted">{user.phone}</p>
                      </td>
                      <td className="px-5 py-3.5">
                        <div className="flex flex-wrap gap-1.5">
                          <Badge tone={user.enabled ? 'success' : 'danger'}>
                            {user.enabled ? 'Active' : 'Disabled'}
                          </Badge>
                          <Badge tone={user.kycVerified ? 'success' : 'warning'}>
                            {user.kycVerified ? <BadgeCheck size={11} /> : <ShieldAlert size={11} />}
                            {user.kycVerified ? 'KYC' : 'No KYC'}
                          </Badge>
                        </div>
                      </td>
                      <td className="px-5 py-3.5 text-xs text-secondary">{formatDate(user.createdAt)}</td>
                      <td className="px-5 py-3.5">
                        <div className="flex justify-end gap-2">
                          <Button size="sm" variant="ghost" onClick={() => toggleKyc(user)}>
                            {user.kycVerified ? 'Revoke KYC' : 'Verify KYC'}
                          </Button>
                          <Button
                            size="sm"
                            variant={user.enabled ? 'ghost' : 'secondary'}
                            disabled={user.role === 'ADMIN'}
                            onClick={() => setTarget(user)}
                            className={cx(user.enabled && user.role !== 'ADMIN' && 'text-[color:var(--accent-danger)]')}
                          >
                            {user.enabled ? 'Disable' : 'Enable'}
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <Pagination data={data} onPage={setPage} />
          </>
        ) : (
          <EmptyState icon={Users} title="No users match your search" />
        )}
      </Card>

      <Modal
        open={Boolean(target)}
        onClose={() => { setTarget(null); setReason('') }}
        title={target?.enabled ? 'Disable this customer?' : 'Enable this customer?'}
        subtitle={
          target?.enabled
            ? `${target?.fullName} will be signed out of every device and blocked from signing in.`
            : `${target?.fullName} will regain access immediately.`
        }
        size="sm"
      >
        <div className="space-y-4">
          <Input
            label="Reason (recorded in the audit trail)" name="reason" maxLength={255}
            placeholder="Suspected fraudulent activity"
            value={reason} onChange={(e) => setReason(e.target.value)}
          />
          <div className="flex gap-3">
            <Button variant="secondary" onClick={() => { setTarget(null); setReason('') }} className="flex-1">
              Cancel
            </Button>
            <Button
              variant={target?.enabled ? 'danger' : 'primary'}
              loading={saving} onClick={toggleStatus} className="flex-1"
            >
              {target?.enabled ? 'Disable' : 'Enable'}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  )
}

export function Pagination({ data, onPage }) {
  return (
    <div className="flex items-center justify-between border-t px-5 py-3.5">
      <p className="text-sm text-secondary">
        Page <span className="font-semibold">{data.page + 1}</span> of{' '}
        <span className="font-semibold">{data.totalPages || 1}</span>
      </p>
      <div className="flex gap-2">
        <Button variant="secondary" size="sm" disabled={data.first} onClick={() => onPage((p) => Math.max(0, p - 1))}>
          <ChevronLeft size={15} /> Previous
        </Button>
        <Button variant="secondary" size="sm" disabled={data.last} onClick={() => onPage((p) => p + 1)}>
          Next <ChevronRight size={15} />
        </Button>
      </div>
    </div>
  )
}
