# PulseOS — Clinical Operations Portfolio

Next.js 16, Spring Boot 4.1, PostgreSQL, JWT authentication, patient records, appointment scheduling, clinical notes and activity history.

> **Portfolio / test environment only.** Use fictional patient information, not real patient records. The application is not a certified EHR or approved for real-world healthcare operations.

## Available functionality

- Responsive light/dark/system themes with **amber/yellow and charcoal** accents
- Staff account registration using **email verification**: username, email and password → six-digit email code → activate account → login
- Existing staff accounts continue to work
- User-scoped patient records, editing, visit notes, timestamps and change history
- Authenticated PostgreSQL appointment scheduling for patients in the user's test registry
- Logout and HttpOnly, short-lived JWT session cookie

## Email delivery configuration

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

## Application settings

On both frontend and backend:
```env
PULSEOS_AUTH_ENABLED=true
PULSEOS_REGISTRATION_ENABLED=true
```
Backend also needs a Base64 random 32+ byte JWT signing key, PostgreSQL environment variables, and configured email delivery. Next.js needs `API_INTERNAL_URL` reachable from the frontend server.

Open `/signup`, enter details, check your inbox, verify your code, then open `/login`.

Flyway migrations are in `services/api/src/main/resources/db/migration`.

## Security limitations

Email verification checks mailbox access, **not professional licensure or authorization to access real patient information**. There is no MFA, login throttling, complete revocable-session system, medically compliant hosting, or clinical audit certification. Never store protected health information in this portfolio deployment.
