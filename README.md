# PulseOS — Clinical Operations Workspace

Full-stack medical operations portfolio project built with Next.js, Spring Boot and PostgreSQL.

> **Synthetic records only.** This is not a certified EHR, is not HIPAA-compliant, and is not approved for real patient records.

## Stack
- Frontend: Next.js 16 App Router, React 19, TypeScript
- Backend: Java 21, Spring Boot 4.1, Spring Security 7, REST API
- Infrastructure: PostgreSQL 17, Docker Compose, GitHub Actions

## Development milestones
1. **Foundation:** Next.js, Spring Boot, PostgreSQL, Docker and API status endpoint.
2. **Clinical workspace UI:** Responsive patient queue, fictional charts, tabs, demo timeline and theme switching (Light / Dark / System).
3. **Staff login foundation:** BCrypt-verified single staff account, signed short-lived JWT, authenticated `/api/v1/auth/me`, Next.js server-side route guarding, HttpOnly session cookie, and sign-out control.

The medical charts are still **fictional client-side fixtures**. The backend currently stores **no patient records**.

## Local development
```bash
./scripts/init-env.sh
docker compose up --build
```

Open http://localhost:3000. Authentication is **off by default** to preserve the public demo until deployment secrets are configured.

## Enable staff sign-in (Render or Docker environment)

This is a *single-staff demonstration identity*, not a full identity or access-management system. Set:

**Spring Boot API environment**
- `PULSEOS_AUTH_ENABLED=true`
- `PULSEOS_AUTH_USERNAME=clinician` (replace with your preferred staff username)
- `PULSEOS_AUTH_PASSWORD_HASH` = a 12-round BCrypt hash; never a plaintext password
- `PULSEOS_AUTH_JWT_SECRET_BASE64` = securely generated Base64 secret decoding to at least 32 bytes

**Next.js web environment**
- `PULSEOS_AUTH_ENABLED=true`
- `API_INTERNAL_URL` = your API's reachable URL from the Next.js server (for example, the internal service URL on Docker or your Render API HTTPS URL)

Generate a signing key locally:
```bash
openssl rand -base64 48
```

Generate the password hash interactively without putting the password in shell history:
```bash
docker run --rm -it httpd:2.4-alpine htpasswd -nBC 12 clinician
```
The command prints `clinician:<BCrypt hash>`; copy **only the part after the colon** into `PULSEOS_AUTH_PASSWORD_HASH`. Save keys and hashes in your deployment's secret environment configuration, not in Git. Configure both services, then redeploy both.

When enabled:
- Open `/login`, sign in, and the server validates the token before rendering `/`.
- The Next.js server stores the token in a Secure, HttpOnly, SameSite=Lax cookie for 15 minutes.
- The browser does not receive the JWT in JSON or local storage.
- The Spring Boot API exposes `POST /api/v1/auth/login` and protected `GET /api/v1/auth/me`.
- Sign-out clears the cookie. Tokens already issued remain valid on the API until expiry, so do not use this setup for real clinical records.

Misconfigured enablement on one service cannot establish a valid session. The API rejects startup when authentication is enabled without proper secrets or a BCrypt password hash.

## Security and deployment status
See [SECURITY.md](SECURITY.md). No rate limiting, centralized user database, MFA, session revocation, audit trails, authorization for clinical records, operational monitoring, backup recovery, or privacy/compliance reviews have been implemented. Real patient information is prohibited.

## Next milestones
Database migrations; patients and appointments with scoped authorization; write audit logging; integration tests and production hardening.
