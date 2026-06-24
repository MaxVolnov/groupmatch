/**
 * Static mock API — used when VITE_MOCK_API=true (e.g. GitHub Pages demo).
 * All handlers return the exact same shapes as the real backend DTOs.
 */
import type {
  AuthResponse,
  AvailabilityResponse,
  FeedbackCategory,
  FeedbackResponse,
  GroupResponse,
  HeatmapResponse,
  HeatmapSlot,
  InviteResponse,
  MeetingResponse,
  MemberResponse,
  UserResponse,
} from '@/types'
import { DateTime } from 'luxon'

export const IS_MOCK = import.meta.env.VITE_MOCK_API === 'true'

// ── Mock JWT (decoded by auth store's decodeJwt) ──────────────────────────────

const MOCK_USER_ID = '00000000-0000-0000-0000-000000000001'

function makeMockJwt(): string {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))
    .replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '')
  const payload = btoa(JSON.stringify({
    sub: MOCK_USER_ID,
    email: 'demo@groupmatch.app',
    role: 'USER',
    plan: 'PRO',
    exp: 9999999999,
    iat: 1700000000,
  })).replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '')
  return `${header}.${payload}.mock-signature`
}

const MOCK_ACCESS_TOKEN = makeMockJwt()
const MOCK_REFRESH_TOKEN = 'mock-refresh-token-demo'

function makeMockGuestJwt(displayName: string): string {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))
    .replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '')
  const payload = btoa(JSON.stringify({
    sub: '00000000-0000-0000-0000-000000000002',
    email: 'guest-mock@guest.groupmatch.local',
    displayName,
    role: 'USER',
    plan: 'FREE',
    exp: 9999999999,
    iat: 1700000000,
  })).replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '')
  return `${header}.${payload}.mock-signature`
}

// ── Mutable mock state (survives for the lifetime of the page) ────────────────

const groups: GroupResponse[] = [
  {
    id: 'group-1',
    title: 'Weekly Team Sync',
    description: 'Our weekly engineering standup',
    tzId: 'Europe/London',
    locked: false,
    showParticipants: true,
    ownerId: MOCK_USER_ID,
    version: 3,
    createdAt: '2025-01-10T10:00:00Z',
    updatedAt: '2025-01-10T10:00:00Z',
  },
  {
    id: 'group-2',
    title: 'Book Club',
    description: 'Monthly sci-fi discussions',
    tzId: 'Europe/Moscow',
    locked: false,
    showParticipants: false,
    ownerId: 'user-2',
    version: 1,
    createdAt: '2025-02-01T09:00:00Z',
    updatedAt: '2025-02-01T09:00:00Z',
  },
  {
    id: 'group-3',
    title: 'Game Night Planning',
    description: 'Finding time to play together',
    tzId: 'Europe/Berlin',
    locked: false,
    showParticipants: true,
    ownerId: MOCK_USER_ID,
    version: 2,
    createdAt: '2025-03-15T14:00:00Z',
    updatedAt: '2025-03-15T14:00:00Z',
  },
]

const membersByGroup: Record<string, MemberResponse[]> = {
  'group-1': [
    { userId: MOCK_USER_ID, displayName: 'Demo User', role: 'OWNER', status: 'ACTIVE', joinedAt: '2025-01-10T10:00:00Z' },
    { userId: 'user-2', displayName: 'Alice Chen', role: 'MEMBER', status: 'ACTIVE', joinedAt: '2025-01-11T09:00:00Z' },
    { userId: 'user-3', displayName: 'Bob Smith', role: 'MEMBER', status: 'ACTIVE', joinedAt: '2025-01-12T14:00:00Z' },
    { userId: 'user-4', displayName: 'Carol White', role: 'MEMBER', status: 'ACTIVE', joinedAt: '2025-01-13T11:00:00Z' },
    { userId: 'user-5', displayName: 'Dave Brown', role: 'MEMBER', status: 'ACTIVE', joinedAt: '2025-01-14T16:00:00Z' },
  ],
  'group-2': [
    { userId: 'user-2', displayName: 'Alice Chen', role: 'OWNER', status: 'ACTIVE', joinedAt: '2025-02-01T09:00:00Z' },
    { userId: MOCK_USER_ID, displayName: 'Demo User', role: 'MEMBER', status: 'ACTIVE', joinedAt: '2025-02-02T10:00:00Z' },
    { userId: 'user-6', displayName: 'Eve Johnson', role: 'MEMBER', status: 'ACTIVE', joinedAt: '2025-02-03T12:00:00Z' },
  ],
  'group-3': [
    { userId: MOCK_USER_ID, displayName: 'Demo User', role: 'OWNER', status: 'ACTIVE', joinedAt: '2025-03-15T14:00:00Z' },
    { userId: 'user-3', displayName: 'Bob Smith', role: 'MEMBER', status: 'ACTIVE', joinedAt: '2025-03-16T09:00:00Z' },
    { userId: 'user-7', displayName: 'Frank Lee', role: 'MEMBER', status: 'ACTIVE', joinedAt: '2025-03-17T11:00:00Z' },
    { userId: 'user-8', displayName: 'Grace Kim', role: 'MEMBER', status: 'ACTIVE', joinedAt: '2025-03-18T15:00:00Z' },
  ],
}

const slotsByGroupUser: Record<string, AvailabilityResponse[]> = {}

const meetingsByGroup: Record<string, MeetingResponse[]> = {
  'group-1': [
    {
      id: 'meeting-1',
      groupId: 'group-1',
      creatorId: MOCK_USER_ID,
      title: 'Sprint Planning Q2',
      description: 'Planning session for Q2 sprint',
      startsAt: DateTime.now().plus({ days: 3 }).set({ hour: 10, minute: 0, second: 0, millisecond: 0 }).toUTC().toISO()!,
      endsAt: DateTime.now().plus({ days: 3 }).set({ hour: 11, minute: 30, second: 0, millisecond: 0 }).toUTC().toISO()!,
      createdAt: DateTime.now().minus({ days: 1 }).toUTC().toISO()!,
    },
  ],
  'group-2': [],
  'group-3': [],
}

const invitesByGroup: Record<string, InviteResponse[]> = {
  'group-1': [
    {
      id: 'invite-1',
      groupId: 'group-1',
      token: 'demo-invite-token-abc123',
      createdBy: MOCK_USER_ID,
      createdAt: DateTime.now().minus({ days: 2 }).toUTC().toISO()!,
      expiresAt: DateTime.now().plus({ days: 28 }).toUTC().toISO()!,
      maxUses: 0,
      currentUses: 3,
      revoked: false,
    },
  ],
  'group-2': [],
  'group-3': [],
}

// ── Heatmap generation ────────────────────────────────────────────────────────

function generateHeatmap(groupId: string, from: string): HeatmapSlot[] {
  const monday = DateTime.fromISO(from).toLocal().startOf('day')
  const memberCounts: Record<string, number> = { 'group-1': 5, 'group-2': 3, 'group-3': 4 }
  const maxMembers = memberCounts[groupId] ?? 3

  // day → array of [startHour, count] pairs (local time)
  const pattern: Record<number, Array<[number, number]>> = {
    0: [[9, 3], [11, 5], [14, 2], [17, 4]],   // Mon
    1: [[10, 4], [13, 5], [16, 3], [18, 2]],   // Tue
    2: [[9, 2], [11, 3], [15, 5], [17, 5]],    // Wed — best overlap 15-18
    3: [[10, 3], [14, 4], [16, 5], [18, 2]],   // Thu
    4: [[9, 5], [11, 4], [14, 3]],             // Fri
  }

  const slots: HeatmapSlot[] = []

  for (const [dayStr, windows] of Object.entries(pattern)) {
    const day = monday.plus({ days: parseInt(dayStr) })
    for (const [startHour, baseCount] of windows) {
      // Expand each 2-hour window into 30-min buckets with slight variation
      for (let b = 0; b < 4; b++) {
        const bucketStart = day.plus({ hours: startHour, minutes: b * 30 })
        const bucketEnd = bucketStart.plus({ minutes: 30 })
        const count = Math.min(maxMembers, Math.max(1, baseCount + (b === 1 ? 1 : b === 3 ? -1 : 0)))
        slots.push({
          startsAt: bucketStart.toUTC().toISO()!,
          endsAt: bucketEnd.toUTC().toISO()!,
          count,
          memberIds: null,
          displayNames: null,
        })
      }
    }
  }

  return slots
}

// ── Delay helper ──────────────────────────────────────────────────────────────

const delay = (ms = 250) => new Promise<void>((r) => setTimeout(r, ms))

// ── Mock handlers ─────────────────────────────────────────────────────────────

export const mockApi = {
  auth: {
    signin: async (_data: { email: string; password: string }): Promise<AuthResponse> => {
      await delay()
      return { accessToken: MOCK_ACCESS_TOKEN, refreshToken: MOCK_REFRESH_TOKEN, expiresIn: 900, tokenType: 'Bearer' }
    },
    signup: async (_data: { email: string; password: string; displayName: string }): Promise<UserResponse> => {
      await delay()
      return {
        id: MOCK_USER_ID,
        email: 'demo@groupmatch.app',
        displayName: 'Demo User',
        tzId: 'Europe/London',
        plan: 'PRO',
        role: 'USER',
        isEmailVerified: true,
        createdAt: new Date().toISOString(),
      }
    },
    guest: async (data: { displayName: string }): Promise<AuthResponse> => {
      await delay()
      return { accessToken: makeMockGuestJwt(data.displayName), refreshToken: 'mock-guest-refresh-token', expiresIn: 900, tokenType: 'Bearer' }
    },
    refresh: async (): Promise<AuthResponse> => {
      await delay(100)
      return { accessToken: MOCK_ACCESS_TOKEN, refreshToken: MOCK_REFRESH_TOKEN, expiresIn: 900, tokenType: 'Bearer' }
    },
    logout: async (): Promise<void> => { await delay(100) },
  },

  groups: {
    list: async (): Promise<GroupResponse[]> => { await delay(); return [...groups] },
    get: async (id: string): Promise<GroupResponse> => {
      await delay()
      const g = groups.find((x) => x.id === id)
      if (!g) throw Object.assign(new Error('Group not found'), { response: { status: 404, data: { code: 'group_not_found', message: 'Group not found' } } })
      return g
    },
    create: async (data: { title: string; description?: string; tzId?: string }): Promise<GroupResponse> => {
      await delay(400)
      const g: GroupResponse = {
        id: `group-${Date.now()}`,
        title: data.title,
        description: data.description ?? null,
        tzId: data.tzId ?? 'Europe/London',
        locked: false,
        showParticipants: false,
        ownerId: MOCK_USER_ID,
        version: 0,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      }
      groups.push(g)
      membersByGroup[g.id] = [{ userId: MOCK_USER_ID, displayName: 'Demo User', role: 'OWNER', status: 'ACTIVE', joinedAt: new Date().toISOString() }]
      meetingsByGroup[g.id] = []
      invitesByGroup[g.id] = []
      return g
    },
    update: async (id: string, data: Partial<GroupResponse>): Promise<GroupResponse> => {
      await delay()
      const g = groups.find((x) => x.id === id)
      if (!g) throw new Error('Not found')
      Object.assign(g, data, { updatedAt: new Date().toISOString() })
      return g
    },
    delete: async (id: string): Promise<void> => {
      await delay()
      const idx = groups.findIndex((x) => x.id === id)
      if (idx !== -1) groups.splice(idx, 1)
    },
    members: async (id: string): Promise<MemberResponse[]> => {
      await delay()
      return membersByGroup[id] ?? []
    },
    addMember: async (id: string, userId: string): Promise<MemberResponse> => {
      await delay()
      const m: MemberResponse = { userId, displayName: `User ${userId.slice(0, 6)}`, role: 'MEMBER', status: 'ACTIVE', joinedAt: new Date().toISOString() }
      if (!membersByGroup[id]) membersByGroup[id] = []
      membersByGroup[id].push(m)
      return m
    },
    removeMember: async (groupId: string, userId: string): Promise<void> => {
      await delay()
      const arr = membersByGroup[groupId]
      if (arr) {
        const idx = arr.findIndex((m) => m.userId === userId)
        if (idx !== -1) arr.splice(idx, 1)
      }
    },
  },

  availability: {
    mySlots: async (groupId: string): Promise<AvailabilityResponse[]> => {
      await delay()
      return slotsByGroupUser[`${groupId}:${MOCK_USER_ID}`] ?? []
    },
    addSlot: async (groupId: string, data: { startsAt: string; endsAt: string; note?: string }): Promise<AvailabilityResponse> => {
      await delay(300)
      const slot: AvailabilityResponse = {
        id: `slot-${Date.now()}`,
        groupId,
        userId: MOCK_USER_ID,
        startsAt: data.startsAt,
        endsAt: data.endsAt,
        note: data.note ?? null,
        createdAt: new Date().toISOString(),
      }
      const key = `${groupId}:${MOCK_USER_ID}`
      if (!slotsByGroupUser[key]) slotsByGroupUser[key] = []
      slotsByGroupUser[key].push(slot)
      return slot
    },
    updateSlot: async (groupId: string, slotId: string, data: { startsAt: string; endsAt: string; note?: string }): Promise<AvailabilityResponse> => {
      await delay()
      const key = `${groupId}:${MOCK_USER_ID}`
      const arr = slotsByGroupUser[key] ?? []
      const slot = arr.find((s) => s.id === slotId)
      if (!slot) throw new Error('Slot not found')
      Object.assign(slot, { startsAt: data.startsAt, endsAt: data.endsAt, note: data.note ?? null })
      return slot
    },
    deleteSlot: async (groupId: string, slotId: string): Promise<void> => {
      await delay()
      const key = `${groupId}:${MOCK_USER_ID}`
      const arr = slotsByGroupUser[key]
      if (arr) {
        const idx = arr.findIndex((s) => s.id === slotId)
        if (idx !== -1) arr.splice(idx, 1)
      }
    },
    heatmap: async (groupId: string, from: string): Promise<HeatmapResponse> => {
      await delay(500)
      return {
        slots: generateHeatmap(groupId, from),
        granularityMinutes: 30,
        from,
        to: DateTime.fromISO(from).plus({ days: 7 }).toUTC().toISO()!,
      }
    },
  },

  invites: {
    list: async (groupId: string): Promise<InviteResponse[]> => {
      await delay()
      return invitesByGroup[groupId] ?? []
    },
    create: async (groupId: string, _data: { maxUses: number }): Promise<InviteResponse> => {
      await delay(300)
      const inv: InviteResponse = {
        id: `invite-${Date.now()}`,
        groupId,
        token: `demo-token-${Math.random().toString(36).slice(2, 10)}`,
        createdBy: MOCK_USER_ID,
        createdAt: new Date().toISOString(),
        expiresAt: DateTime.now().plus({ days: 30 }).toUTC().toISO()!,
        maxUses: 0,
        currentUses: 0,
        revoked: false,
      }
      if (!invitesByGroup[groupId]) invitesByGroup[groupId] = []
      invitesByGroup[groupId].push(inv)
      return inv
    },
    revoke: async (groupId: string, inviteId: string): Promise<void> => {
      await delay()
      const arr = invitesByGroup[groupId]
      if (arr) {
        const inv = arr.find((i) => i.id === inviteId)
        if (inv) inv.revoked = true
      }
    },
    join: async (token: string): Promise<InviteResponse> => {
      await delay(400)
      // Find the invite by token and return it so JoinInvite page can navigate
      for (const arr of Object.values(invitesByGroup)) {
        const inv = arr.find((i) => i.token === token)
        if (inv) return inv
      }
      // Default: join group-1
      return invitesByGroup['group-1']?.[0] ?? {
        id: 'invite-demo', groupId: 'group-1', token, createdBy: MOCK_USER_ID,
        createdAt: new Date().toISOString(),
        expiresAt: DateTime.now().plus({ days: 30 }).toUTC().toISO()!,
        maxUses: 0, currentUses: 1, revoked: false,
      }
    },
  },

  feedback: {
    create: async (data: { category: string; message: string }): Promise<FeedbackResponse> => {
      await delay(300)
      return {
        id: `feedback-${Date.now()}`,
        category: data.category as FeedbackCategory,
        message: data.message,
        createdAt: new Date().toISOString(),
      }
    },
  },

  me: (() => {
    const profile: UserResponse = {
      id: MOCK_USER_ID,
      email: 'demo@groupmatch.app',
      displayName: 'Demo User',
      tzId: 'Europe/London',
      plan: 'PRO',
      role: 'USER',
      isEmailVerified: true,
      createdAt: '2025-01-01T00:00:00Z',
    }
    return {
      get: async (): Promise<UserResponse> => { await delay(); return { ...profile } },
      update: async (data: { displayName?: string; tzId?: string }): Promise<UserResponse> => {
        await delay(300)
        Object.assign(profile, data)
        return { ...profile }
      },
    }
  })(),

  meetings: {
    list: async (groupId: string): Promise<MeetingResponse[]> => {
      await delay()
      return meetingsByGroup[groupId] ?? []
    },
    get: async (groupId: string, meetingId: string): Promise<MeetingResponse> => {
      await delay()
      const m = (meetingsByGroup[groupId] ?? []).find((x) => x.id === meetingId)
      if (!m) throw new Error('Meeting not found')
      return m
    },
    create: async (groupId: string, data: { title: string; description?: string; startsAt: string; endsAt: string }): Promise<MeetingResponse> => {
      await delay(400)
      const m: MeetingResponse = {
        id: `meeting-${Date.now()}`,
        groupId,
        creatorId: MOCK_USER_ID,
        title: data.title,
        description: data.description ?? null,
        startsAt: data.startsAt,
        endsAt: data.endsAt,
        createdAt: new Date().toISOString(),
      }
      if (!meetingsByGroup[groupId]) meetingsByGroup[groupId] = []
      meetingsByGroup[groupId].push(m)
      return m
    },
    update: async (groupId: string, meetingId: string, data: { title: string; description?: string; startsAt: string; endsAt: string }): Promise<MeetingResponse> => {
      await delay()
      const arr = meetingsByGroup[groupId] ?? []
      const m = arr.find((x) => x.id === meetingId)
      if (!m) throw new Error('Meeting not found')
      Object.assign(m, { title: data.title, description: data.description ?? null, startsAt: data.startsAt, endsAt: data.endsAt })
      return m
    },
    delete: async (groupId: string, meetingId: string): Promise<void> => {
      await delay()
      const arr = meetingsByGroup[groupId]
      if (arr) {
        const idx = arr.findIndex((x) => x.id === meetingId)
        if (idx !== -1) arr.splice(idx, 1)
      }
    },
  },
}
