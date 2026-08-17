/**
 * Chart colour tokens.
 *
 * The two SERIES hues were run through a palette validator for lightness band, chroma floor,
 * colour-blind (protan/deutan/tritan) separation and contrast against the actual chart surface,
 * in both light and dark mode. They clear the CVD floor with ΔE ~25 and the normal-vision floor
 * with ΔE ~32, so the two lines stay distinguishable for every reader.
 *
 * They are therefore the ONLY hard-coded values left in this file, and they are unchanged. The
 * colour pass around them deliberately did not touch them: re-validating a CVD palette is not
 * something a smoothness pass gets to do as a side effect. Re-measured against the new card
 * surfaces: light series1 4.22:1, series2 3.06:1; dark series1 4.73:1, series2 4.43:1 - all
 * above the 3:1 non-text floor.
 *
 * KNOWN, DELIBERATELY UNFIXED: the file used to claim both modes were lightness-matched, but
 * only dark is (both at OKLCH L .6221). Light sits at series1 L .5753 against series2 L .6708 -
 * a ΔL of .096 that gives the orange materially more visual weight than the blue, and leaves
 * series2 at 3.06:1, close to the floor. A candidate replacement is #c74b13 (L .5742, matching
 * series1 to .001, 4.5:1 on the card, hue unchanged so CVD separation should hold) - but that
 * MUST go back through the palette validator before it ships. Recording it rather than
 * inheriting the defect silently.
 *
 * Everything else is read from the CSS custom properties at runtime. Eight of the sixteen
 * values here used to be literal duplicates of index.css tokens - two files guaranteed to drift
 * the moment either was edited - and two were cross-wired to the WRONG mode's --text-muted, so
 * light-mode tick labels rendered visibly heavier than every other muted string on the page.
 * Reading from the cascade makes that class of bug structurally impossible.
 */
const SERIES_HEX = {
  // Categorical slot 1 / slot 2. Blue vs orange, not green vs red: green/red are reserved
  // for status (a credit or debit amount), and a colour must mean one thing per screen.
  light: { series1: '#2a78d6', series2: '#eb6834' },
  dark: { series1: '#3987e5', series2: '#d95926' },
}

// SVG attributes have no notion of "unset": an empty string would paint a stroke as black
// rather than falling back, so a resolved value is guaranteed here even in the window before
// the stylesheet has applied.
const FALLBACK = { light: '#94a3b8', dark: '#64748b' }

const readVar = (name, isDark) =>
  getComputedStyle(document.documentElement).getPropertyValue(name).trim() ||
  FALLBACK[isDark ? 'dark' : 'light']

let cache = { key: null, value: null }

export function chartColors(isDark) {
  const key = isDark ? 'dark' : 'light'
  // Memoised per theme. ThemeContext flips `.dark` in a layout effect, i.e. before this ever
  // runs during a render, so the values read here always belong to the theme being painted.
  if (cache.key === key) return cache.value

  const value = {
    ...SERIES_HEX[key],
    grid: readVar('--surface-border', isDark),
    // Tick labels are small text and belong at 4.5:1, so they read --text-muted, never the
    // decorative --text-faint tier.
    axis: readVar('--text-muted', isDark),
    textMuted: readVar('--text-muted', isDark),
    text: readVar('--text-primary', isDark),
    // The tooltip floats, so it sits on the overlay tier; the charts themselves render on the
    // card. Keeping the two separate is what fixes the mismatched halo around an active dot.
    tooltipBg: readVar('--surface-overlay', isDark),
    tooltipBorder: readVar('--surface-border', isDark),
    surface: readVar('--surface-card', isDark),
    cursor: readVar('--surface-border-strong', isDark),
  }

  cache = { key, value }
  return value
}

/** Series labels live next to their colour so the legend and the marks can never drift apart. */
export const SERIES = {
  income: { key: 'income', label: 'Money in', slot: 'series1' },
  spend: { key: 'spend', label: 'Money out', slot: 'series2' },
  credit: { key: 'credit', label: 'Credits', slot: 'series1' },
  debit: { key: 'debit', label: 'Debits', slot: 'series2' },
}

/** Recessive axis styling - the data should be the loudest thing in the frame. */
export function axisProps(colors) {
  return {
    stroke: colors.axis,
    tick: { fill: colors.textMuted, fontSize: 11 },
    tickLine: false,
    axisLine: false,
  }
}
