import axios from 'axios'
import { useAuthStore } from '@/store/auth'

export const api = axios.create({
  baseURL: `${import.meta.env.VITE_API_URL ?? 'http://localhost:8080'}/api/v1`,
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
    console.warn('[Interceptor] 401 detected, url:', original.url)

    if (refreshing) {
      console.warn('[Interceptor] Already refreshing, queuing request')
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
      console.warn('[Interceptor] Refresh success, retrying queued requests:', queue.length)
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
      console.warn('[Interceptor] Refresh failed, status:', errStatus)
      if (errStatus === 401 || errStatus === 403) {
        console.warn('[Interceptor] Logging out due to 401/403 on refresh')
        useAuthStore.getState().logout()
        window.location.replace(`${window.location.origin}/signin`)
      }
      return Promise.reject(e)
    }
  },
)
