import { useState } from 'react'
import { Link } from 'react-router-dom'
import toast from 'react-hot-toast'
import {
  ArrowDownLeft,
  ArrowUpRight,
  CreditCard,
  Download,
  PiggyBank,
  Plus,
  TrendingUp,
  Wallet,
} from 'lucide-react'
import { accountApi, transactionApi } from '../api/endpoints'
import { useApi } from '../hooks/useApi'
import {
  Badge, Button, Card, EmptyState, Input, Modal, PageLoader, Select, StatusBadge, cx,
} from '../components/ui'
import { daysAgo, formatAccountNumber, formatCurrency, formatDate, isoDate, newIdempotencyKey } from '../utils/format'

const ACCOUNT_ICON = { SAVINGS: PiggyBank, CURRENT: CreditCard, FIXED_DEPOSIT: TrendingUp }

const ACCOUNT_BLURB = {
  SAVINGS: 'Earns 3.50% p.a. Requires a ₹500 minimum balance.',
  CURRENT: 'No interest, but comes with a ₹10,000 overdraft buffer.',
  FIXED_DEPOSIT: 'Earns 7.10% p.a. Funds are locked until maturity.',
}

export default function Accounts() {
  const { data: accounts, loading, refetch } = useApi(() => accountApi.list(), [])
  const [openModal, setOpenModal] = useState(null) // 'open' | 'deposit' | 'withdraw'
  const [activeAccount, setActiveAccount] = useState(null)

  if (loading) return <PageLoader label="Loading your accounts" />

  const startMoneyAction = (mode, account) => {
    setActiveAccount(account)
    setOpenModal(mode)
  }

  const closeAll = () => {
    setOpenModal(null)
    setActiveAccount(null)
  }

  return (
    <div className="mx-auto max-w-7xl space-y-6">
      <header className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Accounts</h1>
          <p className="mt-1 text-sm text-secondary">
            Open new accounts, move cash in and out, and download statements.
          </p>
        </div>
        <Button onClick={() => setOpenModal('open')}>
          <Plus size={17} /> Open account
        </Button>
      </header>

      {accounts?.length ? (
        <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-3">
          {accounts.map((account) => (
            <AccountCard
              key={account.accountNumber}
              account={account}
              onDeposit={() => startMoneyAction('deposit', account)}
              onWithdraw={() => startMoneyAction('withdraw', account)}
            />
          ))}
        </div>
      ) : (
        <Card>
          <EmptyState
            icon={Wallet}
            title="You do not have any accounts yet"
            description="Open your first account to start banking."
            action={<Button onClick={() => setOpenModal('open')}><Plus size={16} /> Open account</Button>}
          />
        </Card>
      )}

      <OpenAccountModal
        open={openModal === 'open'}
        onClose={closeAll}
        onDone={() => { closeAll(); refetch({ quiet: true }) }}
      />

      <MoneyModal
        mode={openModal === 'deposit' || openModal === 'withdraw' ? openModal : null}
        account={activeAccount}
        onClose={closeAll}
        onDone={() => { closeAll(); refetch({ quiet: true }) }}
      />
    </div>
  )
}

/* ------------------------------------------------------------------ card */

function AccountCard({ account, onDeposit, onWithdraw }) {
  const Icon = ACCOUNT_ICON[account.type] || Wallet
  const [downloading, setDownloading] = useState(false)
  const isActive = account.status === 'ACTIVE'

  /**
   * Downloads the CSV statement.
   *
   * The response is a blob, so we build a temporary object URL and click a synthetic anchor -
   * the only way to trigger a browser download for a response that needed an auth header and so
   * could not simply be a plain <a href>.
   */
  const downloadStatement = async () => {
    setDownloading(true)
    try {
      const response = await accountApi.statement(account.accountNumber, daysAgo(180), isoDate())
      const url = URL.createObjectURL(new Blob([response.data], { type: 'text/csv' }))
      const link = document.createElement('a')
      link.href = url
      link.download = `statement-${account.accountNumber}.csv`
      document.body.appendChild(link)
      link.click()
      link.remove()
      URL.revokeObjectURL(url)
      toast.success('Statement downloaded')
    } catch (error) {
      toast.error(error.message || 'Could not download the statement')
    } finally {
      setDownloading(false)
    }
  }

  return (
    <Card className="flex flex-col animate-fade-up">
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-3">
          <span className="grid h-11 w-11 place-items-center rounded-2xl bg-[color:var(--accent-positive-chip)] text-[color:var(--accent-positive)]">
            <Icon size={19} />
          </span>
          <div>
            <p className="font-semibold">{account.typeLabel}</p>
            <p className="tnum text-xs text-muted">{formatAccountNumber(account.accountNumber)}</p>
          </div>
        </div>
        <StatusBadge status={account.status} />
      </div>

      <p className="tnum mt-5 text-3xl font-bold tracking-tight">{formatCurrency(account.balance)}</p>
      <p className="mt-1 text-xs text-secondary">
        Available balance · {account.currency}
        {Number(account.interestRate) > 0 && (
          <> · <span className="text-[color:var(--accent-positive)]">{account.interestRate}% p.a.</span></>
        )}
      </p>

      <dl className="mt-4 grid grid-cols-2 gap-3 border-t pt-4 text-xs">
        <div>
          <dt className="text-muted">Daily limit</dt>
          <dd className="tnum mt-0.5 font-medium">{formatCurrency(account.dailyTransferLimit)}</dd>
        </div>
        <div>
          <dt className="text-muted">Opened</dt>
          <dd className="mt-0.5 font-medium">{formatDate(account.openedAt)}</dd>
        </div>
      </dl>

      <div className="mt-5 flex flex-wrap gap-2">
        <Button size="sm" variant="secondary" onClick={onDeposit} disabled={!isActive}>
          <ArrowDownLeft size={15} /> Deposit
        </Button>
        <Button size="sm" variant="secondary" onClick={onWithdraw} disabled={!isActive}>
          <ArrowUpRight size={15} /> Withdraw
        </Button>
        <Button size="sm" variant="ghost" onClick={downloadStatement} loading={downloading}>
          <Download size={15} /> Statement
        </Button>
      </div>

      <Link
        to={`/transactions?accountNumber=${account.accountNumber}`}
        className="mt-3 text-sm font-medium text-[color:var(--accent-positive)] hover:underline"
      >
        View transactions →
      </Link>
    </Card>
  )
}

/* --------------------------------------------------------- open account */

function OpenAccountModal({ open, onClose, onDone }) {
  const [type, setType] = useState('SAVINGS')
  const [openingBalance, setOpeningBalance] = useState('500')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState(null)

  const submit = async (event) => {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      const account = await accountApi.open({ type, openingBalance: Number(openingBalance) })
      toast.success(`${account.typeLabel} account ${account.accountNumber} opened`)
      onDone()
    } catch (err) {
      setError(err.message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title="Open a new account" subtitle="Choose a product and fund it.">
      <form onSubmit={submit} className="space-y-4">
        <Select label="Account type" name="type" value={type} onChange={(e) => setType(e.target.value)}>
          <option value="SAVINGS">Savings</option>
          <option value="CURRENT">Current</option>
          <option value="FIXED_DEPOSIT">Fixed deposit</option>
        </Select>

        <p className="surface-raised rounded-xl p-3 text-xs text-secondary">{ACCOUNT_BLURB[type]}</p>

        <Input
          label="Opening balance (₹)" name="openingBalance" type="number" min="0" step="0.01"
          value={openingBalance} onChange={(e) => setOpeningBalance(e.target.value)}
          error={error}
        />

        <div className="flex gap-3 pt-1">
          <Button type="button" variant="secondary" onClick={onClose} className="flex-1">Cancel</Button>
          <Button type="submit" loading={submitting} className="flex-1">Open account</Button>
        </div>
      </form>
    </Modal>
  )
}

/* ------------------------------------------------------ deposit/withdraw */

function MoneyModal({ mode, account, onClose, onDone }) {
  const [amount, setAmount] = useState('')
  const [description, setDescription] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState(null)

  if (!mode || !account) return null

  const isDeposit = mode === 'deposit'

  const submit = async (event) => {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      const payload = {
        accountNumber: account.accountNumber,
        amount: Number(amount),
        description: description || undefined,
        // A fresh key per submission: if the network drops and the client retries, the server
        // recognises the key and returns the original result rather than moving money twice.
        idempotencyKey: newIdempotencyKey(),
      }
      const result = isDeposit
        ? await transactionApi.deposit(payload)
        : await transactionApi.withdraw(payload)

      toast.success(
        `${isDeposit ? 'Deposited' : 'Withdrew'} ${formatCurrency(result.amount)} · new balance ${formatCurrency(result.balanceAfter)}`,
      )
      setAmount('')
      setDescription('')
      onDone()
    } catch (err) {
      setError(err.message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      open
      onClose={onClose}
      title={isDeposit ? 'Deposit cash' : 'Withdraw cash'}
      subtitle={`${account.typeLabel} · ${formatAccountNumber(account.accountNumber)}`}
    >
      <form onSubmit={submit} className="space-y-4">
        <div className="surface-raised flex items-center justify-between rounded-xl p-3.5">
          <span className="text-sm text-secondary">Current balance</span>
          <span className="tnum font-semibold">{formatCurrency(account.balance)}</span>
        </div>

        <Input
          label="Amount (₹)" name="amount" type="number" min="0.01" step="0.01" autoFocus
          placeholder="0.00" value={amount} onChange={(e) => setAmount(e.target.value)}
          error={error}
        />

        <div className="flex flex-wrap gap-2">
          {[500, 1000, 5000, 10000].map((preset) => (
            <button
              key={preset}
              type="button"
              onClick={() => setAmount(String(preset))}
              className={cx(
                'surface-raised rounded-lg px-3 py-1.5 text-xs font-medium transition-colors hover:border-[color:var(--focus-ring)] hover:text-[color:var(--accent-positive)]',
                Number(amount) === preset && 'border-[color:var(--focus-ring)] text-[color:var(--accent-positive)]',
              )}
            >
              ₹{preset.toLocaleString('en-IN')}
            </button>
          ))}
        </div>

        <Input
          label="Note (optional)" name="description" maxLength={255}
          placeholder={isDeposit ? 'Cash deposit at branch' : 'ATM withdrawal'}
          value={description} onChange={(e) => setDescription(e.target.value)}
        />

        <div className="flex gap-3 pt-1">
          <Button type="button" variant="secondary" onClick={onClose} className="flex-1">Cancel</Button>
          <Button type="submit" loading={submitting} className="flex-1">
            {isDeposit ? 'Deposit' : 'Withdraw'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
