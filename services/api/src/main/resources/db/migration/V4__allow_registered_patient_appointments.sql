ALTER TABLE demo_appointments DROP CONSTRAINT IF EXISTS demo_appointments_patient_id_check;
ALTER TABLE demo_appointments ALTER COLUMN patient_id TYPE VARCHAR(64);
