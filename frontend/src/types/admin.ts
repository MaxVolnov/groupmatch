export interface AdminUser {
  id: string
  email: string
  displayName: string
  role: 'USER' | 'ADMIN'
  plan: 'FREE' | 'PRO' | 'TEAM'
  isGuest: boolean
  isBanned: boolean
  createdAt: string
}

export interface AdminUsersPage {
  users: AdminUser[]
  page: number
  totalPages: number
  totalElements: number
}
