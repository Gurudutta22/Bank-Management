import axios from 'axios'

/**
 * The single axios instance every request goes through.
 *
 * Base URL is relative ('/api/v1'), which means:
 *  - in dev, Vite proxies /api to localhost:8080 (see vite.config.js)
 *  - in production, nginx proxies /api to the backend container
 * Neither case needs a rebuild or an environment variable baked into the bundle.
 */
const api = axios.create({
  baseURL: '/api/v1',
  headers: { 'Content-Type': 'application/json' },
  timeout: 20000,
})

const ACCESS_KEY = 'novabank.accessToken'
const REFRESH_KEY = 'novabank.refreshToken'
const USER_KEY = 'novabank.user'

export const tokenStore = {
  access: () => localStorage.getItem(ACCESS_KEY),
  refresh: () => localStorage.getItem(REFRESH_KEY),
  user: () => {
    try {
      return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
    } catch {
      return null
    }
  },
  save: ({ accessToken, refreshToken, user }) => {
    if (accessToken) localStorage.setItem(ACCESS_KEY, accessToken)
    if (refreshToken) localStorage.setItem(REFRESH_KEY, refreshToken)
    if (user) localStorage.setItem(USER_KEY, JSON.stringify(user))
  },
  clear: () => {
    localStorage.removeItem(ACCESS_KEY)
    localStorage.removeItem(REFRESH_KEY)
    localStorage.removeItem(USER_KEY)
  },
}

// Attach the bearer token to every outgoing request.
api.interceptors.request.use((config) => {
  const token = tokenStore.access()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/*
 * Silent token refresh.
 *
 * The access token only lives 15 minutes, so a user reading a page will hit a 401 mid-session.
 * Rather than bounce them to the login screen, the first 401 triggers a refresh and the original
 * request is replayed.
 *
 * `refreshPromise` is the important detail: if six requests 401 at once (a dashboard fires several
 * in parallel), all six await the *same* refresh call. Without it, six concurrent refreshes would
 * race, and because the server rotates refresh tokens, five of them would be rejected and log the
 * user out.
 */
let refreshPromise = null
const listeners = new Set()

/** Lets AuthContext react when the session ends for good. */
export function onSessionExpired(callback) {
  listeners.add(callback)
  return () => listeners.delete(callback)
}

function endSession() {
  tokenStore.clear()
  listeners.forEach((cb) => cb())
}

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const { config, response } = error

    if (!response) {
      return Promise.reject(
        new ApiError('Cannot reach the server. Check that the backend is running.', 0, 'NETWORK_ERROR'),
      )
    }

    const isAuthCall = config?.url?.includes('/auth/login') || config?.url?.includes('/auth/refresh')

    if (response.status === 401 && !config._retried && !isAuthCall) {
      config._retried = true

      const storedRefresh = tokenStore.refresh()
      if (!storedRefresh) {
        endSession()
        return Promise.reject(toApiError(response))
      }

      try {
        refreshPromise =
          refreshPromise ||
          axios
            .post('/api/v1/auth/refresh', { refreshToken: storedRefresh })
            .then((res) => {
              tokenStore.save(res.data)
              return res.data.accessToken
            })
            .finally(() => {
              refreshPromise = null
            })

        const freshToken = await refreshPromise
        config.headers.Authorization = `Bearer ${freshToken}`
        return api(config)
      } catch {
        endSession()
        return Promise.reject(
          new ApiError('Your session has expired. Please sign in again.', 401, 'SESSION_EXPIRED'),
        )
      }
    }

    return Promise.reject(toApiError(response))
  },
)

/** Normalised error the UI can render without knowing about axios. */
export class ApiError extends Error {
  constructor(message, status, code, violations) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.violations = violations || []
  }
}

/**
 * Turns the backend's ApiError JSON into an ApiError instance.
 * Field-level violations are surfaced separately so forms can highlight the offending input.
 */
function toApiError(response) {
  const body = response?.data || {}
  const message =
    body.violations?.length > 0
      ? body.violations.map((v) => v.message).join(' ')
      : body.message || 'Something went wrong. Please try again.'

  return new ApiError(message, response?.status, body.code, body.violations)
}

export default api
