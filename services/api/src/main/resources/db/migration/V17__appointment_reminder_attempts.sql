-- Durable, at-most-once mail attempts for each appointment's scheduled time.
CREATE TABLE appointment_reminder_attempts (
  id UUID PRIMARY KEY,
  appointment_id UUID NOT NULL REFERENCES demo_appointments(id) ON DELETE CASCADE,
  created_by VARCHAR(80) NOT NULL,
  scheduled_at TIMESTAMPTZ NOT NULL,
  status VARCHAR(16) NOT NULL CHECK (status IN ('CLAIMED','ACCEPTED','FAILED','SKIPPED')),
  attempted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  finished_at TIMESTAMPTZ,
  CONSTRAINT uniq_reminder_for_appointment_time UNIQUE (appointment_id,scheduled_at)
);
CREATE INDEX idx_appointment_reminders_owner_history
  ON appointment_reminder_attempts(created_by,attempted_at DESC);
