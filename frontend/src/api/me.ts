import { api } from './axios'
import { IS_MOCK, mockApi } from './mock'
import type { UserResponse } from '@/types'

export interface UpdateMeRequest {
  displayName?: string
  tzId?: string
}

export const meApi = {
  get: (): Promise<UserResponse> =>
    IS_MOCK ? mockApi.me.get() : api.get<UserResponse>('/me').then((r) => r.data),

  update: (data: UpdateMeRequest): Promise<UserResponse> =>
    IS_MOCK ? mockApi.me.update(data) : api.patch<UserResponse>('/me', data).then((r) => r.data),
}
