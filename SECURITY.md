# PulseOS security status

**Development / portfolio demonstration only. Not authorized for real patient data, treatment decisions, or regulated healthcare workloads.**

## Existing controls
- Spring Security permits only health/status and login endpoints without authorization. `/api/v1/auth/me` requires a valid signed bearer token when authentication is enabled; all other unspecified API endpoints are denied.
- `PULSEOS_AUTH_ENABLED` defaults to `false`, and login cannot be enabled without a Base64 signing secret (minimum 32 bytes), an explicit username and an appropriately formatted BCrypt password hash.
- Credentials are checked using Spring Security's `DaoAuthenticationProvider` and `BCryptPasswordEncoder`.
- JWTs are HMAC-SHA256 signed and expire after 15 minutes; decoder verifies issuer, signature, and expiration.
- Next.js only accepts credentials through its server-side API endpoint, stores the access token in a Secure, HttpOnly, SameSite=Lax cookie, and verifies the token with the API server before rendering the protected workspace.
- No clinical data is saved; patients are fictional frontend fixtures.
- Secrets should be configured in a hosting provider's environment, never in version control.

## Not yet implemented
- Database-backed staff identities, invitation and account recovery, MFA/SSO
- Login rate limiting, anomaly detection and account lockouts
- Revocable sessions and key rotation support
- Detailed role and patient-level authorization and immutable audit events
- Clinical data lifecycle and consent controls, backup/restore drills
- Data protection reviews, vulnerability testing and privacy/compliance assessments

**Deployment note:** To guard the UI, set `PULSEOS_AUTH_ENABLED=true` on **both** the frontend and backend, along with the required backend secrets. The default unauthenticated UI uses synthetic demo records and should never be changed to display real patient records without comprehensive security work.
