# GroupMatch

**GroupMatch** is a web application for coordinating group availability and scheduling meetings. Members share their free time slots, the app builds an overlap heatmap, and the group owner picks the best window.

## Features

- **Group management** — create groups, invite members via a one-time link, lock groups to prevent new joins, transfer ownership to another member
- **Availability sharing** — each member adds their free windows; windows persist across weeks. Adjacent windows are merged, so a working week takes a handful of slots rather than dozens
- **Selection by gesture** — drag across the grid with a mouse to mark time and across your own slots to erase it; on touch, tap to start a selection and stretch it by its handles. A slot you already own opens an editor instead: change the time, copy it to other days, delete it
- **Repeating slots** — "every Tuesday and Thursday, 10:00–12:00, until the end of the month" in one form; the series is expanded into ordinary slots and can be edited or deleted as a whole
- **Bulk clear** — wipe a range of days and hours in one action, with a dry run that says how much would go
- **Overlap heatmap** — one 30-minute grid showing both layers at once: background is how many people are free, an inset marks your own time. Click any busy cell to pre-fill a meeting form
- **Who is free, by name** — hover a cell on a desktop or tap it on a phone and the names of everyone free in that half-hour appear under the table. Off by default; the group owner turns it on per group
- **Meetings** — owner schedules meetings from heatmap or manually; export to `.ics`
- **Account deletion** — delete your account from the profile page. Groups you own are handed to another member first, empty groups go away, and the account itself is anonymised after a 30-day grace period during which you can restore it
- **Dark mode** — light / dark / system-preference toggle, persisted in `localStorage`
- **User profile** — change display name and home timezone
- **Feedback** — in-app feedback form (bug reports, feature requests, other)
- **Admin panel** — user list with search, ban/unban, role and plan management, group list, feedback resolution
- **Guest accounts** — start without signing up and convert to a full account later, keeping every group and slot
- **Interface in Russian and English** — switchable on the profile page; emails follow the same choice
- **Notifications** — in-app bell and email: someone joined your group, a meeting was scheduled, a meeting starts in an hour. Each channel is toggled separately
- **Plans and subscriptions** — Free and Pro, YooKassa payments (behind a feature flag), free Pro for the first users
- **Calendar subscription** — a per-group `.ics` feed that calendar clients refresh on their own
- **Promo page** at `/promo` — standalone landing with its own `robots.txt` and `sitemap.xml`
- **`/legal` and `/about`** — terms, privacy policy and the about page as separate entry points, not routes inside the SPA

## Tech stack

| Layer | Technology |
|---|---|
| Frontend | React 18, TypeScript 5, Vite 5, Tailwind CSS 3 |
| State | Zustand (persist middleware) |
| Data fetching | TanStack Query v5 |
| Dates | Luxon |
| Backend | Java 25, Spring Boot 4 |
| Database | PostgreSQL 18 |
| Cache & sessions | Valkey (Redis-compatible) |
| Migrations | Flyway |
| Auth | JWT (access + refresh tokens) |
| Tests | JUnit 5, Testcontainers |
| Deployment | Vercel (frontend), Timeweb App Platform via `Dockerfile` (backend) |

## Production URLs

| Service | URL |
|---|---|
| Frontend | https://groupmatch.app |
| API | https://api.groupmatch.app |

How production is wired up, which environment variables it needs and what to do
when it breaks — `docs/prod-runbook.md`.

## Local development

### Prerequisites

- Node.js 20+
- Java 25 (or JDK compatible with Spring Boot 4)
- Docker (for PostgreSQL via Testcontainers or a local instance)

### Frontend

```bash
cd frontend
npm install
npm run dev                  # http://localhost:3000
```

Set `VITE_MOCK_API=true` in `.env.local` to run entirely in-browser with mock data (no backend needed). Set `VITE_API_URL` to point at a local backend instance.

### Backend

```bash
cd backend
# Start a local Postgres instance or rely on Testcontainers for tests
./gradlew bootRun            # http://localhost:8080
```

Environment variables expected by the backend:

| Variable | Description |
|---|---|
| `SPRING_DATASOURCE_URL` | JDBC URL, e.g. `jdbc:postgresql://localhost:5432/groupmatch` |
| `SPRING_DATASOURCE_USERNAME` | Database user |
| `SPRING_DATASOURCE_PASSWORD` | Database password |
| `JWT_SECRET` | 256-bit secret for signing JWTs |
| `SPRING_REDIS_URL` | Redis URL, e.g. `redis://localhost:6379` |
| `CORS_ALLOWED_ORIGINS` | Comma-separated allowed origins, e.g. `http://localhost:3000` — must match the dev server port above, or every request fails CORS |

### Running tests

```bash
cd backend
./gradlew test   # requires Docker for Testcontainers
```

## Project structure

The frontend is a multi-page build, not a single SPA: `index.html` is the
application, `promo.html`, `legal.html` and `about.html` are separate entry
points with their own bundles, which is why they have their own directories
under `src/`.

```
groupmatch/
├── frontend/
│   ├── index.html        # the app
│   ├── promo.html        # landing, /legal and /about are the same shape
│   ├── api/og/           # Vercel edge function: OG preview for /join/:token
│   ├── vercel.json       # rewrites for the entry points above + security headers
│   └── src/
│       ├── api/          # Axios wrappers + mock layer
│       ├── components/   # Shared UI components
│       ├── hooks/        # Drag selection, slot editor
│       ├── pages/        # Route-level pages; pages/group/ is the group screen
│       ├── store/        # Zustand stores (auth, theme)
│       ├── utils/        # Pure logic: grid, selection, series, error mapping
│       ├── shared/       # Shared between the app and the standalone pages
│       ├── locales/      # ru.json / en.json
│       ├── promo/  legal/  about/   # entry points for the standalone pages
│       └── types/        # Shared TypeScript types
└── backend/
    └── src/main/java/com/groupmatch/
        ├── config/       # Security, CORS, Jackson, startup guards
        ├── controller/   # REST controllers
        ├── domain/       # JPA entities + enums
        ├── dto/          # Request / response records
        ├── exception/    # Custom exceptions + @RestControllerAdvice
        ├── filter/       # Rate limiting
        ├── job/          # Scheduled cleanup and anonymisation
        ├── repository/   # Spring Data JPA repos
        ├── scheduler/    # Reminders
        ├── security/     # JWT filter, principal, client IP resolution
        ├── service/      # Business logic
        └── util/         # Small shared helpers
```

## Contributing

- `feature/*` branches cut from `develop`; open a PR back into `develop`
- `hotfix/*` branches cut from `main`; PR into both `main` and `develop`
- `develop` → `main` is merged on release
- CI (lint, type-check, tests, build) must pass before any merge
