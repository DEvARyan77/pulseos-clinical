# PulseOS — Clinical Operations Portfolio

Next.js 16, Spring Boot 4.1, PostgreSQL, JWT authentication, patient records, appointment scheduling, clinical notes and activity history.

> **Portfolio / test environment only.** Use fictional patient information, not real patient records. The application is not a certified EHR or approved for real-world healthcare operations.

## Available functionality

- Responsive light/dark/system themes with **amber/yellow and charcoal** accents
- Staff account registration using **email verification**: username, email and password → six-digit email code → activate account → login
- Designed amber/charcoal HTML email templates for verification and password-reset codes (plain-text fallback)
- Resend signup verification codes after a 60-second cooldown, without retyping the password
- Email-based password recovery with one-time codes at `/forgot-password`
- Existing staff accounts continue to work
- User-scoped patient records, editing, visit notes, timestamps and change history
- Authenticated PostgreSQL appointment scheduling for patients in the user's test registry
- Clinician and verified patient appointment calendars with private `.ics` downloads (whole displayed schedule, selected day, or one visit). Imports are manual snapshots, not automatic Google/Outlook sync; export again after a change.
- Per-clinician PostgreSQL availability with selected IANA time zone, working days, opening hours, optional daily break and up to 90 blocked dates. Once configured, the API rejects new and rescheduled visits outside these windows. Existing bookings remain unchanged; until configured, legacy unrestricted scheduling remains available.
- Opt-in automatic QR appointment reminders, evaluated every five minutes during the 1–24 hour pre-visit window, with provider-result history for clinicians and duplicate prevention across replicas. Requires a continuously running API and approved test mailboxes; see [reminder setup](docs/appointment-reminders.md).
- Clinician-owned [appointment operations dashboard](docs/appointment-dashboard.md): today’s visits, pending and missed check-ins, and complete 24-hour automatic reminder counts in the configured time zone.
- Logout and HttpOnly, short-lived JWT session cookie

## Email delivery configuration

### Gmail address on Render Free (Google Apps Script)

Use the [Gmail HTTPS relay setup guide](docs/gmail-https-relay.md) to send email from your own Gmail account without SMTP or a Gmail app password. It uses an Apps Script Web App and a shared secret held only in Google Script Properties and Render environment variables. **This requires an explicit script deployment and Google authorization before signup can send email.**

The backend supports `PULSEOS_EMAIL_PROVIDER=resend` for HTTPS delivery (Render Free compatible) or `PULSEOS_EMAIL_PROVIDER=smtp` for hosts where SMTP ports are allowed. **Email verification cannot send mail until a provider is configured**. It fails closed with 503; no account is created if sending fails.

For **Render Free**, choose an HTTPS provider:
- `PULSEOS_EMAIL_PROVIDER=resend`
- `PULSEOS_EMAIL_FROM` = an address on your verified sender domain
- `PULSEOS_RESEND_API_KEY` = provider API key stored only in Render environment variables

For **paid hosting or local development** using Gmail SMTP:
- `PULSEOS_EMAIL_PROVIDER=smtp`
- `PULSEOS_EMAIL_FROM` / `PULSEOS_SMTP_USER` = your Gmail address
- `PULSEOS_SMTP_HOST=smtp.gmail.com`
- `PULSEOS_SMTP_PORT=587`
- `PULSEOS_SMTP_PASSWORD` = new Google app password, stored in environment configuration only

Render **Free** blocks outbound SMTP ports 25/465/587. A Gmail app password does not work with Gmail's HTTPS API. See [email setup](docs/authentication.md).

For styled Gmail messages rather than plain text, republish your existing Google Apps Script Web app using the [email-template activation steps](docs/gmail-https-relay.md#why-you-are-currently-getting-plain-text-emails). The branded email template requires updating your Apps Script's deployed version (**Deploy → Manage deployments → Edit → New version**); saving `Code.gs` alone is insufficient.

## Application settings

On both frontend and backend:
```env
PULSEOS_AUTH_ENABLED=true
PULSEOS_REGISTRATION_ENABLED=true
```
Backend also needs a Base64 random 32+ byte JWT signing key, PostgreSQL environment variables, and configured email delivery. Next.js needs `API_INTERNAL_URL` reachable from the frontend server.

Open `/signup`, enter details, check your inbox, verify your code, then open `/login`.

Flyway migrations are in `services/api/src/main/resources/db/migration`.


## Admin and clinician access (RBAC)

PulseOS has two roles: **CLINICIAN** and **ADMIN**. Every new email-verified signup receives CLINICIAN. Existing accounts are unchanged. An ADMIN manages synthetic staff accounts at `/admin`; a CLINICIAN works with their own fictional patient registry and appointments. ADMIN does **not** automatically gain access to patient or appointment APIs.

The API checks each account's current database role and enabled state on every staff-management and clinical operation. Client-side navigation does not grant permissions. The Admin dashboard can promote/demote *other* staff members and enable/disable them; an admin cannot demote or disable themselves. This avoids accidentally locking out the last active admin. The optional legacy environment-based sign-in remains CLINICIAN-only.

**One-time admin setup:** After creating and verifying your own staff account, use the connected Render PostgreSQL SQL console (privileged infrastructure access, not the public app) to promote only the intended account:

```sql
UPDATE staff_accounts
SET role = 'ADMIN'
WHERE email = 'your-verified-account@example.com'
  AND enabled = true
RETURNING username, email, role;
```

Replace the example email with the actual verified staff account. If the query returns zero rows, that account has not been registered or the email does not match. Sign out and sign in again to issue a JWT with the correct role; then open `/admin`. Do not add automatic admin privileges to public signup or commit personal credentials.

**Permission matrix**

| Capability | Clinician | Admin |
|---|---|---|
| Own fictional patient records, notes and appointments | Yes | No |
| Read-only list of the 100 most recently updated fictional patient records | No | Yes |
| List all staff accounts | No | Yes |
| Assign Admin or Clinician roles to other staff | No | Yes |
| Enable or disable other staff accounts | No | Yes |
| Assign own role or disable self | No | No |

This is a synthetic portfolio, not a production clinical authorization implementation. Consider a separate audit log, MFA, fine-grained permissions, and stronger approval workflows before any production or regulated use.

## Patient management and staff communications

The clinician registry supports enhanced search, status filtering, sorting, patient profile, visit history and audit events. It also includes account-scoped **demo notification history** and test-only email notifications. Admins and clinicians can exchange internal staff messages at `/messages`.

**Sending patient demo email is disabled by default.** To opt in, configure `PULSEOS_DEMO_EMAIL_ALLOWLIST` as a comma-separated list of approved test inboxes on the API's Render environment. The new Google Apps Script relay must be republished to support the fixed `patient` message purpose. No clinical details are sent. A successful provider response is not proof that an inbox exists or received the email; registered staff still verify ownership with a one-time email code.

See [Patient communications and safety](docs/patient-communications.md). Never enter real patient data in this portfolio application.

## Security limitations

Email verification checks mailbox access, **not professional licensure or authorization to access real patient information**. There is no MFA, login throttling, complete revocable-session system, medically compliant hosting, or clinical audit certification. Never store protected health information in this portfolio deployment.


## Verification and deployment

See [repeatable checks, operator configuration and known limits](docs/operations.md). CI success does not verify a live deployment or clinical/compliance readiness.
