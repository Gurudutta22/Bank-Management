import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { authApi } from '../api/endpoints'
import { onSessionExpired, tokenStore } from '../api/client'

const AuthContext = createContext(null)

/**
 * Holds the session for the whole app.
 *
 * The user object is hydrated from localStorage on first render so a page refresh does not flash
 * the login screen, then revalidated against /auth/me in the background. If the token was revoked
 * server-side while the tab was closed, that call fails and the session is cleared.
 */
export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => tokenStore.user())
  const [loading, setLoading] = useState(() => Boolean(tokenStore.access()))

  const signOut = useCallback(async () => {
    const refreshToken = tokenStore.refresh()
    try {
      if (refreshToken) await authApi.logout(refreshToken)
    } catch {
      // A failed logout call must never trap the user in a signed-in state.
    } finally {
      tokenStore.clear()
      setUser(null)
    }
  }, [])

  // The axios layer tells us when a refresh failed for good.
  useEffect(() => onSessionExpired(() => setUser(null)), [])

  useEffect(() => {
    if (!tokenStore.access()) {
      setLoading(false)
      return
    }
    let cancelled = false
    authApi
      .me()
      .then((profile) => {
        if (cancelled) return
        tokenStore.save({ user: profile })
        setUser(profile)
      })
      .catch(() => {
        if (!cancelled) {
          tokenStore.clear()
          setUser(null)
        }
      })
      .finally(() => !cancelled && setLoading(false))

    return () => {
      cancelled = true
    }
  }, [])

  const signIn = useCallback(async (credentials) => {
    const data = await authApi.login(credentials)
    tokenStore.save(data)
    setUser(data.user)
    return data.user
  }, [])

  const register = useCallback(async (payload) => {
    const data = await authApi.register(payload)
    tokenStore.save(data)
    setUser(data.user)
    return data.user
  }, [])

  const value = useMemo(
    () => ({
      user,
      loading,
      signIn,
      register,
      signOut,
      updateUser: (patch) => {
        setUser((prev) => {
          const next = { ...prev, ...patch }
          tokenStore.save({ user: next })
          return next
        })
      },
      isAdmin: user?.role === 'ADMIN',
      isAuthenticated: Boolean(user),
    }),
    [user, loading, signIn, register, signOut],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used inside an <AuthProvider>')
  }
  return context
}
