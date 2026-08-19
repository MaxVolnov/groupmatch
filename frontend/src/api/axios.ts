import axios from 'axios'
import { useAuthStore } from '@/store/auth'

/**
 * Адрес API. Дефолта намеренно нет.
 *
 * Раньше здесь стояло `?? 'http://localhost:8080'`, и прод-сборка без
 * VITE_API_URL выглядела совершенно исправной: страницы открывались, а каждый
 * запрос молча уходил на localhost пользователя. Отсутствие переменной — это
 * ошибка сборки, и узнавать о ней надо на старте, а не по жалобам.
 *
 * В dev localhost остаётся дефолтом: там он верен.
 */
function resolveApiBaseUrl(): string {
  const configured = import.meta.env.VITE_API_URL
  if (configured) return configured
  if (import.meta.env.DEV) return 'http://localhost:8080'
  throw new Error(
    'VITE_API_URL не задана. Значение подставляется на этапе сборки: ' +
      'задайте переменную в настройках проекта и пересоберите фронтенд ' +
      '(redeploy, не перезапуск).',
  )
}

/** Отладочный вывод: включён только в dev-сборке. */
const debug = (...args: unknown[]) => {
  if (import.meta.env.DEV) console.warn(...args)
}

export const api = axios.create({
  baseURL: `${resolveApiBaseUrl()}/api/v1`,
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true,
})

// Attach access token to every request except /auth/ endpoints
api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  const isAuthEndpoint = config.url?.includes('/auth/')
  if (token && !isAuthEndpoint) config.headers.Authorization = `Bearer ${token}`
  return config
})

// On 401 try token refresh once, then sign out
let refreshing = false
let queue: Array<{ resolve: (t: string) => void; reject: (e: unknown) => void }> = []

api.interceptors.response.use(
  (r) => r,
  async (error) => {
    const original = error.config
    const status = error.response?.status

    // Не обрабатываем: не 401, уже ретраили, или это сам auth-запрос
    if (status !== 401 || original._retry || original.url?.includes('/auth/')) {
      return Promise.reject(error)
    }

    original._retry = true
    debug('[Interceptor] 401 detected, url:', original.url)

    if (refreshing) {
      debug('[Interceptor] Already refreshing, queuing request')
      return new Promise((resolve, reject) => {
        queue.push({ resolve, reject })
      }).then((token) => {
        original.headers.Authorization = `Bearer ${token}`
        return api(original)
      })
    }

    refreshing = true
    try {
      const newToken = await useAuthStore.getState().refresh()
      debug('[Interceptor] Refresh success, retrying queued requests:', queue.length)
      queue.forEach((q) => q.resolve(newToken))
      queue = []
      refreshing = false  // сбрасываем ПОСЛЕ resolve очереди
      original.headers.Authorization = `Bearer ${newToken}`
      return api(original)
    } catch (e) {
      queue.forEach((q) => q.reject(e))
      queue = []
      refreshing = false
      const errStatus = (e as { response?: { status?: number } })?.response?.status
      debug('[Interceptor] Refresh failed, status:', errStatus)
      if (errStatus === 401 || errStatus === 403) {
        debug('[Interceptor] Logging out due to 401/403 on refresh')
        useAuthStore.getState().logout()
        window.location.replace(`${window.location.origin}/signin`)
      }
      return Promise.reject(e)
    }
  },
)
