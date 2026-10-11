# PulseOS — Clinical Operations Workspace

A full-stack **medical operations portfolio project** built incrementally with Next.js, Spring Boot and PostgreSQL.

> **Synthetic data only.** PulseOS is a development/reference application, not a certified EHR or an approved clinical decision-support system. Never upload real patient information.

## Tech stack

- **Frontend:** Next.js 16 App Router, React 19, TypeScript
- **Backend:** Java 21, Spring Boot 4.1, REST API
- **Data:** PostgreSQL 17
- **Operations:** Docker Compose, GitHub Actions

## Stage 01 — Foundation (current)

- Monorepo with separate `apps/web` and `services/api`
- Responsive dark-themed landing dashboard (no fabricated patient data)
- A real backend status endpoint: `GET /api/v1/system/status`
- PostgreSQL database and private container network
- Docker-based development and GitHub Actions CI

The UI detects whether the backend is available. Authentication, real patient workflows, schema migrations and charting will be added in later, separately reviewed commits.

## Run with Docker

Requirements: Docker Desktop (or Docker Engine + Compose).

```bash
./scripts/init-env.sh
docker compose up --build
```

Open http://localhost:3000. The Spring Boot service is accessible only within the Docker network. Do not commit your generated `.env` file.

## Run locally without Docker

Start a PostgreSQL 17 server and export database environment variables, then:

```bash
cd services/api && mvn spring-boot:run
# in another terminal
cd apps/web && npm install && API_INTERNAL_URL=http://localhost:8080 npm run dev
```

## Development roadmap

1. Foundation: Next.js, Spring Boot, Docker, CI
2. Authentication: staff identity, JWT, server-side sessions
3. Clinical domain: patients, appointments, notes, audit events
4. Workspace: charts, search, appointments, activity
5. Security hardening, tests, release documentation

Read [the architecture overview](docs/architecture.md) and [the security notice](SECURITY.md).