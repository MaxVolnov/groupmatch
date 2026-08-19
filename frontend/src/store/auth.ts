import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import { authApi } from '@/api/auth'
import type { Plan, Role } from '@/types'

interface AuthState {
  accessToken: string | null
  refreshToken: string | null
  userId: string | null
  email: string | null
  displayName: string | null
  role: Role | null
  plan: Plan | null
  isAuthenticated: boolean
  isGuest: boolean

  login: (accessToken: string, refreshToken: string, displayName?: string) => void
  logout: () => void
  refresh: () => Promise<string>
  setProfile: (userId: string, email: string, displayName: string, role: Role, plan: Plan) => void
  upgradeGuest: (data: { email: string; password: string; displayName: string }) => Promise<void>
}

// Decode a JWT payload without verifying (client-side only)
function decodeJwt(token: string): Record<string, unknown> {
  try {
    const payload = token.split('.')[1]
    return JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')))
  } catch {
    return {}
  }
}

// Module-level mutex so concurrent callers share a single in-flight refresh
let refreshPromise: Promise<string> | null = null

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      accessToken: null,
      refreshToken: null,
      userId: null,
      email: null,
      displayName: null,
      role: null,
      plan: null,
      isAuthenticated: false,
      isGuest: false,

      login: (accessToken, refreshToken, displayName) => {
        const claims = decodeJwt(accessToken)
        set({
          accessToken,
          refreshToken,
          userId: claims.sub as string,
          email: claims.email as string,
          displayName: displayName ?? null,
          role: claims.role as Role,
          plan: claims.plan as Plan,
          isAuthenticated: true,
          isGuest: claims.isGuest === true,
        })
      },

      logout: () => {
        const { accessToken, refreshToken } = get()
        if (accessToken && refreshToken) {
          authApi.logout(accessToken, refreshToken).catch(() => {/* ignore */})
        }
        set({
          accessToken: null,
          refreshToken: null,
          userId: null,
          email: null,
          displayName: null,
          role: null,
          plan: null,
          isAuthenticated: false,
          isGuest: false,
        })
      },

      refresh: async () => {
        // Если refresh уже в процессе — ждём его результата
        if (refreshPromise) return refreshPromise

        const { refreshToken } = get()
        if (!refreshToken) throw new Error('No refresh token')

        refreshPromise = (async () => {
          try {
            const data = await authApi.refresh(refreshToken)
            const claims = decodeJwt(data.accessToken)

            // Синхронная запись в localStorage
            try {
              const stored = JSON.parse(localStorage.getItem('groupmatch-auth') || '{}')
              if (stored.state) {
                stored.state.refreshToken = data.refreshToken
                stored.state.accessToken = data.accessToken
                localStorage.setItem('groupmatch-auth', JSON.stringify(stored))
              }
            } catch {
              // ignore
            }

            set({
              accessToken: data.accessToken,
              refreshToken: data.refreshToken,
              userId: claims.sub as string,
              email: claims.email as string,
              role: claims.role as Role,
              plan: claims.plan as Plan,
              isAuthenticated: true,
              isGuest: claims.isGuest === true,
            })

            return data.accessToken
          } finally {
            refreshPromise = null
          }
        })()

        return refreshPromise
      },

      setProfile: (userId, email, displayName, role, plan) => {
        set({ userId, email, displayName, role, plan })
      },

      upgradeGuest: async (data) => {
        const response = await authApi.upgradeGuest(data)
        const claims = decodeJwt(response.accessToken)
        set({
          accessToken: response.accessToken,
          refreshToken: response.refreshToken,
          userId: claims.sub as string,
          email: claims.email as string,
          displayName: data.displayName,
          role: claims.role as Role,
          plan: claims.plan as Plan,
          isAuthenticated: true,
          isGuest: false,
        })
      },
    }),
    {
      name: 'groupmatch-auth',
      partialize: (s) => ({
        accessToken: s.accessToken,
        refreshToken: s.refreshToken,
        userId: s.userId,
        email: s.email,
        displayName: s.displayName,
        role: s.role,
        plan: s.plan,
        isAuthenticated: s.isAuthenticated,
        isGuest: s.isGuest,
      }),
    },
  ),
)
