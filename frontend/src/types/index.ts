// ── Auth ─────────────────────────────────────────────────────────────────────

export type Role = 'USER' | 'ADMIN'
export type Plan = 'FREE' | 'PRO' | 'TEAM'
export type Language = 'ru' | 'en'

export interface UserResponse {
  id: string
  email: string
  displayName: string
  tzId: string
  plan: Plan
  role: Role
  isEmailVerified: boolean
  createdAt: string
  locale?: Language
  /** Окончание псевдо-премиума; null — триала нет или он уже закрыт. */
  trialExpiresAt?: string | null
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
  /** Язык интерфейса на момент регистрации — им же уходят письма. */
  locale?: Language
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
  /**
   * Общий идентификатор слотов одной повторяющейся серии; `null` у одиночного
   * слота. Бэкенд отдаёт это поле с версии V23, тип отставал.
   */
  seriesId: string | null
  createdAt: string
}

export interface AvailabilityRequest {
  startsAt: string
  endsAt: string
  note?: string
}

/** Тело `POST /groups/{id}/availability/series`. */
export interface AvailabilitySeriesRequest {
  startDate: string
  endDate: string
  /** Имена дней недели: `'MONDAY'` … `'SUNDAY'`. */
  daysOfWeek: string[]
  startTime: string
  endTime: string
  timeZone: string
}

export interface AvailabilitySeriesResponse {
  seriesId: string
  createdCount: number
}

/** Тело `DELETE /groups/{id}/availability/bulk`. */
export interface AvailabilityBulkClearRequest {
  daysOfWeek: string[]
  startTime: string
  endTime: string
  fromDate: string
  toDate: string
  timeZone: string
  dryRun: boolean
}

export interface AvailabilityBulkClearResponse {
  /** При `dryRun` — сколько было бы удалено. */
  deletedCount: number
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

/**
 * Публичное превью приглашения. Полей ровно столько, сколько отдаёт бэкенд:
 * эндпоинт открыт без авторизации, и всё лишнее там было бы утечкой.
 */
export interface InvitePreview {
  valid: boolean
  groupName?: string
  inviterName?: string
  reason?: 'not_found' | 'expired' | 'revoked' | 'max_uses'
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

/** Ссылки на .ics-подписку календаря группы. */
export interface CalendarSubscriptionResponse {
  /** https-адрес фида — для копирования в любой календарь */
  url: string
  /** тот же адрес по схеме webcal:// — открывает календарь ОС */
  webcalUrl: string
  refreshMinutes: number
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

// ── Plan ──────────────────────────────────────────────────────────────────────

export interface PlanInfoResponse {
  plan: Plan
  ownedGroups: number
  groupLimit: number  // -1 = unlimited
}

// ── Subscription ─────────────────────────────────────────────────────────────

export type SubscriptionStatus = 'PENDING' | 'ACTIVE' | 'EXPIRED' | 'CANCELLED' | 'FAILED'

export interface SubscriptionResponse {
  id: string
  status: SubscriptionStatus
  periodMonths: number
  amountKopecks: number
  expiresAt: string | null
}

export interface CreatePaymentRequest {
  periodMonths: number
}

export interface CreatePaymentResponse {
  subscriptionId: string
  confirmationUrl: string | null
  amountKopecks: number
  currency: string
}

// ── Error ─────────────────────────────────────────────────────────────────────

export interface ApiError {
  code: string
  message: string
  details?: Record<string, string>
}
