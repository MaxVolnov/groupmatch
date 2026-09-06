import { api } from './axios'
import { IS_MOCK, mockApi } from './mock'
import type { AccountDeletionPreview, Language, PlanInfoResponse, UserResponse } from '@/types'

export interface UpdateMeRequest {
  displayName?: string
  tzId?: string
  locale?: Language
}

export const meApi = {
  get: (): Promise<UserResponse> =>
    IS_MOCK ? mockApi.me.get() : api.get<UserResponse>('/me').then((r) => r.data),

  update: (data: UpdateMeRequest): Promise<UserResponse> =>
    IS_MOCK ? mockApi.me.update(data) : api.patch<UserResponse>('/me', data).then((r) => r.data),

  getPlanInfo: (): Promise<PlanInfoResponse> =>
    api.get<PlanInfoResponse>('/me/plan').then((r) => r.data),

  /** Что произойдёт с группами при удалении. Считает сервер: у клиента нет их состава. */
  deletionPreview: (): Promise<AccountDeletionPreview> =>
    api.get<AccountDeletionPreview>('/me/deletion-preview').then((r) => r.data),

  /**
   * Удаление собственного аккаунта. Пароль обязателен для всех, кроме гостей —
   * им его никогда не выдавали.
   */
  deleteAccount: (password?: string): Promise<void> =>
    api.delete('/me', { data: password ? { password } : {} }).then(() => undefined),
}
