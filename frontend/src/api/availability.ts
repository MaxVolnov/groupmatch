import { api } from './axios'
import type { AvailabilityRequest, AvailabilityResponse, HeatmapResponse } from '@/types'

export const availabilityApi = {
  addSlot: (groupId: string, data: AvailabilityRequest) =>
    api.post<AvailabilityResponse>(`/groups/${groupId}/availability`, data).then((r) => r.data),

  mySlots: (groupId: string) =>
    api
      .get<AvailabilityResponse[]>(`/groups/${groupId}/availability/my`)
      .then((r) => r.data),

  updateSlot: (groupId: string, slotId: string, data: AvailabilityRequest) =>
    api
      .put<AvailabilityResponse>(`/groups/${groupId}/availability/${slotId}`, data)
      .then((r) => r.data),

  deleteSlot: (groupId: string, slotId: string) =>
    api.delete(`/groups/${groupId}/availability/${slotId}`),

  heatmap: (groupId: string, from?: string, to?: string, granularityMinutes?: number) => {
    const params: Record<string, string | number> = {}
    if (from) params.from = from
    if (to) params.to = to
    if (granularityMinutes) params.granularityMinutes = granularityMinutes
    return api.get<HeatmapResponse>(`/groups/${groupId}/availability/heatmap`, { params }).then((r) => r.data)
  },
}
