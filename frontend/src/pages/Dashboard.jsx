import { Link } from 'react-router-dom'
import {
  ArrowDownLeft,
  ArrowLeftRight,
  ArrowUpRight,
  CreditCard,
  PiggyBank,
  Plus,
  Receipt,
  TrendingUp,
  Wallet,
} from 'lucide-react'
import { transactionApi } from '../api/endpoints'
import { useApi } from '../hooks/useApi'
import { Card, CardHeader, EmptyState, PageLoader, Skeleton, TONE_CHIP, cx } from '../components/ui'
import { CategoryBarChart, TrendAreaChart } from '../components/charts/Charts'
import TransactionRow from '../components/TransactionRow'
import { formatAccountNumber, formatCurrency } from '../utils/format'

const QUICK_ACTIONS = [
  { to: '/transfer', label: 'Send money', icon: ArrowLeftRight },
  { to: '/accounts', label: 'Deposit', icon: ArrowDownLeft },
  { to: '/accounts', label: 'Open account', icon: Plus },
  { to: '/transactions', label: 'Statements', icon: Receipt },
]

const ACCOUNT_ICON = { SAVINGS: PiggyBank, CURRENT: CreditCard, FIXED_DEPOSIT: TrendingUp }

export default function Dashboard() {
  const { data, loading, error } = useApi(() => transactionApi.dashboard(), [])

  if (loading) return <PageLoader label="Loading your dashboard" />

  if (error) {
    return (
      <Card>
        <EmptyState
          icon={Wallet}
          title="We could not load your dashboard"
          description={error.message}
        />
      </Card>
    )
  }

  const netFlow = Number(data.monthlyIncome) - Number(data.monthlySpend)

  return (
    <div className="mx-auto max-w-7xl space-y-6">
      {/* ---- Hero: total balance is the one number the user opened the app for ---- */}
      <section className="surface relative overflow-hidden p-6 sm:p-8 animate-fade-up">
        <div
          aria-hidden="true"
          className="brand-gradient absolute -right-20 -top-24 h-64 w-64 rounded-full opacity-20 blur-3xl"
        />
        <div className="relative flex flex-wrap items-end justify-between gap-6">
          <div>
            <p className="text-sm text-secondary">Total balance across {data.accountCount} account{data.accountCount === 1 ? '' : 's'}</p>
            <p className="tnum mt-1.5 text-4xl font-bold tracking-tight sm:text-5xl">
              {formatCurrency(data.totalBalance)}
            </p>
            <p className="mt-2 flex items-center gap-1.5 text-sm">
              <span
                className={cx(
                  'font-semibold',
                  netFlow >= 0
                    ? 'text-[color:var(--accent-positive)]'
                    : 'text-[color:var(--accent-negative)]',
                )}
              >
                {netFlow >= 0 ? '↑' : '↓'} {formatCurrency(Math.abs(netFlow))}
              </span>
              <span className="text-secondary">net this month</span>
            </p>
          </div>

          <div className="grid grid-cols-2 gap-2 sm:flex sm:gap-2">
            {QUICK_ACTIONS.map(({ to, label, icon: Icon }) => (
              <Link
                key={label}
                to={to}
                className="surface-raised flex items-center gap-2 rounded-xl px-3.5 py-2.5 text-sm font-medium transition-all hover:bg-[color:var(--surface-hover)] hover:shadow-[var(--elev-2)]"
              >
                <Icon size={16} /> {label}
              </Link>
            ))}
          </div>
        </div>
      </section>

      {/* ---- Stat tiles ---- */}
      <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <StatTile
          label="Money in this month" value={formatCurrency(data.monthlyIncome)}
          icon={ArrowDownLeft} tone="positive"
        />
        <StatTile
          label="Money out this month" value={formatCurrency(data.monthlySpend)}
          icon={ArrowUpRight} tone="negative"
        />
        <StatTile
          label="Open accounts" value={data.accountCount}
          icon={Wallet} tone="info"
        />
      </section>

      {/* ---- Charts ---- */}
      <section className="grid gap-5 lg:grid-cols-5">
        <Card className="lg:col-span-3">
          <CardHeader
            title="Cash flow" subtitle="Money in versus money out, last six months" icon={TrendingUp}
          />
          {data.monthlyTrend?.length ? (
            <TrendAreaChart data={data.monthlyTrend} />
          ) : (
            <Skeleton className="h-[260px]" />
          )}
        </Card>

        <Card className="lg:col-span-2">
          <CardHeader title="Where it went" subtitle="Spending by category this month" icon={Receipt} />
          {data.spendByCategory?.length ? (
            <CategoryBarChart data={data.spendByCategory} />
          ) : (
            <EmptyState title="No spending yet" description="Categorised spending appears here once you start transacting." />
          )}
        </Card>
      </section>

      {/* ---- Accounts + recent activity ---- */}
      <section className="grid gap-5 lg:grid-cols-5">
        <Card className="lg:col-span-2">
          <CardHeader
            title="Your accounts"
            icon={Wallet}
            action={
              <Link to="/accounts" className="text-sm font-medium text-[color:var(--accent-positive)] hover:underline">
                Manage
              </Link>
            }
          />
          <div className="space-y-2.5">
            {data.accounts.map((account) => {
              const Icon = ACCOUNT_ICON[account.type] || Wallet
              return (
                <Link
                  key={account.accountNumber}
                  to={`/transactions?accountNumber=${account.accountNumber}`}
                  className="surface-raised flex items-center gap-3 rounded-xl p-3.5 transition-all hover:border-[color:var(--surface-border-strong)]"
                >
                  <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-[color:var(--accent-positive-chip)] text-[color:var(--accent-positive)]">
                    <Icon size={17} />
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-semibold">{account.typeLabel}</p>
                    <p className="tnum truncate text-xs text-muted">
                      {formatAccountNumber(account.accountNumber)}
                    </p>
                  </div>
                  <p className="tnum shrink-0 text-sm font-semibold">{formatCurrency(account.balance)}</p>
                </Link>
              )
            })}
          </div>
        </Card>

        <Card className="lg:col-span-3">
          <CardHeader
            title="Recent activity"
            icon={Receipt}
            action={
              <Link to="/transactions" className="text-sm font-medium text-[color:var(--accent-positive)] hover:underline">
                View all
              </Link>
            }
          />
          {data.recentTransactions?.length ? (
            <div className="-mx-3 divide-y divide-[color:var(--surface-border)]">
              {data.recentTransactions.map((transaction) => (
                <TransactionRow key={transaction.id} transaction={transaction} showAccount />
              ))}
            </div>
          ) : (
            <EmptyState
              icon={Receipt}
              title="No transactions yet"
              description="Your deposits, withdrawals and transfers will appear here."
            />
          )}
        </Card>
      </section>
    </div>
  )
}

function StatTile({ label, value, icon: Icon, tone }) {
  return (
    <Card className="flex items-center gap-4">
      <span className={cx('grid h-12 w-12 shrink-0 place-items-center rounded-2xl', TONE_CHIP[tone])}>
        <Icon size={20} />
      </span>
      <div className="min-w-0">
        <p className="text-sm text-secondary">{label}</p>
        <p className="tnum truncate text-xl font-bold tracking-tight">{value}</p>
      </div>
    </Card>
  )
}
