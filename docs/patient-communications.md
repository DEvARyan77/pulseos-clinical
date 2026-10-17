# Patient registry, notifications, and staff chat

PulseOS is a portfolio application for **fictional records only**. Never enter real patient data, clinical guidance, or confidential messages.

## Patient management

- The clinician's patient registry supports full-name, room, visit reason, status and demo-email search, a status filter, and sorting by recent update, oldest update or patient name.
- Selecting a patient shows the profile, vitals, diagnoses, visit notes, append-only audit timeline, and Notifications section. All patient records are scoped by the signed-in clinician's owner ID.
- An optional **demo contact email** is stored with the patient record. It must be an invented/test identity or the owner's approved test mailbox, never a real patient's address.

## Checking whether an email exists

Email syntax and domain checks do not prove that an individual mailbox exists. Many mail providers intentionally prevent recipient enumeration. For newly registered staff, the **six-digit email verification code** already proves possession of a working inbox; users cannot finish signup until they enter the correct code. The system also checks whether a staff account with that email is already registered before attempting another signup.

For demonstration patient email, the destination must match an operator-approved allowlist. This prevents clinicians from sending test notifications to arbitrary addresses. Enable deliberately in the **PulseOS API Render environment**:

```
PULSEOS_DEMO_EMAIL_ALLOWLIST=test1@example.com,test2@example.com
```

The allowlist is **empty by default**; then no external patient emails can be sent. Only the fixed text "You have a new notification from the PulseOS fictional-data demonstration" is transmitted; it contains no patient name, notes, diagnosis, or room. The notification log records an EMAIL status `SENT` only when the email provider accepted the message. It does **not** guarantee inbox delivery. No full patient chart or arbitrary user-entered message is emailed.

The Apps Script mail relay must also be updated to the new version from `scripts/google-mail-relay.gs` through **Deploy → Manage deployments → Edit → New version → Deploy**, retaining its existing URL and secret. The updated script accepts `kind: "patient"` with an empty code and sends a fixed, styled HTML demo notification with a plain-text fallback. Older relay versions will reject this message type.

## Care team assignments

The **Care Team** tab on each fictional patient profile lets the patient record's owning clinician select an **active Clinician** and **active Admin/Supervisor**. An assignment must match a registered and enabled staff account with the required role; selecting a username or editing the browser request cannot bypass the API's validation.

The owner can change or remove either assignment. Every change is recorded as `CARE_TEAM_UPDATED` in the patient's audit history. Assignments are stored separately in PostgreSQL by migration V8.

Care team assignments are for coordination **only**: they **do not grant** chart, notes, notifications, or history access to the assigned team member. Existing patient records remain visible only to their original owner. This avoids accidentally expanding access to clinical-looking data without a separate authorization review.

Saved assignments provide **Message assigned clinician** and **Message supervisor** shortcuts; those links open `/messages?to=username` and select the recipient from the server-approved staff directory. They never include patient names or other clinical information in URLs or chat content. The messenger remains demo-only and does not represent an on-call roster, automatically route emergency escalations, or provide actual clinical supervision.

## In-app notifications

Clinicians can record an in-app demo notification under the selected patient's **Notifications** tab. This is an *internal staff-visible log*, **not a patient portal or a push notification**. There is no claim that an external patient has read it. Email notifications are separate from these internal records.

## Staff chat

Staff can open `/messages` and chat with other enabled, registered staff members. Admins appear as **Supervisor / Admin**; clinicians appear as **Clinician / Doctor**. Inbox messages refresh every 15 seconds, and staff can mark their received messages as read. This is a near-real-time **internal demo messenger**, not a clinical messaging service. Messages are stored in PostgreSQL and visible only to sender and recipient. The backend enforces current account enabled status and recipient availability.

Care-team message shortcuts select a registered recipient from the staff directory; they do not provide an on-call roster. Do not use this chat for real-time escalation, emergency communications, real patient details, or regulated data.

## Operational limitations

- External delivery remains a best-effort provider operation, not mailbox existence verification.
- New email controls must not be enabled outside approved test mailboxes.
- The system is not HIPAA compliant and does not provide end-to-end encryption, real-time guarantees, consent management, retention controls, production abuse prevention, or a certified patient-facing clinical service.
