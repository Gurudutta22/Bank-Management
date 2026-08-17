import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import toast from 'react-hot-toast'
import { ChevronLeft, ChevronRight, Download, Filter, Receipt, Search, X } from 'lucide-react'
import { accountApi, transactionApi } from '../api/endpoints'
import { useApi, useDebounced } from '../hooks/useApi'
import { Button, Card, EmptyState, Input, Select, Skeleton } from '../components/ui'
import TransactionRow from '../components/TransactionRow'
import { daysAgo, isoDate } from '../utils/format'

const TYPES = [
  { value: '', label: 'All types' },
  { value: 'DEPOSIT', label: 'Deposits' },
  { value: 'WITHDRAWAL', label: 'Withdrawals' },
  { value: 'TRANSFER_IN', label: 'Transfers in' },
  { value: 'TRANSFER_OUT', label: 'Transfers out' },
  { value: 'INTEREST_CREDIT', label: 'Interest' },
]

export default function Transactions() {
  const [searchParams, setSearchParams] = useSearchParams()

  const [filters, setFilters] = useState({
    accountNumber: searchParams.get('accountNumber') || '',
    type: '',
    from: '',
    to: '',
    search: '',
  })
  const [page, setPage] = useState(0)
  const [showFilters, setShowFilters] = useState(false)

  // Debounced so typing in the search box does not fire a request per keystroke.
  const debouncedSearch = useDebounced(filters.search, 400)

  const { data: accounts } = useApi(() => accountApi.list(), [])

  const { data, loading } = useApi(
    () =>
      transactionApi.search({
        accountNumber: filters.accountNumber || undefined,
        type: filters.type || undefined,
        from: filters.from || undefined,
        to: filters.to || undefined,
        search: debouncedSearch || undefined,
        page,
        size: 15,
      }),
    [filters.accountNumber, filters.type, filters.from, filters.to, debouncedSearch, page],
  )

  // Any filter change invalidates the current page number - staying on page 4 of a
  // now-3-page result set would show an empty screen.
  useEffect(() => {
    setPage(0)
  }, [filters.accountNumber, filters.type, filters.from, filters.to, debouncedSearch])

  useEffect(() => {
    setSearchParams(filters.accountNumber ? { accountNumber: filters.accountNumber } : {}, {
      replace: true,
    })
  }, [filters.accountNumber, setSearchParams])

  const update = (field) => (event) =>
    setFilters((prev) => ({ ...prev, [field]: event.target.value }))

  const clearFilters = () =>
    setFilters({ accountNumber: '', type: '', from: '', to: '', search: '' })

  const activeFilterCount = [filters.type, filters.from, filters.to, filters.accountNumber].filter(Boolean).length

  const exportCsv = async () => {
    const accountNumber = filters.accountNumber || accounts?.[0]?.accountNumber
    if (!accountNumber) {
      toast.error('Select an account to export')
      return
    }
    try {
      const response = await accountApi.statement(
        accountNumber,
        filters.from || daysAgo(180),
        filters.to || isoDate(),
      )
      const url = URL.createObjectURL(new Blob([response.data], { type: 'text/csv' }))
      const link = document.createElement('a')
      link.href = url
      link.download = `statement-${accountNumber}.csv`
      document.body.appendChild(link)
      link.click()
      link.remove()
      URL.revokeObjectURL(url)
      toast.success('Statement downloaded')
    } catch (error) {
      toast.error(error.message || 'Export failed')
    }
  }

  return (
    <div className="mx-auto max-w-5xl space-y-5">
      <header className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Transactions</h1>
          <p className="mt-1 text-sm text-secondary">
            {data ? `${data.totalElements} matching transaction${data.totalElements === 1 ? '' : 's'}` : 'Loading…'}
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="secondary" size="sm" onClick={() => setShowFilters((v) => !v)}>
            <Filter size={15} /> Filters
            {activeFilterCount > 0 && (
              <span className="ml-1 rounded-full bg-[color:var(--accent-positive-solid)] px-1.5 text-[10px] font-bold text-white">
                {activeFilterCount}
              </span>
            )}
          </Button>
          <Button variant="secondary" size="sm" onClick={exportCsv}>
            <Download size={15} /> Export CSV
          </Button>
        </div>
      </header>

      {/* Search stays visible; the rest of the filters collapse to keep the page calm. */}
      <Card className="space-y-4">
        <Input
          name="search" icon={Search} placeholder="Search descriptions or reference…"
          value={filters.search} onChange={update('search')}
        />

        {showFilters && (
          <div className="grid gap-4 border-t pt-4 sm:grid-cols-2 lg:grid-cols-4">
            <Select label="Account" name="accountNumber" value={filters.accountNumber} onChange={update('accountNumber')}>
              <option value="">All accounts</option>
              {(accounts || []).map((account) => (
                <option key={account.accountNumber} value={account.accountNumber}>
                  {account.typeLabel} · {account.accountNumber}
                </option>
              ))}
            </Select>

            <Select label="Type" name="type" value={filters.type} onChange={update('type')}>
              {TYPES.map((type) => (
                <option key={type.value} value={type.value}>{type.label}</option>
              ))}
            </Select>

            <Input label="From" name="from" type="date" value={filters.from} onChange={update('from')} />
            <Input label="To" name="to" type="date" value={filters.to} onChange={update('to')} />

            {activeFilterCount > 0 && (
              <button
                type="button"
                onClick={clearFilters}
                className="flex items-center gap-1.5 self-end text-sm font-medium text-[color:var(--accent-positive)] hover:underline"
              >
                <X size={14} /> Clear filters
              </button>
            )}
          </div>
        )}
      </Card>

      <Card>
        {loading ? (
          <div className="space-y-3">
            {Array.from({ length: 6 }).map((_, index) => (
              <Skeleton key={index} className="h-16" />
            ))}
          </div>
        ) : data?.content?.length ? (
          <>
            <div className="-mx-3 divide-y divide-[color:var(--surface-border)]">
              {data.content.map((transaction) => (
                <TransactionRow key={transaction.id} transaction={transaction} showAccount />
              ))}
            </div>

            <div className="mt-5 flex items-center justify-between border-t pt-4">
              <p className="text-sm text-secondary">
                Page <span className="font-semibold">{data.page + 1}</span> of{' '}
                <span className="font-semibold">{data.totalPages || 1}</span>
              </p>
              <div className="flex gap-2">
                <Button
                  variant="secondary" size="sm" disabled={data.first}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                >
                  <ChevronLeft size={15} /> Previous
                </Button>
                <Button
                  variant="secondary" size="sm" disabled={data.last}
                  onClick={() => setPage((p) => p + 1)}
                >
                  Next <ChevronRight size={15} />
                </Button>
              </div>
            </div>
          </>
        ) : (
          <EmptyState
            icon={Receipt}
            title="No transactions found"
            description={
              activeFilterCount > 0 || filters.search
                ? 'Try widening your filters or clearing the search.'
                : 'Your activity will appear here once money starts moving.'
            }
            action={
              activeFilterCount > 0 ? (
                <Button variant="secondary" size="sm" onClick={clearFilters}>Clear filters</Button>
              ) : null
            }
          />
        )}
      </Card>

      {data?.content?.length > 0 && (
        <p className="text-center text-xs text-muted">
          Showing {data.content.length} of {data.totalElements} transactions
        </p>
      )}
    </div>
  )
}
