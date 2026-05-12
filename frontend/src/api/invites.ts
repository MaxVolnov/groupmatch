import { api } from './axios'
import { IS_MOCK, mockApi } from './mock'
import type { CreateInviteRequest, InviteResponse } from '@/types'

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

  join: (token: string): Promise<InviteResponse> =>
    IS_MOCK
      ? mockApi.invites.join(token)
      : api.post<InviteResponse>(`/invites/${token}/join`).then((r) => r.data),
}
