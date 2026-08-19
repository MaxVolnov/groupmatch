# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [Unreleased]

_Nothing yet._

---

## [0.10.0] — 2026-08-19

### Added
- **Brand identity across the whole product** — new blue palette (`gm.*` scale, 50–950) replacing the default indigo; logo mark in the app header and on the static pages; favicons, Apple touch icon and web manifest; Open Graph image and tags on every entry point (`index.html`, `promo.html`, `legal.html`, `about.html`)
- **Redesigned sign-in and sign-up screens** — brand radial-gradient background, tinted form card, dark surface regardless of the user's theme

### Changed
- **Backend moved from Railway (US) to Timeweb Cloud (Moscow)** — the previous host was unreachable from Russian networks without a VPN, including on tiny responses. PostgreSQL and Valkey now live in the same Moscow private network; the frontend stays on Vercel
- **Privacy policy and terms brought in line with the product** — data-hosting statement now names Russia instead of the former US host; the free Pro period of early access is described in the offer; paid tariffs and payment are stated in the future tense while payments are switched off; "browser type" removed from the list of collected data, since the User-Agent is not logged anywhere

### Fixed
- `Retry-After` now reaches the browser — the header was set by the backend but stripped by CORS, so the rate-limit countdown in `ErrorMessage` never ran
- Debug logging no longer runs in production — `DEBUG` levels moved out of the defaults into `application-dev.yml`
- Creating a meeting no longer issues one query per member for notification preferences
- **Paid subscriptions were short-changing everyone** — the term was counted as 30 days per month, so a yearly subscription gave 360 days instead of 365. It is now counted in calendar months from the moment the payment goes through, which is also how the free Pro period has always been counted
- Production builds no longer default to a base path left over from GitHub Pages — a build made outside the main Vercel project loaded no assets at all and showed a blank page, without any error to explain it

### Technical
- Deployment is built from a `Dockerfile` at the repository root: multi-stage, JDK 25 → JRE 25, non-root user, fixed artifact name `app.jar` so the platform run command survives version bumps
- The platform health check points at `/actuator/health/liveness`, not at the `/actuator/health` aggregate — the aggregate returns 503 when any dependency is DOWN, which made the platform kill a perfectly alive container
- Application logs its actual bind address, socket family and self-reachability at startup (`ListenAddressLogger`)
- Missing Gradle wrapper JAR committed — `./gradlew` had never worked in a clean checkout
- Stale GitHub Pages workflow and Railway-era comments removed

---

## [0.9.0] — 2026-08-03

### Added
- **Free Pro for the first users** — every new account gets the Pro plan for 3 calendar months from sign-up, with a badge, a one-off dashboard banner and a note on the profile page; `PlanExpiryJob` returns the account to Free afterwards unless a paid subscription is active. Guests are not included
- **Calendar subscription for a group** (`.ics`) — a per-group token gives a feed URL that calendar clients refresh on their own, instead of downloading a one-off file
- **Promo landing page** at `/promo` — standalone entry point with its own copy, screenshots, `robots.txt` and `sitemap.xml`
- `/legal` and `/about` split out into standalone static pages that no longer load the SPA bundle

### Changed
- Guest accounts are no longer deleted while the person is still using them — the 90-day window is now counted from last activity instead of from the sign-up date
- Emails arrive in the language the user selected in the interface
- "Premium" renamed to "Pro" everywhere in the interface
- Signed-in visitors see "Open app" on the public pages instead of an invitation to sign in

### Security
- **YooKassa webhook is now authenticated** — the endpoint used to accept any POST. It now verifies an HMAC-SHA256 signature and the caller's IP against the provider's allowlist, and fails closed when either check cannot be performed
- **`X-Forwarded-For` is no longer trusted from arbitrary clients** — `ClientIpResolver` takes the rightmost untrusted hop and only honours the header when the TCP peer is in `TRUSTED_PROXIES`. Without this, anyone could bypass the sign-in rate limit by forging the header

### Fixed
- Plan limits on members, availability slots and invites are applied consistently on every path and only while monetization is enabled
- Creating a meeting no longer scales its query count with group size

---

## [0.8.0] — 2026-07-21

### Added
- **Russian and English interface** — full i18n across every page and component, a language switcher on the profile page, and localized email templates
- **Monetization feature flag** — the paywall, billing UI and plan limits are off by default, so the product can ship without payments being live

### Fixed
- **Users were being logged out at random** — the refresh flow raced with itself on parallel requests, sent an `Authorization` header to `/auth/` endpoints, and treated any error as a reason to sign out. Refresh is now serialized behind a mutex, the rotated token is persisted immediately, and only an explicit 401/403 ends the session
- Signed-in users opening `/signin` or `/signup` are redirected to the dashboard instead of seeing the forms again
- The browser tab title shows the group name on a group page

### Changed
- Frontend routing moved to Vercel (`vercel.json` rewrites); the GitHub Pages deployment became a redirect to `groupmatch.app`

---

## [0.7.0] — 2026-06-25

### Added
- **Admin area** — `ADMIN` role with auto-promotion via `ADMIN_EMAIL`, user list with search, ban/unban, plan and role management, group list, feedback resolution, and a route guard on the frontend
- **Paid plans** — pricing page, upgrade modal, paywall, `/me/plan` endpoint with usage stats, and a billing section on the profile page
- **YooKassa payments** — payment creation, subscription entity, expiry job, and a stub mode for local work

### Changed
- Guest sessions now last 90 days, with a banner offering to convert the guest account into a full one
- Transactional email switched from SMTP to the Resend HTTP API

### Fixed
- Unauthenticated requests return 401 instead of 403
- The heatmap cache is invalidated when availability changes, so the picture stops lagging behind the data
- Sticky footer no longer floats mid-page on short screens

### Technical
- Flyway migrations were disabled in `application.yml` and had to be turned on
- The monolithic integration test split into per-area classes with tag-based isolation; JaCoCo coverage reporting added

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
