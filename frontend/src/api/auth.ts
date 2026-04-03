import axios from 'axios'
import type { AuthResponse, SigninRequest, SignupRequest, UserResponse } from '@/types'

const base = axios.create({
  baseURL: '/api/v1/auth',
  headers: { 'Content-Type': 'application/json' },
})

export const authApi = {
  signup: (data: SignupRequest) =>
    base.post<UserResponse>('/signup', data).then((r) => r.data),

  signin: (data: SigninRequest) =>
    base.post<AuthResponse>('/signin', data).then((r) => r.data),

  refresh: (refreshToken: string) =>
    base.post<AuthResponse>('/refresh', { refreshToken }).then((r) => r.data),

  logout: (accessToken: string, refreshToken: string) =>
    base.post<void>(
      '/logout',
      { refreshToken },
      { headers: { Authorization: `Bearer ${accessToken}` } },
    ),
}
