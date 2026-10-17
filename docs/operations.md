# Demo release and deployment checks

PulseOS supports fictional records only. Never import real patient information.
Passing automated checks does not establish clinical, security or compliance readiness.

## Repeatable checks

Use Java 21, Maven 3.9+, and Node 22 with npm:

```sh
mvn -B -f services/api/pom.xml verify
cd apps/web
npm ci --no-audit --no-fund
npm run typecheck
npm run lint
npm test
npm run build
```

CI runs both backend verification and all four frontend checks. The frontend
lockfile is committed and used by CI and the Docker build. Backend unit tests exercise authorization, ownership, validation and scheduling logic.
CI also starts a disposable PostgreSQL 17 database and boots the API against all
Flyway migrations with Hibernate validation. HTTP smoke tests check the real
security chain, revoked accounts, owner isolation and aggregate reminder counts
beyond the history limit. These tests run only when PULSEOS_TEST_DATABASE_URL is
set; without it, the two PostgreSQL tests are skipped. Never point this variable
at a deployment database. This still does not replace a hosting smoke test.

## Host configuration requiring operator access

- Provide PostgreSQL connection settings and a private Base64 JWT signing key
  containing at least 32 random bytes. Use the hosting secret store.
- Enable `PULSEOS_AUTH_ENABLED=true` on both services for persisted workflows.
  Point the web server's `API_INTERNAL_URL` at the reachable API origin.
- Keep registration off unless deliberately enabled. Configure an invitation
  code when restricting demo signup. Never grant admin privileges to public signup.
- Configure an email provider and sender separately. Leave mail disabled until
  ready; never commit API keys, SMTP passwords or relay keys.
- Set `PULSEOS_PUBLIC_URL` to the HTTPS frontend origin for QR links.
  `PULSEOS_DEMO_EMAIL_ALLOWLIST` must contain only approved test inboxes.
- Automatic reminders remain off by default. Enable
  `PULSEOS_APPOINTMENT_REMINDERS_ENABLED=true` only after testing the allowlist
  and provider. Sleeping/free instances can miss reminder windows.
- SMTP requires STARTTLS and hostname verification. Servers without valid TLS
  fail closed. On hosts that block SMTP ports, use a configured HTTPS provider.
- For Apps Script, publish/authorize the current relay deployment in Google.
  Saving source alone does not update its deployed version. Provider acceptance
  does not establish inbox delivery.
- Use HTTPS for browser access. Secure cookies require HTTPS; the default Compose
  HTTP port needs a local TLS reverse proxy before authenticated browser use.
  Compose binds the web port to loopback by default.
- Back up the fictional database before an upgrade, deploy both services, and
  inspect API startup for successful Flyway migrations through V17.
  Test clinician ownership, revoked/disabled roles, and patient portal matching
  with separate synthetic accounts.

Compose forwards the optional mail/reminder settings from the local environment;
they default to disabled/empty. The API and database have no published host ports.
No hosting resource is created or changed merely by committing this configuration.

## Known limits

This release has no MFA, comprehensive distributed abuse protection, proven
clinical audit system, independent penetration test, delivery SLA, or regulatory
certification. Email is synchronous for booking and status changes; if a request
times out, refresh the appointment list before retrying because the operation
may still complete. Provider failures and unknown automatic reminder attempts
require manual investigation without blind resend.

A GitHub Actions success verifies that commit's automated checks. Separately
verify hosting rollout, health, migrations, and a fictional end-to-end visit before
considering the demo deployment operational. No real mailbox delivery or live
deployment should be assumed from the source repository alone.
