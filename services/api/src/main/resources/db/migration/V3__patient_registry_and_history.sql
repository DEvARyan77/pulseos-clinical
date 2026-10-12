CREATE TABLE patient_records (
 id UUID PRIMARY KEY,
 owner VARCHAR(80) NOT NULL,
 full_name VARCHAR(120) NOT NULL,
 age INTEGER NOT NULL CHECK (age BETWEEN 0 AND 120),
 gender VARCHAR(32) NOT NULL,
 reason VARCHAR(240) NOT NULL,
 room VARCHAR(32) NOT NULL,
 status VARCHAR(24) NOT NULL CHECK(status IN ('WAITING','IN_CARE','COMPLETED')),
 bp VARCHAR(16) NOT NULL DEFAULT '—',
 heart_rate INTEGER CHECK (heart_rate IS NULL OR heart_rate BETWEEN 0 AND 250),
 oxygen INTEGER CHECK (oxygen IS NULL OR oxygen BETWEEN 0 AND 100),
 temperature VARCHAR(16) NOT NULL DEFAULT '—',
 allergies VARCHAR(500) NOT NULL DEFAULT '',
 diagnoses VARCHAR(500) NOT NULL DEFAULT '',
 medications VARCHAR(500) NOT NULL DEFAULT '',
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL,
 version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX patient_owner_idx ON patient_records(owner,updated_at DESC);
CREATE TABLE visit_notes (
 id UUID PRIMARY KEY,
 patient_id UUID NOT NULL REFERENCES patient_records(id),
 owner VARCHAR(80) NOT NULL,
 content VARCHAR(4000) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX note_patient_idx ON visit_notes(patient_id,created_at DESC);
CREATE TABLE patient_audit (
 id UUID PRIMARY KEY,
 patient_id UUID NOT NULL REFERENCES patient_records(id),
 actor VARCHAR(80) NOT NULL,
 action VARCHAR(64) NOT NULL,
 happened_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX audit_patient_idx ON patient_audit(patient_id,happened_at DESC);
