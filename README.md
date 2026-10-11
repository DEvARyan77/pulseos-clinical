# PulseOS — Clinical Operations Workspace

A full-stack **medical operations portfolio project** developed incrementally with Next.js, Spring Boot and PostgreSQL.

> **Synthetic data only.** PulseOS is a reference/portfolio application, not a certified EHR or approved clinical decision-support system. Do not enter real patient information.

## Tech stack
- Frontend: Next.js 16 App Router, React 19, TypeScript
- Backend: Java 21, Spring Boot 4.1 REST API
- Database infrastructure: PostgreSQL 17
- Operations: Docker Compose and GitHub Actions

## Stage 01 — Foundation
- Monorepo: `apps/web`, `services/api`
- API status endpoint at `GET /api/v1/system/status`
- Containerized PostgreSQL and development Docker Compose
- CI configuration

## Stage 02 — Patient workspace UI (synthetic demonstration)
- Option D: three-column layout with patient queue, chart overview and care timeline
- Search and filter the queue; select patients to update the visible chart
- Switch between Overview, Notes, Orders and Results preview tabs
- Toggle in-memory demo tasks
- Light, Dark and System theme selector, with persistent browser preference. System mode follows OS color-scheme changes.
- Responsive layouts for smaller screens
- **No real clinical records or backend CRUD**: all patient data is fabricated and kept in frontend source code; task updates reset on reload

## Run with Docker
```bash
./scripts/init-env.sh
docker compose up --build
```
Open http://localhost:3000.

## Run locally
Start PostgreSQL 17, configure credentials via environment variables and run:
```bash
cd services/api && mvn spring-boot:run
# separate terminal
cd apps/web && npm install && API_INTERNAL_URL=http://localhost:8080 npm run dev
```

## Upcoming stages
Secure authentication and sessions; patient/appointment persistence with migrations; authorization and audit events; automated tests, monitoring, and security hardening. The current deployment must not be used with protected health information.

See [architecture](docs/architecture.md) and [security notice](SECURITY.md).
