# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [Unreleased]

_Nothing yet._

---

## [0.6.0] — 2026-06-24

### Added
- **In-app notification bell** — polling every 30 s; unread badge (capped at 9+); dropdown lists last 50 notifications; mark single or all as read (`MEMBER_JOINED`, `MEETING_CREATED` types)
- **Email notifications** — owner receives an email when a member joins their group; group members receive a meeting-reminder email 55–65 min before a scheduled meeting (`MeetingReminderJob`, `@Scheduled` every 5 min)
- **Notification preferences** — four per-user toggles (`emailMemberJoined`, `emailMeetingReminder`, `inappMemberJoined`, `inappMeetingCreated`); exposed on the Profile page; lazy-row creation on first use
- **Guest account upgrade** — guest users can supply email + password + display name to convert to a full account; old refresh tokens are invalidated and a verification email is sent
- Flyway migrations V14–V16: `notification` table (VARCHAR type column, JSONB payload), `meeting.reminder_sent` column, `notification_preferences` table
- 13 new integration tests (orders 23–35) covering notifications, notification preferences, meeting-created notification, guest upgrade, email verification, and password reset

### Technical
- `NotificationRepository.findTop50ByUserIdOrderByCreatedAtDesc` replaces the unbounded query
- `InviteService.joinByToken` now loads the group owner once instead of twice
- `app.mail.from` in test config aligned to `test@groupmatch-test.io`

---

## [0.4.1] — 2026-06-17

### Fixed
- Group page crashed with a blank white screen on every visit (`Minified React error #301: Too many re-renders`) — caused by calling `setInitialLoaded` inside the `select` callback of the heatmap `useQuery`, which executes synchronously during render. Moved the side effect into a `useEffect`.

---

## [0.4.0] — 2026-06-17

### Added
- **Dark mode** — light / dark / system-preference theme toggle with `useThemeStore` (Zustand + localStorage persist); `ThemeToggle` button in the nav bar cycles ☀️ → 🌙 → 💻
- **Skeleton loaders** — replaced all loading spinners with animated skeletons on first page load; kept inline spinner for subsequent background refreshes (e.g. heatmap week navigation)
- **Empty states** — every list and tab now shows a friendly message with an emoji icon when there is no data
- **Human-readable error messages** — `ErrorMessage` component handles 429 with Retry-After countdown, network errors, 404, 5xx, and passes through backend-provided messages for everything else
- **Edit group modal** — group owner can update title, description, timezone, and the "show participants" toggle without leaving the group page
- **User profile page** (`/profile`) — change display name and home timezone; accessible from the nav bar
- **Heatmap → meeting shortcut** — clicking any busy heatmap slot (owner only) opens the "Create meeting" modal pre-filled with the slot's start/end times
- **`CreateMeetingModal`** — extracted from `MeetingsTab` into its own component; accepts optional `initialStartsAt` / `initialEndsAt` props
- **Feedback form** — "💬 Feedback" button in the nav bar (desktop + mobile) opens a modal with category select (Bug report / Feature request / Other) and a free-text field; persisted to `POST /api/v1/feedback`
- **`feedback` table** (V7 Flyway migration) with category CHECK constraint, FK → `app_user ON DELETE CASCADE`, and two indexes
- **`FeedbackController`**, **`FeedbackService`**, **`FeedbackRepository`** — full backend implementation returning HTTP 201
- **Integration test** `submitFeedback()` — verifies the feedback endpoint end-to-end via Testcontainers

### Fixed
- Datetime range validation in availability and meeting forms: changing `startsAt` now auto-advances `endsAt` to `startsAt + 1h` when `endsAt` would otherwise precede `startsAt`
- Stale `setTimeout` in `FeedbackModal`: rapid close → reopen within the 2-second auto-close window no longer triggers a phantom close on the newly opened modal (fixed with `useRef` to clear the previous timer)
- `tsconfig.json` had invalid `"ignoreDeprecations": "6.0"` for TypeScript 5.9 — removed

### Changed
- `MeetingsTab` now receives an `onScheduleClick` callback instead of rendering its own `CreateMeetingModal` — the modal is lifted to `GroupPage` so heatmap and meetings tab share one instance
- Layout nav links to `/profile` instead of being plain text
- Mobile hamburger menu includes "💬 Feedback" entry

---

## [0.3.0] — 2026-05-10

### Added
- Meetings tab: create, list, delete meetings; export single meeting to `.ics`
- `meetings` table (V6 Flyway migration)

---

## [0.2.0] — 2026-04-15

### Added
- Availability tab: add and delete time windows per group per user
- Heatmap tab: 30-minute overlap grid with week navigator and member name tooltip
- `availability` table (V5 Flyway migration)
- Heatmap aggregation API returning slot counts and participant lists

---

## [0.1.0] — 2026-03-20

### Added
- Project scaffolding: React 18 + Vite + TypeScript + Tailwind CSS frontend; Spring Boot 4 + Gradle backend
- GitHub Actions CI: lint, type-check, backend tests, frontend build
- Sign-up / sign-in with JWT (access + refresh tokens), token refresh interceptor
- Dashboard: list and create groups
- Group page with Availability / Heatmap / Meetings / Members tabs
- Invite link system: generate, copy, join via token
- Member management: owner can ban/unban members
- Group settings: lock, show/hide participant names
- Flyway migrations V1–V4
