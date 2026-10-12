# PulseOS security status — not clinical production software

Only use **synthetic test records**. Do not use real medical information or deploy for actual patient care.

## Current demonstrations
- Opt-in authentication with BCrypt password hashes, invitation-code enrollment and signed 15-minute JWT sessions
- HttpOnly + Secure (production) + SameSite=Lax session cookie handled by Next.js; no browser-local storage token
- Account records persisted in PostgreSQL
- Demo appointments persisted in PostgreSQL, bound to the authenticated account and restricted to hardcoded `DEMO-1001` through `DEMO-1007` IDs
- Flyway-controlled database migrations and synthetic frontend chart fixtures
- No patient names, messages, clinical notes, or free-text health data accepted by the demo appointment API

## Still missing
- MFA and centralized identity, login throttling and account lockouts
- Proper invitation issuance/revocation, email verification, password resets and revocable sessions
- Trusted audit trails, medical access controls, data retention/backup tests
- Privacy and compliance reviews, deployment penetration tests and clinical validation
- Full integration and end-to-end test coverage

Enabling registration on a publicly accessible service permits anyone with the shared invite code to create a clinician account. Use a strong secret and disable enrollment as soon as setup is complete. Keep database credentials private and rotate exposed credentials.
