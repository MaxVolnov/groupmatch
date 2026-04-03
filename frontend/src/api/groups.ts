import { api } from './axios'
import type { GroupRequest, GroupResponse, MemberResponse } from '@/types'

export const groupsApi = {
  list: () => api.get<GroupResponse[]>('/groups').then((r) => r.data),

  get: (id: string) => api.get<GroupResponse>(`/groups/${id}`).then((r) => r.data),

  create: (data: GroupRequest) =>
    api.post<GroupResponse>('/groups', data).then((r) => r.data),

  update: (id: string, data: GroupRequest) =>
    api.put<GroupResponse>(`/groups/${id}`, data).then((r) => r.data),

  delete: (id: string) => api.delete(`/groups/${id}`),

  members: (id: string) =>
    api.get<MemberResponse[]>(`/groups/${id}/members`).then((r) => r.data),

  addMember: (id: string, userId: string) =>
    api.post<MemberResponse>(`/groups/${id}/members`, { userId }).then((r) => r.data),

  removeMember: (id: string, userId: string) =>
    api.delete(`/groups/${id}/members/${userId}`),
}
