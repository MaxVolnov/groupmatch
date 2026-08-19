# GroupMatch

**GroupMatch** is a web application for coordinating group availability and scheduling meetings. Members share their free time slots, the app builds an overlap heatmap, and the group owner picks the best window.

## Features

- **Group management** — create groups, invite members via a one-time link, lock groups to prevent new joins
- **Availability sharing** — each member adds their free windows; windows persist across weeks
- **Overlap heatmap** — 30-minute grid coloured by member count; click any busy cell to pre-fill a meeting form
- **Meetings** — owner schedules meetings from heatmap or manually; export to `.ics`
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
npm run dev                  # http://localhost:5173
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
| `CORS_ALLOWED_ORIGINS` | Comma-separated allowed origins, e.g. `http://localhost:5173` |

### Running tests

```bash
cd backend
./gradlew test   # requires Docker for Testcontainers
```

## Project structure

```
groupmatch/
├── frontend/
│   ├── src/
│   │   ├── api/          # Axios wrappers + mock layer
│   │   ├── components/   # Shared UI components
│   │   ├── pages/        # Route-level pages
│   │   ├── store/        # Zustand stores (auth, theme)
│   │   └── types/        # Shared TypeScript types
│   └── ...
└── backend/
    └── src/main/java/com/groupmatch/
        ├── controller/   # REST controllers
        ├── domain/       # JPA entities + enums
        ├── dto/          # Request / response records
        ├── repository/   # Spring Data JPA repos
        └── service/      # Business logic
```

## Contributing

- `feature/*` branches cut from `develop`; open a PR back into `develop`
- `hotfix/*` branches cut from `main`; PR into both `main` and `develop`
- `develop` → `main` is merged on release
- CI (lint, type-check, tests, build) must pass before any merge
