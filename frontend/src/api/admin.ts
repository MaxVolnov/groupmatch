import { api } from './axios'
import type { AdminUsersPage, AdminFeedbackPage } from '@/types/admin'

export const adminApi = {
  getUsers: (search?: string, page = 0, size = 20): Promise<AdminUsersPage> =>
    api.get('/admin/users', { params: { search, page, size } }).then((r) => r.data),

  banUser: (id: string, reason: string): Promise<void> =>
    api.patch(`/admin/users/${id}/ban`, { reason }).then(() => undefined),

  unbanUser: (id: string): Promise<void> =>
    api.patch(`/admin/users/${id}/unban`).then(() => undefined),

  getFeedback: (category?: string, resolved?: boolean, page = 0, size = 20): Promise<AdminFeedbackPage> =>
    api.get('/admin/feedback', { params: { category, resolved, page, size } }).then((r) => r.data),

  resolveFeedback: (id: string): Promise<void> =>
    api.patch(`/admin/feedback/${id}/resolve`).then(() => undefined),

  unresolveFeedback: (id: string): Promise<void> =>
    api.patch(`/admin/feedback/${id}/unresolve`).then(() => undefined),
}
