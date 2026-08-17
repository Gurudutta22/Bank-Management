import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  LabelList,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { useTheme } from '../../context/ThemeContext'
import { formatCompact, formatCurrency } from '../../utils/format'
import { axisProps, chartColors } from './theme'

/** Shared tooltip so every chart in the app explains itself the same way. */
function ChartTooltip({ active, payload, label, colors }) {
  if (!active || !payload?.length) return null

  return (
    <div
      className="rounded-xl px-3.5 py-2.5 text-xs"
      style={{
        background: colors.tooltipBg,
        border: `1px solid ${colors.tooltipBorder}`,
        color: colors.text,
        boxShadow: 'var(--elev-3)',
      }}
    >
      <p className="mb-1.5 font-semibold">{label}</p>
      {payload.map((entry) => (
        <p key={entry.dataKey} className="flex items-center gap-2 tnum">
          <span
            className="h-2 w-2 shrink-0 rounded-full"
            style={{ background: entry.color }}
            aria-hidden="true"
          />
          {/* The label carries identity in text too, so the tooltip never relies on colour alone. */}
          <span style={{ color: colors.textMuted }}>{entry.name}</span>
          <span className="ml-auto font-semibold">{formatCurrency(entry.value)}</span>
        </p>
      ))}
    </div>
  )
}

/** Small legend rendered as text + swatch, always present when there are two series. */
export function ChartLegend({ items }) {
  return (
    <ul className="mb-1 flex flex-wrap items-center gap-x-5 gap-y-1.5">
      {items.map((item) => (
        <li key={item.label} className="flex items-center gap-2 text-xs text-secondary">
          <span
            className="h-2.5 w-2.5 rounded-sm"
            style={{ background: item.color }}
            aria-hidden="true"
          />
          {item.label}
        </li>
      ))}
    </ul>
  )
}

/**
 * Money in vs money out over six months.
 *
 * Two magnitudes on one shared y-axis - never a second axis. A dual-scale chart lets the author
 * make any two series appear to cross wherever they like, which is why it is the most misleading
 * chart type in common use.
 */
export function TrendAreaChart({ data, height = 260 }) {
  const { isDark } = useTheme()
  const colors = chartColors(isDark)

  return (
    <div>
      <ChartLegend
        items={[
          { label: 'Money in', color: colors.series1 },
          { label: 'Money out', color: colors.series2 },
        ]}
      />
      <ResponsiveContainer width="100%" height={height}>
        <AreaChart data={data} margin={{ top: 8, right: 8, left: 4, bottom: 0 }}>
          <defs>
            <linearGradient id="fillIncome" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={colors.series1} stopOpacity={0.16} />
              <stop offset="38%" stopColor={colors.series1} stopOpacity={0.09} />
              <stop offset="72%" stopColor={colors.series1} stopOpacity={0.03} />
              <stop offset="100%" stopColor={colors.series1} stopOpacity={0} />
            </linearGradient>
            <linearGradient id="fillSpend" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={colors.series2} stopOpacity={0.16} />
              <stop offset="38%" stopColor={colors.series2} stopOpacity={0.09} />
              <stop offset="72%" stopColor={colors.series2} stopOpacity={0.03} />
              <stop offset="100%" stopColor={colors.series2} stopOpacity={0} />
            </linearGradient>
          </defs>

          {/* Horizontal rules only: vertical grid lines add clutter without aiding value reading. */}
          <CartesianGrid stroke={colors.grid} strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="month" {...axisProps(colors)} />
          <YAxis {...axisProps(colors)} width={54} tickFormatter={formatCompact} />
          <Tooltip
            content={<ChartTooltip colors={colors} />}
            cursor={{ stroke: colors.cursor, strokeWidth: 1, strokeDasharray: '4 4' }}
          />

          <Area
            type="monotone" dataKey="income" legendType="none" tooltipType="none"
            stroke={colors.surface} strokeWidth={5} fill="none"
            activeDot={false} isAnimationActive={false}
          />
          <Area
            type="monotone" dataKey="spend" legendType="none" tooltipType="none"
            stroke={colors.surface} strokeWidth={5} fill="none"
            activeDot={false} isAnimationActive={false}
          />
          <Area
            type="monotone" dataKey="income" name="Money in"
            stroke={colors.series1} strokeWidth={2.25} fill="url(#fillIncome)"
            activeDot={{ r: 4, strokeWidth: 2, stroke: colors.surface }}
          />
          <Area
            type="monotone" dataKey="spend" name="Money out"
            stroke={colors.series2} strokeWidth={2.25} fill="url(#fillSpend)"
            activeDot={{ r: 4, strokeWidth: 2, stroke: colors.surface }}
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  )
}

/**
 * Spending by category, as horizontal bars.
 *
 * Deliberately not a pie chart: comparing lengths against a shared baseline is far more accurate
 * than comparing angles, and category names fit on a horizontal axis without rotating text.
 * One hue throughout - the categories are already named on the axis, so colour has no work to do
 * and a six-colour palette would only add colour-blind risk for nothing.
 */
export function CategoryBarChart({ data, height = 260 }) {
  const { isDark } = useTheme()
  const colors = chartColors(isDark)

  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={data} layout="vertical" margin={{ top: 4, right: 56, left: 4, bottom: 4 }}>
        <CartesianGrid stroke={colors.grid} strokeDasharray="3 3" horizontal={false} />
        <XAxis type="number" hide />
        <YAxis
          type="category"
          dataKey="category"
          width={92}
          {...axisProps(colors)}
          tick={{ fill: colors.textMuted, fontSize: 11 }}
        />
        <Tooltip
          content={<ChartTooltip colors={colors} />}
          cursor={{ fill: colors.cursor, fillOpacity: isDark ? 0.18 : 0.16 }}
        />
        <Bar dataKey="amount" name="Spent" radius={[0, 4, 4, 0]} barSize={16}>
          {data.map((entry) => (
            <Cell key={entry.category} fill={colors.series1} />
          ))}
          {/* Direct labels: the reader gets the number without hovering. */}
          <LabelList
            dataKey="amount"
            position="right"
            formatter={formatCompact}
            style={{ fill: colors.textMuted, fontSize: 11 }}
          />
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  )
}

/** Daily credit vs debit volume for the admin overview. */
export function VolumeBarChart({ data, height = 280 }) {
  const { isDark } = useTheme()
  const colors = chartColors(isDark)

  return (
    <div>
      <ChartLegend
        items={[
          { label: 'Credits', color: colors.series1 },
          { label: 'Debits', color: colors.series2 },
        ]}
      />
      <ResponsiveContainer width="100%" height={height}>
        <BarChart data={data} margin={{ top: 8, right: 8, left: 4, bottom: 0 }} barGap={2}>
          <CartesianGrid stroke={colors.grid} strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="label" {...axisProps(colors)} />
          <YAxis {...axisProps(colors)} width={54} tickFormatter={formatCompact} />
          <Tooltip
            content={<ChartTooltip colors={colors} />}
            cursor={{ fill: colors.cursor, fillOpacity: isDark ? 0.18 : 0.16 }}
          />
          <Bar dataKey="credit" name="Credits" fill={colors.series1} radius={[4, 4, 0, 0]} maxBarSize={18} />
          <Bar dataKey="debit" name="Debits" fill={colors.series2} radius={[4, 4, 0, 0]} maxBarSize={18} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}
