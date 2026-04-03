import { api } from './axios'
import type { CreateInviteRequest, InviteResponse } from '@/types'

export const invitesApi = {
  create: (groupId: string, data: CreateInviteRequest) =>
    api.post<InviteResponse>(`/groups/${groupId}/invites`, data).then((r) => r.data),

  list: (groupId: string) =>
    api.get<InviteResponse[]>(`/groups/${groupId}/invites`).then((r) => r.data),

  revoke: (groupId: string, inviteId: string) =>
    api.delete(`/groups/${groupId}/invites/${inviteId}`),

  join: (token: string) =>
    api.post<InviteResponse>(`/invites/${token}/join`).then((r) => r.data),
}
