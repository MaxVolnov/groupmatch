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

export interface AdminFeedbackItem {
  id: string
  category: 'BUG' | 'FEATURE_REQUEST' | 'OTHER'
  message: string
  authorEmail: string | null
  authorDisplayName: string | null
  resolved: boolean
  resolvedAt: string | null
  createdAt: string
}

export interface AdminFeedbackPage {
  items: AdminFeedbackItem[]
  page: number
  totalPages: number
  totalElements: number
}
