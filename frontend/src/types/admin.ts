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

export interface AdminGroup {
  id: string
  title: string
  description: string | null
  timezone: string
  ownerId: string
  ownerEmail: string
  ownerDisplayName: string
  memberCount: number
  isLocked: boolean
  createdAt: string
}

export interface AdminGroupPage {
  groups: AdminGroup[]
  page: number
  totalPages: number
  totalElements: number
}
