# Email-verified staff registration

PulseOS is a portfolio application. Store only fictional patient data.

## Registration workflow

1. A visitor opens `/signup` and enters a username, email, and a password of at least 8 characters.
2. Backend validates the input, hashes the password with BCrypt, and generates a random six-digit verification code.
3. The pending account and HMAC-hashed code are saved in PostgreSQL. The raw code is not stored or logged.
4. The code is emailed to the address supplied. If sending fails, pending registration rolls back and the user sees an error.
5. The visitor enters the code in PulseOS. A valid code activates the account and the user can then sign in.
6. Codes expire in 10 minutes, a new code cannot be requested within 60 seconds, and five incorrect attempts lock the current code.

Existing verified staff accounts remain valid. The previous shared invitation-code field and signup requirement have been removed. The old `PULSEOS_REGISTRATION_INVITE_CODE` variable is no longer read by this code.

## Enable email sending

### Gmail on Render Free without SMTP

Follow the [Google Apps Script HTTPS relay guide](gmail-https-relay.md). No app password is needed; it requires manual authorization by the Gmail account owner.

### Render Free — HTTPS email API

Render Free blocks outbound ports 25, 465 and 587. Gmail app passwords authenticate to SMTP, not the Gmail HTTPS API. Configure a transactional HTTPS email provider, for example Resend:

- Sign up with the provider and verify a sender domain.
- Set backend secrets: `PULSEOS_EMAIL_PROVIDER=resend`, `PULSEOS_EMAIL_FROM=<verified sender address>`, and `PULSEOS_RESEND_API_KEY=<private key>`.
- Set `PULSEOS_AUTH_ENABLED=true` and `PULSEOS_REGISTRATION_ENABLED=true` on both frontend and backend.
- Set `API_INTERNAL_URL` on the frontend to the actual backend URL.

The sender must be verified. No API key means no email, and registration safely fails with an error. Do not put any provider key in GitHub.

### Local / paid host — Gmail SMTP

If outbound SMTP is supported:
- `PULSEOS_EMAIL_PROVIDER=smtp`
- `PULSEOS_EMAIL_FROM=<gmail address>`
- `PULSEOS_SMTP_HOST=smtp.gmail.com`
- `PULSEOS_SMTP_PORT=587`
- `PULSEOS_SMTP_USER=<gmail address>`
- `PULSEOS_SMTP_PASSWORD=<newly generated app password>`

A secret pasted in a conversation should be treated as exposed and rotated. The old app password is intentionally not committed or configured on the SMTP-blocked free service.


### Render cold-start behavior

On free Render instances, Spring Boot may take over two minutes to start after sleeping. The signup and login forms now ping the API health endpoint on load and wait for it to become ready before sending passwords or email verification requests. This avoids wasting a 90-second POST timeout during API startup. A progress message appears while the server starts, with a bounded wait and an explicit error if it never becomes healthy.

A ready API **does not mean that email delivery is configured**. If the request then returns an email-delivery error, set up an SMTP connection that works in your hosting environment, Google Apps Script HTTPS relay, or an HTTPS email provider. Never use patient information for registration tests.

### Registration failure diagnostics

Signup failures are categorized without logging submitted passwords, OTP values, email addresses, or private mail-provider URLs. The Spring Boot API returns `MAIL_DELIVERY_FAILED` (503), `REGISTRATION_STORAGE_UNAVAILABLE` (503), or `REGISTRATION_INTERNAL_ERROR` (500). The Next.js proxy preserves these status codes instead of incorrectly turning all server problems into the same 503.

For a Google Apps Script relay rejection, check **pulseos-api → Logs**. Safe entries can identify `shared_key_rejected` (the key differs between Render and Script Properties), `script_execution_error` (check Apps Script → Executions), `invalid_delivery_request`, or an upstream HTTP response. These status labels never include the email address, verification code, relay secret, or full provider response.

**Operational check:** Ensure `PULSEOS_EMAIL_PROVIDER=google-script`, `PULSEOS_EMAIL_FROM` is the sender address, `PULSEOS_GOOGLE_SCRIPT_URL` is the deployed `/exec` URL, and `PULSEOS_GOOGLE_SCRIPT_KEY` matches the Apps Script property `PULSEOS_MAIL_RELAY_KEY`. Do not paste those secrets into chats or GitHub.

## Authentication upgrades

- **Designed transactional emails:** PulseOS-styled yellow/charcoal HTML email with a large six-digit code, 10-minute expiry notice, plain-text alternative, and privacy disclaimer. SMTP and Resend send HTML directly; the Google Apps Script relay must be republished with the latest code.
- **Resend signup code:** A button on the verification step requests another challenge without requiring the user to type their password again. The server enforces a 60-second cooldown, six-digit codes, a 10-minute expiry, and a five-attempt limit.
- **Forgot password:** On `/login`, open **Forgot your password?** to request a reset code; go to `/forgot-password`, enter the six-digit email code and a new password (8–128 characters), then sign in. Reset codes are HMAC-hashed in PostgreSQL, expire in 10 minutes, and lock after five invalid entries. The server returns a generic response for both known and unknown addresses to avoid exposing registered accounts.
- New recovery challenge storage is created by Flyway migration V6. The signup invitation-code workflow is still removed. Existing accounts and passwords remain intact until the owner completes a password reset.

Security scope: password changes do not immediately revoke any previously issued JWT; these sessions expire within 15 minutes. This remains a synthetic-data portfolio, not a real clinical system. Public password recovery needs additional abuse controls before production use.


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
| List all staff accounts | No | Yes |
| Assign Admin or Clinician roles to other staff | No | Yes |
| Enable or disable other staff accounts | No | Yes |
| Assign own role or disable self | No | No |

This is a synthetic portfolio, not a production clinical authorization implementation. Consider a separate audit log, MFA, fine-grained permissions, and stronger approval workflows before any production or regulated use.

## Troubleshooting

- "Email delivery is not configured" (503): configure a supported email provider and valid sender credentials.
- "Username or email already registered": use login with an existing account instead.
- "Incorrect or expired code": retry the code from your inbox; if expired, return to the details step to request another.
- "Too many attempts": wait at least 60 seconds before requesting a fresh code.
- API health: open your deployed `/actuator/health` endpoint.

Future security work: login and registration IP throttling, managed identity, domain policies, MFA, session revocation, email delivery monitoring and user administration.
