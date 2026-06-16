import { api } from './axios'
import { IS_MOCK, mockApi } from './mock'
import type { MeetingRequest, MeetingResponse } from '@/types'

export const meetingsApi = {
  list: (groupId: string): Promise<MeetingResponse[]> =>
    IS_MOCK
      ? mockApi.meetings.list(groupId)
      : api.get<MeetingResponse[]>(`/groups/${groupId}/meetings`).then((r) => r.data),

  get: (groupId: string, meetingId: string): Promise<MeetingResponse> =>
    IS_MOCK
      ? mockApi.meetings.get(groupId, meetingId)
      : api.get<MeetingResponse>(`/groups/${groupId}/meetings/${meetingId}`).then((r) => r.data),

  create: (groupId: string, data: MeetingRequest): Promise<MeetingResponse> =>
    IS_MOCK
      ? mockApi.meetings.create(groupId, data)
      : api.post<MeetingResponse>(`/groups/${groupId}/meetings`, data).then((r) => r.data),

  update: (groupId: string, meetingId: string, data: MeetingRequest): Promise<MeetingResponse> =>
    IS_MOCK
      ? mockApi.meetings.update(groupId, meetingId, data)
      : api.put<MeetingResponse>(`/groups/${groupId}/meetings/${meetingId}`, data).then((r) => r.data),

  delete: (groupId: string, meetingId: string): Promise<void> =>
    IS_MOCK
      ? mockApi.meetings.delete(groupId, meetingId)
      : api.delete(`/groups/${groupId}/meetings/${meetingId}`).then(() => undefined),

  exportIcs: (groupId: string, meetingId: string): Promise<string> =>
    IS_MOCK
      ? Promise.resolve('BEGIN:VCALENDAR\r\nVERSION:2.0\r\nEND:VCALENDAR\r\n')
      : api
          .get<string>(`/groups/${groupId}/meetings/${meetingId}/export.ics`, {
            responseType: 'text',
          })
          .then((r) => r.data),
}
