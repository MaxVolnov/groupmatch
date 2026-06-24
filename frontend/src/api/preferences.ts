import { api } from './axios'
import type { NotificationPreferences } from '@/types'

export const preferencesApi = {
  get: (): Promise<NotificationPreferences> =>
    api.get('/me/notification-preferences').then((r) => r.data),

  update: (data: Partial<NotificationPreferences>): Promise<NotificationPreferences> =>
    api.patch('/me/notification-preferences', data).then((r) => r.data),
}
