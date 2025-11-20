# GroupMatch

SaaS platform for coordinating group meetings through visual availability heatmaps.

## Tech Stack

- **Backend**: Java 21, Spring Boot 3, PostgreSQL 16, Redis 7
- **Frontend**: React 18, TypeScript, Vite, Tailwind CSS
- **Infrastructure**: Docker, Kubernetes (future)

## Quick Start

### Prerequisites

- Java 21
- Node.js 20+
- Docker Desktop

### Local Development
```bash
# 1. Start infrastructure
docker-compose up -d

# 2. Start backend
cd backend
./gradlew bootRun

# 3. Start frontend (in another terminal)
cd frontend
npm install
npm run dev
```

- Backend: http://localhost:8080
- Frontend: http://localhost:3000
- API Docs: http://localhost:8080/swagger-ui.html

## Documentation

- [Design Document](docs/design-document.md)
- [Development Plan](docs/development-plan.md)
- [API Specification](docs/api-spec.md)

## Project Status

🚧 **In Development** - Prototype Phase (Sprint 1-2)

## License

Proprietary - All rights reserved