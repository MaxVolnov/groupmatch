import { api } from './axios'
import type {
  AdminFeedbackPage,
  AdminGroupPage,
  AdminUserFilter,
  AdminUsersPage,
} from '@/types/admin'

export const adminApi = {
  getUsers: (
    search?: string,
    filter: AdminUserFilter = 'ALL',
    page = 0,
    size = 20,
  ): Promise<AdminUsersPage> =>
    api.get('/admin/users', { params: { search, filter, page, size } }).then((r) => r.data),

  changeUserRole: (id: string, role: 'USER' | 'ADMIN'): Promise<void> =>
    api.patch(`/admin/users/${id}/role`, { role }).then(() => undefined),

  changeUserPlan: (id: string, plan: 'FREE' | 'PRO'): Promise<void> =>
    api.patch(`/admin/users/${id}/plan`, { plan }).then(() => undefined),

  banUser: (id: string, reason: string): Promise<void> =>
    api.patch(`/admin/users/${id}/ban`, { reason }).then(() => undefined),

  unbanUser: (id: string): Promise<void> =>
    api.patch(`/admin/users/${id}/unban`).then(() => undefined),

  /** Мягкое удаление чужого аккаунта: то же, что DELETE /me, но без пароля. */
  deleteUser: (id: string): Promise<void> =>
    api.delete(`/admin/users/${id}`).then(() => undefined),

  /** Возврат в пределах отсрочки. Состав групп не восстанавливается. */
  restoreUser: (id: string): Promise<void> =>
    api.patch(`/admin/users/${id}/restore`).then(() => undefined),

  getFeedback: (category?: string, resolved?: boolean, page = 0, size = 20): Promise<AdminFeedbackPage> =>
    api.get('/admin/feedback', { params: { category, resolved, page, size } }).then((r) => r.data),

  resolveFeedback: (id: string): Promise<void> =>
    api.patch(`/admin/feedback/${id}/resolve`).then(() => undefined),

  unresolveFeedback: (id: string): Promise<void> =>
    api.patch(`/admin/feedback/${id}/unresolve`).then(() => undefined),

  getGroups: (search?: string, page = 0, size = 20): Promise<AdminGroupPage> =>
    api.get('/admin/groups', { params: { search, page, size } }).then((r) => r.data),

  deleteGroup: (id: string): Promise<void> =>
    api.delete(`/admin/groups/${id}`).then(() => undefined),
}
