import axios from 'axios'
import { IS_MOCK, mockApi } from './mock'
import type { AuthResponse, SigninRequest, SignupRequest, UserResponse } from '@/types'

const base = axios.create({
  baseURL: '/api/v1/auth',
  headers: { 'Content-Type': 'application/json' },
})

export const authApi = {
  signup: (data: SignupRequest): Promise<UserResponse> =>
    IS_MOCK
      ? mockApi.auth.signup(data)
      : base.post<UserResponse>('/signup', data).then((r) => r.data),

  signin: (data: SigninRequest): Promise<AuthResponse> =>
    IS_MOCK
      ? mockApi.auth.signin(data)
      : base.post<AuthResponse>('/signin', data).then((r) => r.data),

  refresh: (refreshToken: string): Promise<AuthResponse> =>
    IS_MOCK
      ? mockApi.auth.refresh()
      : base.post<AuthResponse>('/refresh', { refreshToken }).then((r) => r.data),

  logout: (accessToken: string, refreshToken: string): Promise<void> =>
    IS_MOCK
      ? mockApi.auth.logout()
      : base.post<void>(
          '/logout',
          { refreshToken },
          { headers: { Authorization: `Bearer ${accessToken}` } },
        ).then(() => undefined),
}
