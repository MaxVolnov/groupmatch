# Development Plan

This document tracks the phase-by-phase roadmap for GroupMatch.

---

## Phase 1 — Foundation ✅

- Project scaffolding: Vite + React 18 + TypeScript, Spring Boot 4 + Gradle
- CI pipeline (GitHub Actions): lint, type-check, test, build
- Database schema: `app_user`, JWT auth tables (Flyway V1–V2)
- Sign-up / sign-in pages with JWT access + refresh token flow
- Protected routes, Zustand auth store with `persist`
- Axios instance with automatic token refresh interceptor

## Phase 2 — Groups ✅

- `groups` table and CRUD API (V3 migration)
- Dashboard: list user's groups, create group form
- Group page with tab navigation (Availability / Heatmap / Meetings / Members)
- Invite link system: `group_invites` table (V4), token-based join flow
- Member management: role/status (OWNER / MEMBER, ACTIVE / LEFT / BANNED)
- Group settings: lock, show/hide participant names

## Phase 3 — Availability & Heatmap ✅

- `availability` table (V5 migration)
- Availability tab: add/delete time windows, 44 px touch targets
- Heatmap API: server-side aggregation into 30-minute slots, member name overlay
- Heatmap tab: week navigator, colour gradient (0 → max count), tooltip with names

## Phase 4 — Meetings & Polish ✅

### 4.1 Meetings
- `meetings` table (V6 migration)
- Meetings tab: create / delete meetings, export single meeting to `.ics`
- Heatmap slot click → pre-fill meeting form with slot times

### 4.2 Dark mode
- Tailwind `darkMode: 'class'` strategy
- `useThemeStore` (Zustand + persist): `'light' | 'dark' | 'system'`
- `applyTheme()` helper + `prefers-color-scheme` media query listener
- `ThemeToggle` button in nav (☀️ / 🌙 / 💻 cycle)
- Dark variants on all pages and components

### 4.3 Skeletons, empty states, error messages
- `Skeleton` component (`animate-pulse`, dark-aware)
- Replaced all spinners with skeletons on first load
- Inline spinner on subsequent refreshes (e.g. heatmap week switch)
- Empty states with emoji icons on every list/tab
- `ErrorMessage` component: human-readable messages for 429 (with Retry-After), network errors, 404, 5xx

### 4.4 Edit group & user profile
- `EditGroupModal`: edit title, description, timezone, showParticipants toggle
- `Profile` page (`/profile`): change display name and home timezone
- Layout nav link to profile page
- `meApi` (GET /me, PATCH /me) + mock implementation

### 4.5 Feedback form ✅
- `feedback` table (V7 migration), `POST /api/v1/feedback` endpoint
- `FeedbackModal` with category select (Bug / Feature request / Other) and textarea
- In-nav "💬 Feedback" button (desktop + mobile hamburger)
- Stale timeout fix via `useRef` to prevent phantom close after quick reopen

---

## Phase 5 — Admin panel (planned)

- Role-based access: `ADMIN` role on `app_user`
- Admin routes: `/admin/users`, `/admin/groups`, `/admin/feedback`
- User moderation: ban / unban, force-leave from groups
- Feedback inbox: list, filter by category, mark as resolved
- Group audit log

## Phase 6 — Notifications (planned)

- In-app notification bell: new member joined, meeting created
- Email notifications (SES / Resend): invite accepted, meeting reminder 1h before
- User notification preferences

## Phase 7 — Calendar integrations (planned)

- OAuth 2.0 with Google Calendar: read busy/free blocks, auto-import as availability
- Export full group meeting list to `.ics` feed URL (subscribe in any calendar app)

## Phase 8 — Mobile app (backlog)

- React Native (Expo) app sharing business logic with web
- Push notifications via Expo / FCM

---

## Backlog / Nice-to-have

- Recurring availability patterns (e.g. "every Monday 09:00–17:00")
- AI meeting-time suggestions based on heatmap + participant priorities
- Group analytics: average response rate, busiest days per member
- SSO / social login (Google, GitHub)
- Multi-language UI (i18n)
