import { createContext, useCallback, useContext, useLayoutEffect, useMemo, useRef, useState } from 'react'

const ThemeContext = createContext(null)
const STORAGE_KEY = 'novabank.theme'

/**
 * Light/dark switching.
 *
 * Reads the saved choice first and falls back to the OS preference, so a user who runs their
 * machine in light mode is not blinded by a dark app on first visit. The actual switch is one
 * class on <html>; every colour follows from the CSS variables in index.css.
 */
export function ThemeProvider({ children }) {
  const [theme, setTheme] = useState(() => {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved === 'light' || saved === 'dark') return saved
    return window.matchMedia?.('(prefers-color-scheme: light)').matches ? 'light' : 'dark'
  })

  const first = useRef(true)

  // useLayoutEffect, not useEffect: this runs BEFORE the browser paints and before children
  // read anything from the cascade. Chart colours are pulled from computed custom properties,
  // and with a passive effect the class flip landed after the children had already rendered,
  // so every toggle painted one frame of charts in the outgoing theme's colours.
  useLayoutEffect(() => {
    const root = document.documentElement

    // `theme-anim` opens a short window in which index.css crossfades colour, box-shadow and
    // the two @property-registered shell-glow colours. It is scoped to the switch so the 420ms
    // transition never slows an ordinary hover. Skipped on mount - there is nothing to fade
    // from, and the pre-paint script in index.html has already set the correct class.
    if (!first.current) root.classList.add('theme-anim')
    first.current = false

    root.classList.toggle('dark', theme === 'dark')
    root.style.colorScheme = theme
    localStorage.setItem(STORAGE_KEY, theme)

    // 460ms > --dur-3 (420ms), so the class outlives the transition it enables.
    const timer = setTimeout(() => root.classList.remove('theme-anim'), 460)
    return () => clearTimeout(timer)
  }, [theme])

  const toggle = useCallback(() => setTheme((t) => (t === 'dark' ? 'light' : 'dark')), [])
  const value = useMemo(() => ({ theme, toggle, isDark: theme === 'dark' }), [theme, toggle])

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}

export function useTheme() {
  const context = useContext(ThemeContext)
  if (!context) throw new Error('useTheme must be used inside a <ThemeProvider>')
  return context
}
