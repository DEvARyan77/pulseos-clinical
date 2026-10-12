# Staff authentication and invitation-only registration

PulseOS uses **synthetic clinical data only**. Real healthcare use requires further engineering and compliance review.

## Deploying authenticated staff accounts

Backend variables (on Render service `pulseos-api` or Docker API):
- `PULSEOS_AUTH_ENABLED=true`
- `PULSEOS_AUTH_JWT_SECRET_BASE64=<a unique 32+-byte random Base64 secret>`
- `PULSEOS_REGISTRATION_ENABLED=true` (temporary, while enrolling staff)
- `PULSEOS_REGISTRATION_INVITE_CODE=<a unique private invitation of 24+ characters>`
- Usual working PostgreSQL datasource variables.

Frontend variables (Render service `pulseos-web` or Docker web):
- `PULSEOS_AUTH_ENABLED=true`
- `PULSEOS_REGISTRATION_ENABLED=true` while you are enrolling accounts
- `API_INTERNAL_URL` points to the backend API, reachable **from the frontend server**.

Generate credentials locally:

```bash
openssl rand -base64 48  # signing key, copy into backend secret environment
openssl rand -hex 24     # invitation code, copy into backend secret environment
```

Start the database-backed backend before testing the frontend. Flyway automatically creates the `staff_accounts` schema.

1. Open `/signup`. Fill a username, email, password (minimum 12 characters) and the invitation code.
2. Successful signup is stored as a BCrypt password hash in PostgreSQL.
3. Open `/login` and sign in using the new staff username/password.
4. Next.js sets an HttpOnly cookie with a 15-minute backend-issued JWT and renders the protected workspace.
5. Disable `PULSEOS_REGISTRATION_ENABLED` on both services after enrollment. Existing accounts can still log in.

If you previously configured `PULSEOS_AUTH_USERNAME` and `PULSEOS_AUTH_PASSWORD_HASH`, the old single staff identity continues to work as a fallback.

**Don't use email addresses or passwords belonging to actual patients.** Invites are shared tokens and not a complete identity-proofing workflow.

## Why the sign-in screens weren't visible before

Previously, `/login` redirected away unless auth was enabled. This revision always renders `/login` and `/signup`; when backend authentication/registration is disabled the form clearly explains why submission is unavailable. The demo dashboard remains available while authentication is disabled.

## Still required for production use
Login throttling, MFA, staff invitation lifecycle, session revocation, email verification, audit trails, fine-grained RBAC, trusted hosting, vulnerability testing, clinical validation and all relevant privacy/regulatory reviews.
