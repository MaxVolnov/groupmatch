import { api } from './axios'
import { IS_MOCK, mockApi } from './mock'
import type { GroupRequest, GroupResponse, MemberResponse } from '@/types'

export const groupsApi = {
  list: (): Promise<GroupResponse[]> =>
    IS_MOCK ? mockApi.groups.list() : api.get<GroupResponse[]>('/groups').then((r) => r.data),

  get: (id: string): Promise<GroupResponse> =>
    IS_MOCK ? mockApi.groups.get(id) : api.get<GroupResponse>(`/groups/${id}`).then((r) => r.data),

  create: (data: GroupRequest): Promise<GroupResponse> =>
    IS_MOCK ? mockApi.groups.create(data) : api.post<GroupResponse>('/groups', data).then((r) => r.data),

  update: (id: string, data: GroupRequest): Promise<GroupResponse> =>
    IS_MOCK
      ? mockApi.groups.update(id, data)
      : api.put<GroupResponse>(`/groups/${id}`, data).then((r) => r.data),

  delete: (id: string): Promise<void> =>
    IS_MOCK ? mockApi.groups.delete(id) : api.delete(`/groups/${id}`).then(() => undefined),

  members: (id: string): Promise<MemberResponse[]> =>
    IS_MOCK
      ? mockApi.groups.members(id)
      : api.get<MemberResponse[]>(`/groups/${id}/members`).then((r) => r.data),

  addMember: (id: string, userId: string): Promise<MemberResponse> =>
    IS_MOCK
      ? mockApi.groups.addMember(id, userId)
      : api.post<MemberResponse>(`/groups/${id}/members`, { userId }).then((r) => r.data),

  removeMember: (id: string, userId: string): Promise<void> =>
    IS_MOCK
      ? mockApi.groups.removeMember(id, userId)
      : api.delete(`/groups/${id}/members/${userId}`).then(() => undefined),
}
