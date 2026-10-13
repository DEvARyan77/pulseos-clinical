-- Assignments are coordination-only; patient chart permissions remain owner-scoped.
CREATE TABLE patient_care_teams (
  patient_id UUID PRIMARY KEY REFERENCES patient_records(id) ON DELETE CASCADE,
  clinician_username VARCHAR(80),
  supervisor_username VARCHAR(80),
  updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_care_teams_clinician ON patient_care_teams(clinician_username);
CREATE INDEX idx_care_teams_supervisor ON patient_care_teams(supervisor_username);
