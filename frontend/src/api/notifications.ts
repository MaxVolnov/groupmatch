import { api } from './axios'
import type { NotificationResponse } from '@/types'

export const notificationsApi = {
  list: (): Promise<NotificationResponse[]> =>
    api.get<NotificationResponse[]>('/notifications').then((r) => r.data),

  unreadCount: (): Promise<number> =>
    api.get<{ count: number }>('/notifications/unread-count').then((r) => r.data.count),

  markRead: (id: string): Promise<void> =>
    api.patch(`/notifications/${id}/read`).then(() => {}),

  markAllRead: (): Promise<void> =>
    api.patch('/notifications/read-all').then(() => {}),
}
