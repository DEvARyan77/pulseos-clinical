# Security and deployment status

**Stage 01 is a software foundation, not a production clinical application.**

- No real patient records or protected health information should be entered.
- Only PostgreSQL and the application API can communicate on the private Docker network; only the web app is exposed.
- Database credentials are generated locally and excluded from Git.
- The status endpoint is intentionally public and contains no patient data.
- Authentication, authorization, audit trails, backup/restore testing, privacy controls, monitoring and security assessments are still required.

Do not describe this repository as HIPAA-compliant or ready for real patient workloads.
