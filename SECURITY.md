# Security — development/test application only

PulseOS is not certified for medical use and must not store real patient information.

- Registration uses an email verification code (6 digits), valid 10 minutes.
- Codes are HMAC-SHA256 hashed before persistence; the key is derived from the private JWT signing key. Failed attempts are limited to five; resend cooldown is 60 seconds per email.
- Pending registration records include BCrypt password hashes; no account is activated before email verification.
- Outbound verification email delivery is fail-closed. Invalid provider configuration cannot silently allow account creation.
- Credentials for Gmail SMTP or HTTPS email providers must remain in deployment secrets, not Git.
- Existing user-scoped patient and appointment APIs remain authorization-protected.
- **An email-verified account is not automatically an authorized clinician**. This app uses fictional data only.

Missing: robust IP-based signup throttling, MFA, password recovery, signed invitation lifecycle, proven secure email infrastructure, comprehensive audit trails, regulatory safeguards, and penetration testing.
