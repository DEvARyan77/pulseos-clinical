# Architecture

```text
Browser → Next.js app (public)
             │ server-side API calls
             ▼
           Spring Boot API → PostgreSQL
```

## Session flow (when explicitly enabled)
1. User submits credentials from `/login` to the **Next.js route handler** at `POST /api/auth/login`.
2. Next.js calls `POST /api/v1/auth/login` on the Spring Boot service without exposing backend credentials to client-side JS.
3. Spring Security validates the explicit BCrypt staff password hash and issues an HS256 JWT for 15 minutes.
4. Next.js stores the token in an HttpOnly, Secure cookie. It is not written to local storage.
5. Every server render of `/` verifies the JWT via `GET /api/v1/auth/me`. Invalid or missing sessions redirect to `/login`.
6. `POST /api/auth/logout` clears the browser cookie.

No API endpoints for actual patient data exist yet. All clinical panels show hardcoded synthetic examples. A single account is provisioned through environment configuration for an instructional demonstration; this is not a multi-user identity system.

## Operations and trust boundaries
The API is designed to accept bearer authorization, not browser auth cookies. Production environments should restrict API access to trusted networking where available, use TLS, add login throttling, audit trails, a real identity provider, and least-privilege DB credentials before introducing persistent clinical information.
