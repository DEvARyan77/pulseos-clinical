# PulseOS — Medical Operations Portfolio

PulseOS is a **Next.js 16 + Spring Boot 4.1 + PostgreSQL** clinical workspace demonstration.

> **All clinical data shown by the frontend is fictional. Do not enter real patient information. PulseOS is not a certified EHR or approved clinical application.**

## Implemented functionality
- Responsive patient workspace with fictional patient queue, search, filters, records and timeline
- System-following Light / Dark theme with saved overrides
- Invitation-restricted staff signup, PostgreSQL/BCrypt credential storage and JWT login/logout
- **Database-backed demo appointment scheduling:** choose one of seven hardcoded `DEMO-` patients, select an appointment date and visit type, view your saved appointments, and cancel a booking. Appointments belong to the signed-in staff account.
- Flyway schema migrations for account and demo appointment records

## Limitations
- The seven patient charts are **read-only frontend fixtures**, not synchronized to PostgreSQL.
- Account enrollment is protected only by a shared invitation code; enable it only while enrolling demo users.
- No MFA, login rate limiting, revocable sessions, medical billing, clinical auditing, or real clinical workflows.
- Patient/chart editing, clinical notes, medical orders and actual healthcare compliance are NOT implemented.

## Docker quick start
```bash
./scripts/init-env.sh
docker compose up --build
```
Open http://localhost:3000, `/login`, `/signup` or `/appointments`.

By default, authorization and enrollment are off. The patient workspace is viewable as an unauthenticated synthetic demo; scheduling requires the sign-in feature to be enabled.

## Enable real demo account functionality on Render or Docker
Configure **both API and frontend**:
```
PULSEOS_AUTH_ENABLED=true
PULSEOS_REGISTRATION_ENABLED=true
```
Configure **API only**:
```
PULSEOS_AUTH_JWT_SECRET_BASE64=<random Base64 secret of 32+ bytes>
PULSEOS_REGISTRATION_INVITE_CODE=<private invite of at least 24 characters>
```

Create unique secrets locally using `openssl rand -base64 48` and `openssl rand -hex 24`. Set working PostgreSQL datasource environment variables on the API and set `API_INTERNAL_URL` on the frontend to a backend URL reachable from its server.

1. Redeploy the API and frontend.
2. Sign up at `/signup` with the private invite code, then log in at `/login`.
3. Open `/appointments` to create, view, or cancel database-saved demo bookings.
4. Disable `PULSEOS_REGISTRATION_ENABLED` on both services after signup, but keep authentication enabled.

See [authentication guide](docs/authentication.md), [security status](SECURITY.md), and [architecture](docs/architecture.md).

## Structure
- `apps/web`: Next.js pages and authenticated server-side API gateway
- `services/api`: Spring Boot endpoints and PostgreSQL repositories
- `services/api/src/main/resources/db/migration`: SQL migrations
- `compose.yaml`: local services

## Roadmap
Develop more authenticated synthetic workflows, fine-grained RBAC, auditing, integration tests and security reviews before considering clinical deployment.
