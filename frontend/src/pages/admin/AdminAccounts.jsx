import { useEffect, useState } from 'react'
import toast from 'react-hot-toast'
import { Search, Wallet } from 'lucide-react'
import { adminApi } from '../../api/endpoints'
import { useApi, useDebounced } from '../../hooks/useApi'
import {
  Button, Card, EmptyState, Input, Modal, Select, Skeleton, StatusBadge,
} from '../../components/ui'
import { Pagination } from './AdminUsers'
import { formatAccountNumber, formatCurrency, formatDate } from '../../utils/format'

export default function AdminAccounts() {
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const [target, setTarget] = useState(null)
  const [nextStatus, setNextStatus] = useState('FROZEN')
  const [reason, setReason] = useState('')
  const [saving, setSaving] = useState(false)

  const debouncedSearch = useDebounced(search, 400)

  const { data, loading, refetch } = useApi(
    () => adminApi.accounts({ search: debouncedSearch || undefined, status: status || undefined, page, size: 15 }),
    [debouncedSearch, status, page],
  )

  useEffect(() => setPage(0), [debouncedSearch, status])

  const openStatusModal = (account) => {
    setTarget(account)
    // Default to the most likely next action: unfreeze a frozen account, freeze an active one.
    setNextStatus(account.status === 'FROZEN' ? 'ACTIVE' : 'FROZEN')
    setReason('')
  }

  const applyStatus = async () => {
    setSaving(true)
    try {
      await adminApi.setAccountStatus(target.accountNumber, { status: nextStatus, reason: reason || undefined })
      toast.success(`Account ${target.accountNumber} set to ${nextStatus}`)
      setTarget(null)
      refetch({ quiet: true })
    } catch (error) {
      toast.error(error.message)
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="mx-auto max-w-6xl space-y-5">
      <header>
        <h1 className="text-2xl font-bold tracking-tight">Accounts</h1>
        <p className="mt-1 text-sm text-secondary">
          {data ? `${data.totalElements} account${data.totalElements === 1 ? '' : 's'} across the bank` : 'Loading…'}
        </p>
      </header>

      <Card className="grid gap-4 sm:grid-cols-[1fr_200px]">
        <Input
          name="search" icon={Search} placeholder="Search by account number, holder or email…"
          value={search} onChange={(e) => setSearch(e.target.value)}
        />
        <Select name="status" value={status} onChange={(e) => setStatus(e.target.value)}>
          <option value="">All statuses</option>
          <option value="ACTIVE">Active</option>
          <option value="FROZEN">Frozen</option>
          <option value="CLOSED">Closed</option>
        </Select>
      </Card>

      <Card className="overflow-hidden p-0">
        {loading ? (
          <div className="space-y-2 p-5">
            {Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-14" />)}
          </div>
        ) : data?.content?.length ? (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[760px] text-sm">
                <thead>
                  <tr className="border-b text-left text-xs uppercase tracking-wider text-muted">
                    <th className="px-5 py-3 font-semibold">Account</th>
                    <th className="px-5 py-3 font-semibold">Holder</th>
                    <th className="px-5 py-3 font-semibold">Type</th>
                    <th className="px-5 py-3 text-right font-semibold">Balance</th>
                    <th className="px-5 py-3 font-semibold">Status</th>
                    <th className="px-5 py-3 text-right font-semibold">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[color:var(--surface-border)]">
                  {data.content.map((account) => (
                    <tr key={account.accountNumber} className="transition-colors hover:bg-[color:var(--surface-hover)]">
                      <td className="tnum px-5 py-3.5 font-medium">
                        {formatAccountNumber(account.accountNumber)}
                        <p className="text-xs font-normal text-muted">
                          Opened {formatDate(account.openedAt)}
                        </p>
                      </td>
                      <td className="px-5 py-3.5">
                        <p className="truncate font-medium">{account.ownerName}</p>
                        <p className="truncate text-xs text-muted">{account.ownerEmail}</p>
                      </td>
                      <td className="px-5 py-3.5">{account.typeLabel}</td>
                      <td className="tnum px-5 py-3.5 text-right font-semibold">
                        {formatCurrency(account.balance)}
                      </td>
                      <td className="px-5 py-3.5"><StatusBadge status={account.status} /></td>
                      <td className="px-5 py-3.5 text-right">
                        <Button
                          size="sm" variant="ghost"
                          disabled={account.status === 'CLOSED'}
                          onClick={() => openStatusModal(account)}
                        >
                          Change status
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <Pagination data={data} onPage={setPage} />
          </>
        ) : (
          <EmptyState icon={Wallet} title="No accounts match your search" />
        )}
      </Card>

      <Modal
        open={Boolean(target)}
        onClose={() => setTarget(null)}
        title="Change account status"
        subtitle={target ? `${target.accountNumber} · ${target.ownerName}` : ''}
      >
        <div className="space-y-4">
          <div className="surface-raised flex items-center justify-between rounded-xl p-3.5 text-sm">
            <span className="text-secondary">Current balance</span>
            <span className="tnum font-semibold">{formatCurrency(target?.balance)}</span>
          </div>

          <Select label="New status" name="nextStatus" value={nextStatus} onChange={(e) => setNextStatus(e.target.value)}>
            <option value="ACTIVE">Active — money moves normally</option>
            <option value="FROZEN">Frozen — debits blocked, credits allowed</option>
            <option value="CLOSED">Closed — permanent, requires a zero balance</option>
          </Select>

          {nextStatus === 'CLOSED' && Number(target?.balance) !== 0 && (
            <p className="rounded-xl border border-[color:var(--accent-warning-ring)] bg-[color:var(--accent-warning-chip)] p-3 text-xs text-[color:var(--accent-warning)]">
              This account still holds {formatCurrency(target?.balance)}. The balance must reach zero
              before it can be closed — the server will reject this.
            </p>
          )}

          <Input
            label="Reason (recorded in the audit trail)" name="reason" maxLength={255}
            placeholder="Court order / customer request"
            value={reason} onChange={(e) => setReason(e.target.value)}
          />

          <div className="flex gap-3">
            <Button variant="secondary" onClick={() => setTarget(null)} className="flex-1">Cancel</Button>
            <Button
              variant={nextStatus === 'CLOSED' ? 'danger' : 'primary'}
              loading={saving} onClick={applyStatus} className="flex-1"
            >
              Apply
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  )
}
