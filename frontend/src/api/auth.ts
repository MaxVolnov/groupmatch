import { api } from './axios'
import { IS_MOCK, mockApi } from './mock'
import type { AuthResponse, SigninRequest, SignupRequest, UserResponse } from '@/types'

export const authApi = {
  signup: (data: SignupRequest): Promise<UserResponse> =>
    IS_MOCK
      ? mockApi.auth.signup(data)
      : api.post<UserResponse>('/auth/signup', data).then((r) => r.data),

  signin: (data: SigninRequest): Promise<AuthResponse> =>
    IS_MOCK
      ? mockApi.auth.signin(data)
      : api.post<AuthResponse>('/auth/signin', data).then((r) => r.data),

  guest: (data: { displayName: string }): Promise<AuthResponse> =>
    IS_MOCK
      ? mockApi.auth.guest(data)
      : api.post<AuthResponse>('/auth/guest', data).then((r) => r.data),

  refresh: (refreshToken: string): Promise<AuthResponse> =>
    IS_MOCK
      ? mockApi.auth.refresh()
      : api.post<AuthResponse>('/auth/refresh', { refreshToken }).then((r) => r.data),

  logout: (accessToken: string, refreshToken: string): Promise<void> =>
    IS_MOCK
      ? mockApi.auth.logout()
      : api
          .post<void>('/auth/logout', { refreshToken }, { headers: { Authorization: `Bearer ${accessToken}` } })
          .then(() => undefined),
}
