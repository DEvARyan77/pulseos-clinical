ALTER TABLE demo_appointments DROP CONSTRAINT IF EXISTS demo_appointments_status_check;
ALTER TABLE demo_appointments ADD CONSTRAINT demo_appointments_status_check
 CHECK(status IN ('SCHEDULED','CHECKED_IN','IN_CARE','COMPLETED','CANCELLED'));
