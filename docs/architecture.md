# PulseOS architecture

```text
Browser → Next.js App Router (port 3000)
               │ server-side request
               ▼
           Spring Boot API (internal port 8080)
               │
               ▼
        PostgreSQL (internal port 5432)
```

This repository uses separate app and API folders to keep React rendering isolated from the clinical domain.

**Stage 01:** web layout, API connectivity check, infrastructure and CI. No personal health data stored.

**Planned:** a Spring Security authentication layer, API authorization, audited patient/appointment/note workflows, typed frontend service utilities, integration tests and monitoring. All planned features must be reviewed before the repository is used with real data.
