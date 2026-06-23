import { api } from './axios'
import type { AdminUsersPage } from '@/types/admin'

export const adminApi = {
  getUsers: (search?: string, page = 0, size = 20): Promise<AdminUsersPage> =>
    api.get('/admin/users', { params: { search, page, size } }).then((r) => r.data),

  banUser: (id: string, reason: string): Promise<void> =>
    api.patch(`/admin/users/${id}/ban`, { reason }).then(() => undefined),

  unbanUser: (id: string): Promise<void> =>
    api.patch(`/admin/users/${id}/unban`).then(() => undefined),
}
