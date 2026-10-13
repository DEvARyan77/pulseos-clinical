ALTER TABLE patient_records ADD COLUMN IF NOT EXISTS contact_email VARCHAR(254);
CREATE TABLE patient_notifications (
 id UUID PRIMARY KEY,
 patient_id UUID NOT NULL REFERENCES patient_records(id),
 owner VARCHAR(80) NOT NULL,
 channel VARCHAR(20) NOT NULL CHECK (channel IN ('IN_APP','EMAIL')),
 status VARCHAR(20) NOT NULL CHECK (status IN ('CREATED','SENT','FAILED')),
 created_at TIMESTAMPTZ NOT NULL,
 message VARCHAR(240) NOT NULL
);
CREATE INDEX patient_notifications_owner ON patient_notifications(patient_id,owner,created_at DESC);
CREATE TABLE staff_messages (
 id UUID PRIMARY KEY,
 sender VARCHAR(80) NOT NULL,
 recipient VARCHAR(80) NOT NULL,
 content VARCHAR(1200) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL,
 read_at TIMESTAMPTZ
);
CREATE INDEX staff_messages_inbox_idx ON staff_messages(recipient,created_at DESC);
CREATE INDEX staff_messages_sent_idx ON staff_messages(sender,created_at DESC);
