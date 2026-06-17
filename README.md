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
- **Admin panel** (Phase 5, planned) — moderate users and groups

## Tech stack

| Layer | Technology |
|---|---|
| Frontend | React 18, TypeScript 5, Vite 5, Tailwind CSS 3 |
| State | Zustand (persist middleware) |
| Data fetching | TanStack Query v5 |
| Dates | Luxon |
| Backend | Java 25, Spring Boot 4 |
| Database | PostgreSQL 16 |
| Migrations | Flyway |
| Auth | JWT (access + refresh tokens) |
| Tests | JUnit 5, Testcontainers |

## Production URLs

| Service | URL |
|---|---|
| Frontend | https://groupmatch.app |
| API | https://api.groupmatch.app |

## Local development

### Prerequisites

- Node.js 22+
- Java 25 (or JDK compatible with Spring Boot 4)
- Docker (for PostgreSQL via Testcontainers or a local instance)

### Frontend

```bash
cd frontend
cp .env.example .env.local   # set VITE_API_URL or leave blank for mock mode
npm install
npm run dev                  # http://localhost:5173
```

Set `VITE_MOCK=true` in `.env.local` to run entirely in-browser with mock data (no backend needed).

### Backend

```bash
cd backend
# Start a local Postgres instance or rely on Testcontainers for tests
./gradlew bootRun            # http://localhost:8080
```

Environment variables expected by the backend:

| Variable | Description |
|---|---|
| `DB_URL` | JDBC URL, e.g. `jdbc:postgresql://localhost:5432/groupmatch` |
| `DB_USERNAME` | Database user |
| `DB_PASSWORD` | Database password |
| `JWT_SECRET` | 256-bit secret for signing JWTs |
| `JWT_EXPIRY_SECONDS` | Access token lifetime (default 900) |
| `JWT_REFRESH_EXPIRY_SECONDS` | Refresh token lifetime (default 604800) |

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

1. Create a feature branch from `main`.
2. Make changes, add tests where appropriate.
3. Open a pull request — CI must pass before merge.
