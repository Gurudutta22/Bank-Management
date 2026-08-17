import {
  Activity, Ban, Landmark, Snowflake, TrendingUp, UserPlus, Users, Wallet,
} from 'lucide-react'
import { adminApi } from '../../api/endpoints'
import { useApi } from '../../hooks/useApi'
import { Card, CardHeader, EmptyState, PageLoader, TONE_CHIP, cx } from '../../components/ui'
import { VolumeBarChart } from '../../components/charts/Charts'
import { formatCurrency, formatNumber } from '../../utils/format'

export default function AdminDashboard() {
  const { data, loading, error } = useApi(() => adminApi.stats(), [])

  if (loading) return <PageLoader label="Loading bank metrics" />
  if (error) {
    return <Card><EmptyState icon={Activity} title="Could not load metrics" description={error.message} /></Card>
  }

  // The API returns one point per day; label them compactly for the x-axis.
  const volumeData = (data.dailyVolume || []).map((point) => ({
    label: point.date.slice(5), // MM-DD
    credit: Number(point.credit),
    debit: Number(point.debit),
  }))

  return (
    <div className="mx-auto max-w-7xl space-y-6">
      <header>
        <h1 className="text-2xl font-bold tracking-tight">Bank overview</h1>
        <p className="mt-1 text-sm text-secondary">
          Portfolio health and activity across every customer and account.
        </p>
      </header>

      {/* The headline number: what the bank is actually holding. */}
      <Card className="relative overflow-hidden animate-fade-up">
        <div
          aria-hidden="true"
          className="brand-gradient absolute -right-24 -top-28 h-72 w-72 rounded-full opacity-20 blur-3xl"
        />
        <div className="relative">
          <p className="text-sm text-secondary">Total customer holdings</p>
          <p className="tnum mt-1.5 text-4xl font-bold tracking-tight sm:text-5xl">
            {formatCurrency(data.totalHoldings)}
          </p>
          <p className="mt-2 text-sm text-secondary">
            across {formatNumber(data.totalAccounts)} accounts ·{' '}
            {formatCurrency(data.volumeLast30Days)} moved in the last 30 days
          </p>
        </div>
      </Card>

      <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Metric label="Customers" value={formatNumber(data.totalCustomers)} icon={Users} tone="info"
                foot={`${formatNumber(data.newUsersLast30Days)} joined in 30 days`} />
        <Metric label="Active accounts" value={formatNumber(data.activeAccounts)} icon={Wallet} tone="positive"
                foot={`${formatNumber(data.totalAccounts)} total`} />
        <Metric label="Frozen accounts" value={formatNumber(data.frozenAccounts)} icon={Snowflake} tone="warning"
                foot="Debits blocked, credits allowed" />
        <Metric label="Closed accounts" value={formatNumber(data.closedAccounts)} icon={Ban} tone="danger"
                foot="Terminal state" />
      </section>

      <section className="grid gap-5 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader
            title="Daily transaction volume"
            subtitle="Credits and debits across the bank, last 30 days"
            icon={TrendingUp}
          />
          {volumeData.length ? (
            <VolumeBarChart data={volumeData} />
          ) : (
            <EmptyState title="No activity in this window" />
          )}
        </Card>

        <Card>
          <CardHeader title="At a glance" icon={Landmark} />
          <dl className="space-y-3.5">
            <Line label="Total users" value={formatNumber(data.totalUsers)} />
            <Line label="Administrators" value={formatNumber(data.totalAdmins)} />
            <Line label="Customers" value={formatNumber(data.totalCustomers)} />
            <Line label="New this month" value={formatNumber(data.newUsersLast30Days)} icon={UserPlus} />
            <Line label="Transactions (30d)" value={formatNumber(data.transactionsLast30Days)} />
            <Line label="Volume (30d)" value={formatCurrency(data.volumeLast30Days)} />
          </dl>
        </Card>
      </section>
    </div>
  )
}

function Metric({ label, value, icon: Icon, tone, foot }) {
  return (
    <Card>
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-sm text-secondary">{label}</p>
          <p className="tnum mt-1 text-2xl font-bold tracking-tight">{value}</p>
        </div>
        <span className={cx('grid h-10 w-10 shrink-0 place-items-center rounded-xl', TONE_CHIP[tone])}>
          <Icon size={18} />
        </span>
      </div>
      {foot && <p className="mt-3 truncate text-xs text-muted">{foot}</p>}
    </Card>
  )
}

function Line({ label, value, icon: Icon }) {
  return (
    <div className="flex items-center justify-between gap-3 border-b pb-3 last:border-0 last:pb-0">
      <dt className="flex items-center gap-2 text-sm text-secondary">
        {Icon && <Icon size={14} className="text-muted" />} {label}
      </dt>
      <dd className="tnum text-sm font-semibold">{value}</dd>
    </div>
  )
}
