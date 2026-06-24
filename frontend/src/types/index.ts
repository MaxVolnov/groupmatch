// ── Auth ─────────────────────────────────────────────────────────────────────

export type Role = 'USER' | 'ADMIN'
export type Plan = 'FREE' | 'PRO' | 'TEAM'

export interface UserResponse {
  id: string
  email: string
  displayName: string
  tzId: string
  plan: Plan
  role: Role
  isEmailVerified: boolean
  createdAt: string
}

export interface NotificationBannerProps {
  message: string
  action?: { label: string; onClick: () => void }
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  tokenType: string
}

export interface SignupRequest {
  email: string
  password: string
  displayName: string
  tzid?: string
}

export interface SigninRequest {
  email: string
  password: string
}

// ── Groups ────────────────────────────────────────────────────────────────────

export interface GroupResponse {
  id: string
  title: string
  description: string | null
  tzId: string
  locked: boolean
  showParticipants: boolean
  ownerId: string
  version: number
  createdAt: string
  updatedAt: string
}

export interface GroupRequest {
  title: string
  description?: string
  tzId?: string
  locked?: boolean
  showParticipants?: boolean
}

export type GroupRole = 'OWNER' | 'MEMBER'
export type MemberStatus = 'ACTIVE' | 'LEFT' | 'BANNED'

export interface MemberResponse {
  userId: string
  displayName: string
  role: GroupRole
  status: MemberStatus
  joinedAt: string
}

// ── Availability ──────────────────────────────────────────────────────────────

export interface AvailabilityResponse {
  id: string
  groupId: string
  userId: string
  startsAt: string
  endsAt: string
  note: string | null
  createdAt: string
}

export interface AvailabilityRequest {
  startsAt: string
  endsAt: string
  note?: string
}

export interface HeatmapSlot {
  startsAt: string
  endsAt: string
  count: number
  memberIds: string[] | null
  displayNames: string[] | null
}

export interface HeatmapResponse {
  slots: HeatmapSlot[]
  granularityMinutes: number
  from: string
  to: string
}

// ── Invites ───────────────────────────────────────────────────────────────────

export interface InviteResponse {
  id: string
  groupId: string
  token: string
  createdBy: string
  createdAt: string
  expiresAt: string
  maxUses: number
  currentUses: number
  revoked: boolean
}

export interface CreateInviteRequest {
  expiresAt?: string
  maxUses: number
}

// ── Meetings ──────────────────────────────────────────────────────────────────

export interface MeetingResponse {
  id: string
  groupId: string
  creatorId: string
  title: string
  description: string | null
  startsAt: string
  endsAt: string
  createdAt: string
}

export interface MeetingRequest {
  title: string
  description?: string
  startsAt: string
  endsAt: string
}

// ── Feedback ──────────────────────────────────────────────────────────────────

export type FeedbackCategory = 'BUG' | 'FEATURE_REQUEST' | 'OTHER'

export interface FeedbackRequest {
  category: FeedbackCategory
  message: string
}

export interface FeedbackResponse {
  id: string
  category: FeedbackCategory
  message: string
  createdAt: string
}

// ── Notification preferences ──────────────────────────────────────────────────

export interface NotificationPreferences {
  emailMemberJoined: boolean
  emailMeetingReminder: boolean
  inappMemberJoined: boolean
  inappMeetingCreated: boolean
}

// ── Notifications ─────────────────────────────────────────────────────────────

export type NotificationType = 'MEMBER_JOINED' | 'MEETING_CREATED'

export interface NotificationResponse {
  id: string
  type: NotificationType
  payload: Record<string, string>
  read: boolean
  createdAt: string
}

// ── Pagination ────────────────────────────────────────────────────────────────

export interface PageResponse<T> {
  items: T[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}

export interface UnreadCountResponse {
  count: number
}

// ── Error ─────────────────────────────────────────────────────────────────────

export interface ApiError {
  code: string
  message: string
  details?: Record<string, string>
}
