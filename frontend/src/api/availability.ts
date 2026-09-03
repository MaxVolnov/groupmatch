import { api } from './axios'
import { IS_MOCK, mockApi } from './mock'
import type {
  AvailabilityBulkClearRequest,
  AvailabilityBulkClearResponse,
  AvailabilityRequest,
  AvailabilityResponse,
  AvailabilitySeriesRequest,
  AvailabilityRetimeResponse,
  AvailabilitySeriesResponse,
  AvailabilityTimeRequest,
  HeatmapResponse,
} from '@/types'

export const availabilityApi = {
  addSlot: (groupId: string, data: AvailabilityRequest): Promise<AvailabilityResponse> =>
    IS_MOCK
      ? mockApi.availability.addSlot(groupId, data)
      : api.post<AvailabilityResponse>(`/groups/${groupId}/availability`, data).then((r) => r.data),

  addSeries: (groupId: string, data: AvailabilitySeriesRequest): Promise<AvailabilitySeriesResponse> =>
    IS_MOCK
      ? mockApi.availability.addSeries(groupId, data)
      : api
          .post<AvailabilitySeriesResponse>(`/groups/${groupId}/availability/series`, data)
          .then((r) => r.data),

  /** Правка времени одного слота. Слот при этом выпадает из своей серии. */
  retimeSlot: (slotId: string, data: AvailabilityTimeRequest): Promise<AvailabilityResponse> =>
    IS_MOCK
      ? mockApi.availability.retimeSlot(slotId, data)
      : api.patch<AvailabilityResponse>(`/availability/${slotId}`, data).then((r) => r.data),

  /** Правка времени всей серии слота. Даты у слотов остаются свои. */
  retimeSeries: (slotId: string, data: AvailabilityTimeRequest): Promise<AvailabilityRetimeResponse> =>
    IS_MOCK
      ? mockApi.availability.retimeSeries(slotId, data)
      : api
          .patch<AvailabilityRetimeResponse>(`/availability/${slotId}/series`, data)
          .then((r) => r.data),

  /**
   * Удаление с областью действия. Путь плоский, без группы: идентификатор
   * слота уникален глобально, и группа выводится из него на сервере.
   */
  deleteSlotScoped: (slotId: string, scope: 'single' | 'series'): Promise<void> =>
    IS_MOCK
      ? mockApi.availability.deleteSlotScoped(slotId, scope)
      : api.delete(`/availability/${slotId}`, { params: { scope } }).then(() => undefined),

  /**
   * Массовая очистка. Тело у DELETE намеренное — так его принимает бэкенд;
   * axios передаёт тело через `data`.
   */
  bulkClear: (
    groupId: string,
    data: AvailabilityBulkClearRequest,
  ): Promise<AvailabilityBulkClearResponse> =>
    IS_MOCK
      ? mockApi.availability.bulkClear(groupId, data)
      : api
          .delete<AvailabilityBulkClearResponse>(`/groups/${groupId}/availability/bulk`, { data })
          .then((r) => r.data),

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
