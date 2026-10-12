# Email-verified staff registration

PulseOS is a portfolio application. Store only fictional patient data.

## Registration workflow

1. A visitor opens `/signup` and enters a username, email, and a password of at least 12 characters.
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

## Troubleshooting

- "Email delivery is not configured" (503): configure a supported email provider and valid sender credentials.
- "Username or email already registered": use login with an existing account instead.
- "Incorrect or expired code": retry the code from your inbox; if expired, return to the details step to request another.
- "Too many attempts": wait at least 60 seconds before requesting a fresh code.
- API health: open your deployed `/actuator/health` endpoint.

Future security work: login and registration IP throttling, managed identity, domain policies, MFA, session revocation, email delivery monitoring and user administration.
