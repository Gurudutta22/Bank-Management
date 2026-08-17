import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import toast from 'react-hot-toast'
import { Pencil, Send, Star, Trash2, UserPlus, Users } from 'lucide-react'
import { beneficiaryApi } from '../api/endpoints'
import { useApi } from '../hooks/useApi'
import { Button, Card, EmptyState, Input, Modal, PageLoader, cx } from '../components/ui'
import { formatAccountNumber, formatDate } from '../utils/format'

export default function Beneficiaries() {
  const navigate = useNavigate()
  const { data: beneficiaries, loading, refetch } = useApi(() => beneficiaryApi.list(), [])
  const [editing, setEditing] = useState(null) // null | 'new' | beneficiary
  const [deleting, setDeleting] = useState(null)

  if (loading) return <PageLoader label="Loading your payees" />

  const confirmDelete = async () => {
    try {
      await beneficiaryApi.remove(deleting.id)
      toast.success(`${deleting.nickname} removed`)
      setDeleting(null)
      refetch({ quiet: true })
    } catch (error) {
      toast.error(error.message)
    }
  }

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <header className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Payees</h1>
          <p className="mt-1 text-sm text-secondary">
            Save the accounts you send to often so you never retype a number.
          </p>
        </div>
        <Button onClick={() => setEditing('new')}>
          <UserPlus size={17} /> Add payee
        </Button>
      </header>

      {beneficiaries?.length ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {beneficiaries.map((beneficiary) => (
            <Card key={beneficiary.id} className="animate-fade-up">
              <div className="flex items-start justify-between gap-3">
                <div className="flex items-center gap-3">
                  <span className="brand-gradient grid h-11 w-11 place-items-center rounded-full text-sm font-bold text-white">
                    {beneficiary.holderName.slice(0, 2).toUpperCase()}
                  </span>
                  <div className="min-w-0">
                    <p className="flex items-center gap-1.5 truncate font-semibold">
                      {beneficiary.favourite && (
                        <Star size={13} className="shrink-0 fill-[color:var(--accent-favorite)] text-[color:var(--accent-favorite)]" aria-label="Favourite" />
                      )}
                      {beneficiary.nickname}
                    </p>
                    <p className="truncate text-xs text-muted">{beneficiary.holderName}</p>
                  </div>
                </div>
              </div>

              <p className="tnum mt-4 text-sm">{formatAccountNumber(beneficiary.accountNumber)}</p>
              <p className="mt-0.5 text-xs text-muted">
                {beneficiary.bankName} · added {formatDate(beneficiary.addedAt)}
              </p>

              <div className="mt-4 flex gap-2 border-t pt-4">
                <Button
                  size="sm" variant="secondary" className="flex-1"
                  onClick={() => navigate(`/transfer?to=${beneficiary.accountNumber}`)}
                >
                  <Send size={14} /> Send
                </Button>
                <Button size="sm" variant="ghost" onClick={() => setEditing(beneficiary)} aria-label={`Edit ${beneficiary.nickname}`}>
                  <Pencil size={14} />
                </Button>
                <Button size="sm" variant="ghost" onClick={() => setDeleting(beneficiary)} aria-label={`Remove ${beneficiary.nickname}`}>
                  <Trash2 size={14} className="text-[color:var(--accent-danger)]" />
                </Button>
              </div>
            </Card>
          ))}
        </div>
      ) : (
        <Card>
          <EmptyState
            icon={Users}
            title="No saved payees"
            description="Add a NovaBank account number and we will verify the holder's name before saving it."
            action={<Button onClick={() => setEditing('new')}><UserPlus size={16} /> Add payee</Button>}
          />
        </Card>
      )}

      <BeneficiaryModal
        state={editing}
        onClose={() => setEditing(null)}
        onDone={() => { setEditing(null); refetch({ quiet: true }) }}
      />

      <Modal
        open={Boolean(deleting)}
        onClose={() => setDeleting(null)}
        title="Remove payee"
        subtitle={deleting ? `${deleting.nickname} will no longer appear in your transfer shortcuts.` : ''}
        size="sm"
      >
        <div className="flex gap-3">
          <Button variant="secondary" onClick={() => setDeleting(null)} className="flex-1">Cancel</Button>
          <Button variant="danger" onClick={confirmDelete} className="flex-1">Remove</Button>
        </div>
      </Modal>
    </div>
  )
}

function BeneficiaryModal({ state, onClose, onDone }) {
  const isNew = state === 'new'
  const beneficiary = isNew ? null : state

  const [form, setForm] = useState({ accountNumber: '', nickname: '', favourite: false })
  const [errors, setErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)

  // Re-seed the form whenever a different payee is opened for editing.
  const [seededFor, setSeededFor] = useState(null)
  const key = isNew ? 'new' : beneficiary?.id
  if (state && seededFor !== key) {
    setSeededFor(key)
    setForm(
      isNew
        ? { accountNumber: '', nickname: '', favourite: false }
        : {
            accountNumber: beneficiary.accountNumber,
            nickname: beneficiary.nickname,
            favourite: beneficiary.favourite,
          },
    )
    setErrors({})
  }

  if (!state) return null

  const submit = async (event) => {
    event.preventDefault()
    const next = {}
    if (!/^\d{12}$/.test(form.accountNumber)) next.accountNumber = 'Enter a valid 12-digit account number'
    if (form.nickname.trim().length < 2) next.nickname = 'Give this payee a nickname'
    setErrors(next)
    if (Object.keys(next).length) return

    setSubmitting(true)
    try {
      if (isNew) {
        const created = await beneficiaryApi.add(form)
        toast.success(`${created.holderName} saved as "${created.nickname}"`)
      } else {
        await beneficiaryApi.update(beneficiary.id, form)
        toast.success('Payee updated')
      }
      onDone()
    } catch (error) {
      setErrors({ accountNumber: error.message })
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      open
      onClose={onClose}
      title={isNew ? 'Add a payee' : 'Edit payee'}
      subtitle={isNew ? "We will confirm the account holder's name before saving." : undefined}
    >
      <form onSubmit={submit} className="space-y-4">
        <Input
          label="Account number" name="accountNumber" inputMode="numeric" maxLength={12}
          placeholder="900100100201" disabled={!isNew}
          value={form.accountNumber}
          onChange={(e) =>
            setForm((prev) => ({ ...prev, accountNumber: e.target.value.replace(/\D/g, '') }))
          }
          error={errors.accountNumber}
        />

        <Input
          label="Nickname" name="nickname" maxLength={120} placeholder="Rahul (rent)"
          value={form.nickname}
          onChange={(e) => setForm((prev) => ({ ...prev, nickname: e.target.value }))}
          error={errors.nickname}
        />

        <button
          type="button"
          onClick={() => setForm((prev) => ({ ...prev, favourite: !prev.favourite }))}
          className={cx(
            'surface-raised flex w-full items-center gap-3 rounded-xl p-3.5 text-left transition-colors',
            form.favourite && 'border-[color:var(--accent-favorite)]',
          )}
          aria-pressed={form.favourite}
        >
          <Star
            size={18}
            className={cx(
              form.favourite
                ? 'fill-[color:var(--accent-favorite)] text-[color:var(--accent-favorite)]'
                : 'text-muted',
            )}
          />
          <div>
            <p className="text-sm font-medium">Mark as favourite</p>
            <p className="text-xs text-muted">Favourites are listed first on the transfer screen.</p>
          </div>
        </button>

        <div className="flex gap-3 pt-1">
          <Button type="button" variant="secondary" onClick={onClose} className="flex-1">Cancel</Button>
          <Button type="submit" loading={submitting} className="flex-1">
            {isNew ? 'Save payee' : 'Update'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
