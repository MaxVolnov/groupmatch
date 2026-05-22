import { api } from './axios'
import type { MeetingRequest, MeetingResponse } from '@/types'

export const meetingsApi = {
  list: (groupId: string) =>
    api.get<MeetingResponse[]>(`/groups/${groupId}/meetings`).then((r) => r.data),

  get: (groupId: string, meetingId: string) =>
    api.get<MeetingResponse>(`/groups/${groupId}/meetings/${meetingId}`).then((r) => r.data),

  create: (groupId: string, data: MeetingRequest) =>
    api.post<MeetingResponse>(`/groups/${groupId}/meetings`, data).then((r) => r.data),

  update: (groupId: string, meetingId: string, data: MeetingRequest) =>
    api.put<MeetingResponse>(`/groups/${groupId}/meetings/${meetingId}`, data).then((r) => r.data),

  delete: (groupId: string, meetingId: string) =>
    api.delete(`/groups/${groupId}/meetings/${meetingId}`),

  exportIcs: (groupId: string, meetingId: string) =>
    api.get<string>(`/groups/${groupId}/meetings/${meetingId}/export.ics`, {
      responseType: 'text',
    }).then((r) => r.data),
}
