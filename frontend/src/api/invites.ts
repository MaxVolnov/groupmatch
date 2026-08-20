import { api } from './axios'
import { IS_MOCK, mockApi } from './mock'
import type { CreateInviteRequest, InvitePreview, InviteResponse } from '@/types'

export const invitesApi = {
  create: (groupId: string, data: CreateInviteRequest): Promise<InviteResponse> =>
    IS_MOCK
      ? mockApi.invites.create(groupId, data)
      : api.post<InviteResponse>(`/groups/${groupId}/invites`, data).then((r) => r.data),

  list: (groupId: string): Promise<InviteResponse[]> =>
    IS_MOCK
      ? mockApi.invites.list(groupId)
      : api.get<InviteResponse[]>(`/groups/${groupId}/invites`).then((r) => r.data),

  revoke: (groupId: string, inviteId: string): Promise<void> =>
    IS_MOCK
      ? mockApi.invites.revoke(groupId, inviteId)
      : api.delete(`/groups/${groupId}/invites/${inviteId}`).then(() => undefined),

  /**
   * Публичное превью приглашения: кто зовёт и куда. Без авторизации —
   * экран /join/{token} открывается до входа.
   *
   * Невалидный токен приходит как 200 с valid: false, а не ошибкой, поэтому
   * catch здесь не нужен: обрабатывать надо поле, а не исключение.
   */
  preview: (token: string): Promise<InvitePreview> =>
    IS_MOCK
      ? mockApi.invites.preview(token)
      : api.get<InvitePreview>(`/invites/${token}`).then((r) => r.data),

  /** Занято ли имя в группе — для подсказки про второй гостевой аккаунт. */
  nameTaken: (token: string, name: string): Promise<boolean> =>
    IS_MOCK
      ? Promise.resolve(false)
      : api
          .get<{ taken: boolean }>(`/invites/${token}/name-taken`, { params: { name } })
          .then((r) => r.data.taken),

  join: (token: string): Promise<InviteResponse> =>
    IS_MOCK
      ? mockApi.invites.join(token)
      : api.post<InviteResponse>(`/invites/${token}/join`).then((r) => r.data),
}
