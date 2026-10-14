-- Prevent concurrent rescheduling and QR check-in from silently overwriting each other.
ALTER TABLE demo_appointments ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
