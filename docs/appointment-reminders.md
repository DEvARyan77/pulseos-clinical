# Automated demo appointment reminders

PulseOS reminders are **opt-in** and **fictional-data only**. They reuse the
currently deployed appointment QR notice. No new Google Apps Script deployment
or Google Calendar connection is required.

## How to activate

On the **API service** (for example, Render), set:

`PULSEOS_APPOINTMENT_REMINDERS_ENABLED=true`

Keep the existing mail configuration (`PULSEOS_EMAIL_PROVIDER` and provider-specific keys) and configure
`PULSEOS_DEMO_EMAIL_ALLOWLIST` with only explicitly approved **test inboxes**.
Do not enable reminders against real patient contacts. The default is off.

The backend runs a scan once every five minutes after a one-minute startup
delay. A demo visit is eligible when it is scheduled to begin **more than one
hour and at most 24 hours** after the current scan. Reminders are not issued
to canceled, checked-in, in-care, or completed visits, or for disabled clinician
accounts. A single existing appointment QR email is sent; the email's subject
is the same as the appointment notice, rather than a new reminder template.
An appointment booked less than one hour ahead will not receive a reminder.

## Delivery history

At **Appointments → Automatic appointment reminders**, signed-in clinicians
can see the latest 50 attempt records for their **own account**. The history
does not store recipient addresses or patient information.

- **Provider accepted**: The configured mail provider reported acceptance;
  inbox delivery cannot be guaranteed.
- **Provider failed**: The mail operation returned an error or was rejected.
- **Not eligible**: The patient test address is not allowlisted, the registered
  clinician is disabled, or the visit is no longer valid.
- **Outcome unknown**: The service stopped after claiming an attempt; the
  status was never finalized.

A PostgreSQL unique constraint on appointment ID and appointment timestamp
prevents duplicate automatic attempts across service replicas. If a scheduled
time changes, the new timestamp can create a **new** attempt in the appropriate
window. The system intentionally does **not retry** unsuccessful or ambiguous
attempts; providers may accept a request before the API times out, and blind
retries would risk duplicate messages.

## Operational requirements

The API needs a **continuously running** process for best-effort scheduled
delivery. Free services that idle or scale to zero can miss the reminder window.
A saved configuration does not mean the scheduling job executed, and this is
not a guaranteed delivery service. Use fictional test records only.
