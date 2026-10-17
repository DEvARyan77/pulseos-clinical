# Fictional appointment operations dashboard

Open **Appointments** as an authenticated clinician. The dashboard reads only that
account's visits and reminder attempts. Administrators do not automatically gain
access to clinical APIs, and patients continue to use their separate portal.

## Definitions

- **Today's visits:** all appointments on the clinician's current calendar date,
  excluding cancelled visits. The table also shows cancelled visits for context.
- **Pending check-ins:** today's scheduled visits whose 30-minute slot has not
  ended. This includes upcoming visits and visits inside that slot.
- **Missed check-ins:** today's visits still scheduled when the 30-minute slot
  ends. This is a display flag; no permanent no-show status is written.
  A valid late check-in removes the flag on refresh.
- Checked-in, in-care, completed and cancelled visits are never marked missed.
- The configured availability time zone defines today and the displayed visit
  times. Until availability is configured, the dashboard uses UTC. Calendar
  boundaries support daylight-saving transitions, including 23/25-hour days.
- **Reminder statistics:** all automatic reminder attempts started in the last
  24 hours for this clinician, grouped as provider accepted, failed, skipped or
  claimed/outcome unknown. The counts are not limited to the latest 50 history
  rows. Booking, rescheduling and stage-change notices are excluded.
  Rescheduled appointments can have distinct attempts for different timestamps.

Provider acceptance is not proof of inbox delivery. A claimed attempt may still
be processing or may have stopped with an unknown outcome. Failed or ambiguous
attempts are not automatically retried. The dashboard does not send email.

The dashboard refreshes every minute and after successful scheduling changes or
availability updates; manual refresh is also available. A failed refresh retains
the last successful snapshot with an explicit error and update time. Other
calendar/scheduling controls continue to display times in the browser's local zone.

## API and privacy

`GET /api/v1/demo-appointments/dashboard` requires the current clinician role and
enabled account. The Next.js gateway uses the HttpOnly session cookie to send a
bearer token server-side. Responses use `Cache-Control: no-store`. No recipient
addresses, patient notes, QR payloads, or provider credentials are returned.
The dashboard uses the fictional patient names already loaded in the clinician's
own registry. Its queries use the existing owner/time indexes from Flyway migrations V2 and V17.

This is a fictional portfolio/demo application, not a certified EHR, clinical
decision system, compliance product, or approved healthcare workflow.
