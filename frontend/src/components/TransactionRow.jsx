import { ArrowDownLeft, ArrowUpRight, Banknote, Landmark, Percent } from 'lucide-react'
import { formatCurrency, formatDate } from '../utils/format'
import { cx, TONE_CHIP } from './ui'

const TYPE_ICON = {
  DEPOSIT: Banknote,
  WITHDRAWAL: Landmark,
  TRANSFER_IN: ArrowDownLeft,
  TRANSFER_OUT: ArrowUpRight,
  INTEREST_CREDIT: Percent,
  FEE: Landmark,
}

/**
 * One line of a statement.
 *
 * The sign (+/−) carries the direction as well as the colour, so a colour-blind reader - or
 * anyone reading a printout - can still tell a credit from a debit.
 */
export default function TransactionRow({ transaction, showAccount = false }) {
  const isCredit = transaction.direction === 'CREDIT'
  const Icon = TYPE_ICON[transaction.type] || Banknote

  return (
    <div className="flex items-center gap-3.5 rounded-xl px-3 py-3 transition-colors hover:bg-[color:var(--surface-hover)]">
      <span
        className={cx(
          'grid h-10 w-10 shrink-0 place-items-center rounded-xl',
          // orange-500 (C .187) was the highest-chroma value in the app and sat directly
          // against emerald-500 in the same row - the hottest adjacency on screen.
          // --accent-negative is in the chart's series2 hue family, so the Dashboard stops
          // showing two different oranges for money-out inside one viewport.
          isCredit ? TONE_CHIP.positive : TONE_CHIP.negative,
        )}
        aria-hidden="true"
      >
        <Icon size={17} />
      </span>

      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium">{transaction.description}</p>
        <p className="truncate text-xs text-muted">
          {formatDate(transaction.createdAt, true)}
          {transaction.category && ` · ${transaction.category}`}
          {showAccount && ` · ${transaction.accountNumber}`}
          {transaction.counterpartyName && ` · ${transaction.counterpartyName}`}
        </p>
      </div>

      <div className="shrink-0 text-right">
        <p
          className={cx(
            'tnum text-sm font-semibold',
            isCredit ? 'text-[color:var(--accent-positive)]' : 'text-[color:var(--text-primary)]',
          )}
        >
          {isCredit ? '+' : '−'}
          {formatCurrency(transaction.amount).replace('₹', '₹')}
        </p>
        <p className="tnum text-xs text-muted">Bal {formatCurrency(transaction.balanceAfter)}</p>
      </div>
    </div>
  )
}
