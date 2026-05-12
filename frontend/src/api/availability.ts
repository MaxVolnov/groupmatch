import { api } from './axios'
import { IS_MOCK, mockApi } from './mock'
import type { AvailabilityRequest, AvailabilityResponse, HeatmapResponse } from '@/types'

export const availabilityApi = {
  addSlot: (groupId: string, data: AvailabilityRequest): Promise<AvailabilityResponse> =>
    IS_MOCK
      ? mockApi.availability.addSlot(groupId, data)
      : api.post<AvailabilityResponse>(`/groups/${groupId}/availability`, data).then((r) => r.data),

  mySlots: (groupId: string): Promise<AvailabilityResponse[]> =>
    IS_MOCK
      ? mockApi.availability.mySlots(groupId)
      : api.get<AvailabilityResponse[]>(`/groups/${groupId}/availability/my`).then((r) => r.data),

  updateSlot: (groupId: string, slotId: string, data: AvailabilityRequest): Promise<AvailabilityResponse> =>
    IS_MOCK
      ? mockApi.availability.updateSlot(groupId, slotId, data)
      : api
          .put<AvailabilityResponse>(`/groups/${groupId}/availability/${slotId}`, data)
          .then((r) => r.data),

  deleteSlot: (groupId: string, slotId: string): Promise<void> =>
    IS_MOCK
      ? mockApi.availability.deleteSlot(groupId, slotId)
      : api.delete(`/groups/${groupId}/availability/${slotId}`).then(() => undefined),

  heatmap: (groupId: string, from?: string, to?: string, granularityMinutes?: number): Promise<HeatmapResponse> => {
    if (IS_MOCK) return mockApi.availability.heatmap(groupId, from ?? new Date().toISOString())
    const params: Record<string, string | number> = {}
    if (from) params.from = from
    if (to) params.to = to
    if (granularityMinutes) params.granularityMinutes = granularityMinutes
    return api.get<HeatmapResponse>(`/groups/${groupId}/availability/heatmap`, { params }).then((r) => r.data)
  },
}
