# Architecture

Browser → Next.js web server → Spring Boot API → PostgreSQL

This is a fictional portfolio/demo application, not a certified EHR. Only synthetic
records may be entered, and successful checks do not establish compliance readiness.

## Identity and session boundaries

Next.js route handlers exchange credentials with the API and store the short-lived
JWT in an HttpOnly, Secure, SameSite=Lax cookie in production. Browser JavaScript
does not receive the bearer token. Server requests use `API_INTERNAL_URL` and
no-store caching. State-changing cookie gateways check request origins.

Persisted accounts support CLINICIAN, ADMIN and PATIENT roles. The backend checks
current database roles and enabled state for clinician and administrator actions,
so stale JWT claims cannot retain revoked privileges. Public registration never
creates administrators. Patient portal access checks the current enabled patient
account and its verified email against fictional registry contacts.

Clinicians own their registry, notes, appointments and reminder history.
Administrators manage staff but do not automatically gain access to patient or
appointment APIs. Shared care-team features use explicit assignments.

## Scheduling and reminders

PostgreSQL stores appointment state and clinician availability. A per-clinician
transactional advisory lock serializes booking conflict checks. Optimistic versions
protect concurrent appointment and availability changes; conflicting writes return
a safe 409 response. Thirty-minute slots respect configured working hours, breaks,
blocked dates and time zones. Unconfigured availability retains legacy unrestricted
scheduling.

The [appointment dashboard](appointment-dashboard.md) uses owner-scoped calendar-day
queries and aggregate reminder counts. Missed check-ins are display flags rather
than persisted status changes.

Automatic reminders are opt-in. PostgreSQL claims prevent duplicate attempts for an
appointment/time pair across replicas. Only allowlisted test recipients are eligible.
Neither provider acceptance nor stored configuration proves inbox delivery. Unknown
attempts are not retried automatically.

## Operations

Flyway owns schema changes; Hibernate validates the schema. The API uses bearer
authorization and does not accept browser auth cookies. Compose publishes only the
frontend on loopback and runs both application containers without root privileges.
See [deployment checks and limits](operations.md) and [authentication](authentication.md).
