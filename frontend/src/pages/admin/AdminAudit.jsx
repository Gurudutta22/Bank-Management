import { useEffect, useState } from 'react'
import { ClipboardList, Search } from 'lucide-react'
import { adminApi } from '../../api/endpoints'
import { useApi, useDebounced } from '../../hooks/useApi'
import { Badge, Card, EmptyState, Input, Select, Skeleton } from '../../components/ui'
import { Pagination } from './AdminUsers'
import { formatDate, formatRelative } from '../../utils/format'

const ACTIONS = [
  '', 'LOGIN', 'LOGIN_FAILED', 'LOGOUT', 'USER_REGISTERED', 'ACCOUNT_OPENED', 'ACCOUNT_CLOSED',
  'DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'INSUFFICIENT_FUNDS', 'DAILY_LIMIT_EXCEEDED',
  'UNAUTHORISED_ACCOUNT_ACCESS', 'UNAUTHORISED_TRANSACTION', 'ACCOUNT_STATUS_CHANGED',
  'USER_ENABLED', 'USER_DISABLED', 'PASSWORD_CHANGED', 'BENEFICIARY_ADDED', 'BENEFICIARY_REMOVED',
]

export default function AdminAudit() {
  const [search, setSearch] = useState('')
  const [action, setAction] = useState('')
  const [page, setPage] = useState(0)

  const debouncedSearch = useDebounced(search, 400)

  const { data, loading } = useApi(
    () => adminApi.auditLogs({ search: debouncedSearch || undefined, action: action || undefined, page, size: 20 }),
    [debouncedSearch, action, page],
  )

  useEffect(() => setPage(0), [debouncedSearch, action])

  return (
    <div className="mx-auto max-w-6xl space-y-5">
      <header>
        <h1 className="text-2xl font-bold tracking-tight">Audit trail</h1>
        <p className="mt-1 text-sm text-secondary">
          Append-only record of every security- and money-relevant action. Failed attempts are
          recorded too — that is what makes it useful.
        </p>
      </header>

      <Card className="grid gap-4 sm:grid-cols-[1fr_240px]">
        <Input
          name="search" icon={Search} placeholder="Search actor, action or detail…"
          value={search} onChange={(e) => setSearch(e.target.value)}
        />
        <Select name="action" value={action} onChange={(e) => setAction(e.target.value)}>
          {ACTIONS.map((value) => (
            <option key={value || 'all'} value={value}>
              {value || 'All actions'}
            </option>
          ))}
        </Select>
      </Card>

      <Card className="overflow-hidden p-0">
        {loading ? (
          <div className="space-y-2 p-5">
            {Array.from({ length: 8 }).map((_, i) => <Skeleton key={i} className="h-12" />)}
          </div>
        ) : data?.content?.length ? (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[820px] text-sm">
                <thead>
                  <tr className="border-b text-left text-xs uppercase tracking-wider text-muted">
                    <th className="px-5 py-3 font-semibold">When</th>
                    <th className="px-5 py-3 font-semibold">Actor</th>
                    <th className="px-5 py-3 font-semibold">Action</th>
                    <th className="px-5 py-3 font-semibold">Detail</th>
                    <th className="px-5 py-3 font-semibold">Result</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[color:var(--surface-border)]">
                  {data.content.map((log) => (
                    <tr key={log.id} className="transition-colors hover:bg-[color:var(--surface-hover)]">
                      <td className="whitespace-nowrap px-5 py-3">
                        <p className="text-xs font-medium">{formatRelative(log.createdAt)}</p>
                        <p className="text-xs text-muted">{formatDate(log.createdAt, true)}</p>
                      </td>
                      <td className="px-5 py-3">
                        <p className="max-w-[180px] truncate">{log.actor}</p>
                        <p className="tnum text-xs text-muted">{log.ipAddress}</p>
                      </td>
                      <td className="px-5 py-3">
                        <code className="rounded bg-[color:var(--surface-sunken)] px-1.5 py-0.5 text-xs">
                          {log.action}
                        </code>
                        {log.entityId && (
                          <p className="tnum mt-0.5 max-w-[160px] truncate text-xs text-muted">
                            {log.entityType} {log.entityId}
                          </p>
                        )}
                      </td>
                      <td className="max-w-[280px] px-5 py-3 text-xs text-secondary">
                        <p className="line-clamp-2">{log.detail}</p>
                      </td>
                      <td className="px-5 py-3">
                        <Badge tone={log.outcome === 'SUCCESS' ? 'success' : 'danger'}>{log.outcome}</Badge>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <Pagination data={data} onPage={setPage} />
          </>
        ) : (
          <EmptyState icon={ClipboardList} title="No audit entries match your filters" />
        )}
      </Card>
    </div>
  )
}
