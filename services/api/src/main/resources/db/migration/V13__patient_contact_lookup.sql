CREATE INDEX IF NOT EXISTS idx_patient_contact_email ON patient_records(contact_email) WHERE contact_email IS NOT NULL;
