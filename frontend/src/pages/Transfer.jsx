import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import toast from 'react-hot-toast'
import { ArrowRight, CheckCircle2, Send, ShieldCheck, Star, Users } from 'lucide-react'
import { accountApi, beneficiaryApi, transactionApi } from '../api/endpoints'
import { useApi } from '../hooks/useApi'
import { Button, Card, CardHeader, Input, PageLoader, Select, cx } from '../components/ui'
import { formatAccountNumber, formatCurrency, newIdempotencyKey } from '../utils/format'

const CATEGORIES = ['Transfer', 'Rent', 'Bills', 'Family', 'Loan repayment', 'Savings', 'Other']

export default function Transfer() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const { data: accounts, loading: loadingAccounts } = useApi(() => accountApi.list(), [])
  const { data: beneficiaries } = useApi(() => beneficiaryApi.list(), [])

  const [form, setForm] = useState({
    fromAccountNumber: '',
    // Pre-filled when the user arrived via "Send" on a saved payee.
    toAccountNumber: searchParams.get('to') || '',
    amount: '',
    description: '',
    category: 'Transfer',
  })
  const [errors, setErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)
  const [receipt, setReceipt] = useState(null)

  /**
   * One idempotency key per *form session*, minted when the form is reset rather than on each
   * click. That way a double-click or a retry after a timeout reuses the same key and the server
   * de-duplicates it; starting a genuinely new transfer mints a new one.
   */
  const [idempotencyKey, setIdempotencyKey] = useState(newIdempotencyKey)

  const transactableAccounts = useMemo(
    () => (accounts || []).filter((a) => a.status === 'ACTIVE'),
    [accounts],
  )

  useEffect(() => {
    if (transactableAccounts.length && !form.fromAccountNumber) {
      setForm((prev) => ({ ...prev, fromAccountNumber: transactableAccounts[0].accountNumber }))
    }
  }, [transactableAccounts, form.fromAccountNumber])

  if (loadingAccounts) return <PageLoader label="Loading your accounts" />

  const source = transactableAccounts.find((a) => a.accountNumber === form.fromAccountNumber)

  const update = (field) => (event) => {
    setForm((prev) => ({ ...prev, [field]: event.target.value }))
    setErrors((prev) => ({ ...prev, [field]: undefined }))
  }

  const validate = () => {
    const next = {}
    if (!form.fromAccountNumber) next.fromAccountNumber = 'Choose an account to send from'
    if (!/^\d{12}$/.test(form.toAccountNumber)) next.toAccountNumber = 'Enter a valid 12-digit account number'
    if (form.fromAccountNumber === form.toAccountNumber) {
      next.toAccountNumber = 'You cannot transfer to the same account'
    }

    const amount = Number(form.amount)
    if (!amount || amount <= 0) next.amount = 'Enter an amount greater than zero'
    else if (source && amount > Number(source.balance)) {
      // Client-side pre-check for a fast, friendly message. The authoritative check is the
      // server's, taken under a row lock - this one is convenience, not a control.
      next.amount = `That is more than the ${formatCurrency(source.balance)} available`
    }

    setErrors(next)
    return Object.keys(next).length === 0
  }

  const submit = async (event) => {
    event.preventDefault()
    if (!validate()) return

    setSubmitting(true)
    try {
      const result = await transactionApi.transfer({
        fromAccountNumber: form.fromAccountNumber,
        toAccountNumber: form.toAccountNumber,
        amount: Number(form.amount),
        description: form.description || undefined,
        category: form.category,
        idempotencyKey,
      })
      setReceipt(result)
      toast.success('Transfer completed')
    } catch (error) {
      toast.error(error.message)
      setErrors({ amount: error.message })
    } finally {
      setSubmitting(false)
    }
  }

  const startAnother = () => {
    setReceipt(null)
    setForm((prev) => ({ ...prev, toAccountNumber: '', amount: '', description: '' }))
    setIdempotencyKey(newIdempotencyKey())
  }

  if (receipt) return <Receipt receipt={receipt} onAnother={startAnother} onDone={() => navigate('/dashboard')} />

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <header>
        <h1 className="text-2xl font-bold tracking-tight">Send money</h1>
        <p className="mt-1 text-sm text-secondary">
          Transfers between NovaBank accounts settle instantly.
        </p>
      </header>

      <div className="grid gap-5 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <form onSubmit={submit} className="space-y-5" noValidate>
            <Select
              label="From" name="fromAccountNumber" value={form.fromAccountNumber}
              onChange={update('fromAccountNumber')} error={errors.fromAccountNumber}
            >
              {transactableAccounts.length === 0 && <option value="">No active accounts</option>}
              {transactableAccounts.map((account) => (
                <option key={account.accountNumber} value={account.accountNumber}>
                  {account.typeLabel} · {account.accountNumber} · {formatCurrency(account.balance)}
                </option>
              ))}
            </Select>

            {source && (
              <div className="surface-raised flex items-center justify-between rounded-xl p-3.5 text-sm">
                <span className="text-secondary">Available to send</span>
                <span className="tnum font-semibold">{formatCurrency(source.balance)}</span>
              </div>
            )}

            <Input
              label="To account number" name="toAccountNumber" inputMode="numeric" maxLength={12}
              placeholder="900100100201"
              value={form.toAccountNumber}
              onChange={(e) =>
                update('toAccountNumber')({ target: { value: e.target.value.replace(/\D/g, '') } })
              }
              error={errors.toAccountNumber}
              hint="12 digits, no spaces"
            />

            <Input
              label="Amount (₹)" name="amount" type="number" min="0.01" step="0.01"
              placeholder="0.00" value={form.amount} onChange={update('amount')} error={errors.amount}
            />

            <div className="flex flex-wrap gap-2">
              {[500, 1000, 2500, 5000].map((preset) => (
                <button
                  key={preset}
                  type="button"
                  onClick={() => setForm((prev) => ({ ...prev, amount: String(preset) }))}
                  className={cx(
                    'surface-raised rounded-lg px-3 py-1.5 text-xs font-medium transition-colors hover:border-[color:var(--focus-ring)] hover:text-[color:var(--accent-positive)]',
                    Number(form.amount) === preset && 'border-[color:var(--focus-ring)] text-[color:var(--accent-positive)]',
                  )}
                >
                  ₹{preset.toLocaleString('en-IN')}
                </button>
              ))}
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <Input
                label="Note (optional)" name="description" maxLength={255}
                placeholder="July rent" value={form.description} onChange={update('description')}
              />
              <Select label="Category" name="category" value={form.category} onChange={update('category')}>
                {CATEGORIES.map((category) => (
                  <option key={category} value={category}>{category}</option>
                ))}
              </Select>
            </div>

            <div className="flex items-start gap-2.5 rounded-xl border border-[color:var(--accent-positive-ring)] bg-[color:var(--accent-positive-chip)] p-3.5">
              <ShieldCheck size={17} className="mt-0.5 shrink-0 text-[color:var(--accent-positive)]" />
              <p className="text-xs text-secondary">
                This transfer is applied atomically: the debit and the credit either both succeed or
                neither does, and a repeated submission is de-duplicated server-side.
              </p>
            </div>

            <Button
              type="submit" size="lg" loading={submitting} className="w-full"
              disabled={transactableAccounts.length === 0}
            >
              <Send size={17} /> {submitting ? 'Sending' : 'Send money'}
            </Button>
          </form>
        </Card>

        <Card>
          <CardHeader title="Saved payees" icon={Users} />
          {beneficiaries?.length ? (
            <ul className="space-y-2">
              {beneficiaries.map((beneficiary) => (
                <li key={beneficiary.id}>
                  <button
                    type="button"
                    onClick={() =>
                      setForm((prev) => ({ ...prev, toAccountNumber: beneficiary.accountNumber }))
                    }
                    className={cx(
                      'surface-raised w-full rounded-xl p-3 text-left transition-all hover:border-[color:var(--surface-border-strong)]',
                      form.toAccountNumber === beneficiary.accountNumber && 'border-[color:var(--focus-ring)]',
                    )}
                  >
                    <p className="flex items-center gap-1.5 text-sm font-semibold">
                      {beneficiary.favourite && (
                        <Star size={12} className="fill-[color:var(--accent-favorite)] text-[color:var(--accent-favorite)]" aria-label="Favourite" />
                      )}
                      {beneficiary.nickname}
                    </p>
                    <p className="truncate text-xs text-muted">{beneficiary.holderName}</p>
                    <p className="tnum text-xs text-muted">{beneficiary.maskedAccountNumber}</p>
                  </button>
                </li>
              ))}
            </ul>
          ) : (
            <p className="py-6 text-center text-sm text-secondary">
              No saved payees yet. Add one from the Payees page to skip typing account numbers.
            </p>
          )}
        </Card>
      </div>
    </div>
  )
}

/* --------------------------------------------------------------- receipt */

function Receipt({ receipt, onAnother, onDone }) {
  return (
    <div className="mx-auto max-w-lg animate-fade-up">
      <Card className="text-center">
        <span className="mx-auto grid h-16 w-16 place-items-center rounded-full bg-[color:var(--accent-positive-chip)] text-[color:var(--accent-positive)]">
          <CheckCircle2 size={30} />
        </span>
        <h2 className="mt-4 text-xl font-bold tracking-tight">Transfer complete</h2>
        <p className="tnum mt-2 text-4xl font-bold tracking-tight">{formatCurrency(receipt.amount)}</p>

        <div className="my-6 flex items-center justify-center gap-3 text-sm">
          <span className="tnum surface-raised rounded-lg px-3 py-2">{receipt.fromAccountNumber}</span>
          <ArrowRight size={16} className="text-[color:var(--accent-positive)]" />
          <span className="tnum surface-raised rounded-lg px-3 py-2">{receipt.toAccountNumber}</span>
        </div>

        <dl className="space-y-2.5 border-t pt-5 text-left text-sm">
          <Row label="Recipient" value={receipt.toAccountHolder} />
          <Row label="New balance" value={formatCurrency(receipt.sourceBalanceAfter)} mono />
          <Row label="Reference" value={receipt.reference} mono small />
        </dl>

        <div className="mt-6 flex gap-3">
          <Button variant="secondary" onClick={onAnother} className="flex-1">Send another</Button>
          <Button onClick={onDone} className="flex-1">Back to dashboard</Button>
        </div>
      </Card>
    </div>
  )
}

function Row({ label, value, mono, small }) {
  return (
    <div className="flex items-start justify-between gap-4">
      <dt className="text-secondary">{label}</dt>
      <dd className={cx('text-right font-medium', mono && 'tnum', small && 'text-xs break-all')}>
        {value}
      </dd>
    </div>
  )
}
