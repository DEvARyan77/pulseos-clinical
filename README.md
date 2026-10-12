# PulseOS — Medical Operations Portfolio

PulseOS is a Next.js 16 + Spring Boot 4.1 + PostgreSQL clinical workspace demonstration.

> **All medical information shown by the frontend is fictional. Do not enter real patient data. PulseOS is not a certified EHR and is not approved for healthcare deployments.**

## What works
- Responsive patient queue with search, status filters and selected-patient switching (fictional UI fixtures)
- Read-only synthetic vitals, allergies, diagnoses, medications, visit notes and activity timeline
- Interactive local demo task checklist
- Light / Dark / System appearance with persisted choice
- Real staff **signup** (invitation-restricted), **login**, JWT session checks and logout when authentication is enabled
- PostgreSQL staff accounts created using Flyway, passwords protected with BCrypt

## What is not built yet
- Database-backed patient, appointment and visit-note CRUD
- Staff role permissions, clinical audit logs, session revocation, MFA, invitation lifecycle, API rate limiting, email confirmation
- Medical privacy/compliance assessment and production hardening

## Run with Docker

```bash
./scripts/init-env.sh
docker compose up --build
```

Open http://localhost:3000, http://localhost:3000/login or http://localhost:3000/signup. Authentication and registration are OFF by default: the pages remain visible but signup/login forms are disabled until configured.

## Enable account creation

For both frontend and backend set `PULSEOS_AUTH_ENABLED=true`. For both services set `PULSEOS_REGISTRATION_ENABLED=true` if you want new accounts to sign up. On the backend configure `PULSEOS_AUTH_JWT_SECRET_BASE64` and `PULSEOS_REGISTRATION_INVITE_CODE` to independently generated high-entropy secrets.

```bash
openssl rand -base64 48
openssl rand -hex 24
```

Set your existing `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` to valid PostgreSQL connection values. On Render set `API_INTERNAL_URL` to the deployed API's reachable URL for frontend server requests. The database must be available when the backend starts.

After signup succeeds, disable registration on both services; keep auth enabled to allow existing users to log in.

Detailed configuration: [Authentication guide](docs/authentication.md). Security limitations: [SECURITY.md](SECURITY.md).

## Architecture
- `apps/web`: Next.js (server-side session check, BFF login/register/logout, themed clinical demo UI)
- `services/api`: Spring Boot (JWT signing/verification, BCrypt validation, registration)
- `services/api/src/main/resources/db/migration`: versioned Flyway migrations
- `compose.yaml`: local web + API + database orchestration

## Roadmap
Implement persistent, audited, permission-controlled appointments, patient charts and clinical notes using synthetic data; then add integration tests, security controls and deployment monitoring.
