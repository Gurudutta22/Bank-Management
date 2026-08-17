import { useCallback, useEffect, useRef, useState } from 'react'

/**
 * Minimal data-fetching hook: loading / error / data plus a manual refetch.
 *
 * Enough for this app's needs without pulling in React Query. The `alive` ref is the important
 * part - it stops a response that arrives after the component unmounts from calling setState,
 * which React warns about and which leaks memory on fast route changes.
 *
 * @param fetcher  async function returning the data
 * @param deps     dependency list; the request re-runs when these change
 */
export function useApi(fetcher, deps = []) {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const alive = useRef(true)

  const fetcherRef = useRef(fetcher)
  fetcherRef.current = fetcher

  const run = useCallback(async ({ quiet = false } = {}) => {
    if (!quiet) setLoading(true)
    setError(null)
    try {
      const result = await fetcherRef.current()
      if (alive.current) setData(result)
      return result
    } catch (err) {
      if (alive.current) setError(err)
      return null
    } finally {
      if (alive.current) setLoading(false)
    }
  }, [])

  useEffect(() => {
    alive.current = true
    run()
    return () => {
      alive.current = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)

  return { data, loading, error, refetch: run, setData }
}

/** Debounces a rapidly-changing value - used so a search box does not fire a request per keystroke. */
export function useDebounced(value, delay = 350) {
  const [debounced, setDebounced] = useState(value)

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay)
    return () => clearTimeout(timer)
  }, [value, delay])

  return debounced
}
