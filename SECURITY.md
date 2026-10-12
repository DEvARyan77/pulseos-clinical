# Security status — demo only

PulseOS remains a **portfolio/development demo**. Never use it for actual patient records or healthcare operations.

### Implemented
- Opt-in staff authentication; HS256 JWTs expire in 15 minutes, token stored only in an HttpOnly, Secure (production) SameSite=Lax cookie on the frontend
- BCrypt password hashing and PostgreSQL staff-account persistence
- Flyway schema migration
- Invite-code gate and opt-in flag for staff registration
- Authenticated backend identity checks before serving the main workspace when auth is enabled
- No real clinical data stored by the backend; the dashboard uses entirely synthetic fixtures

### Not implemented
- Account lockout, credential stuffing / brute-force mitigation or account email verification
- MFA, session revocation, password resets, key rotation or managed identity provider
- Clinical access scopes / row-level policy, server-side auditing, retained logs
- Approved regulated-data hosting, secure operational backups and recovery procedures
- Compliance and penetration assessments

When `PULSEOS_AUTH_ENABLED=false`, the synthetic demo dashboard is intentionally public and login is unavailable. To enable real staff enrollment, set matching feature flags on frontend/backend and configure secure secrets. Disable enrollment promptly after adding staff. Invitation codes are single shared secrets (not per-user invitations), so this is not an enterprise enrollment system.
