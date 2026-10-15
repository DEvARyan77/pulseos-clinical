-- Per-clinician booking rules; unconfigured accounts keep existing unrestricted booking.
-- Times and blackout dates are interpreted in the chosen IANA time zone.
CREATE TABLE clinician_availability (
    clinician_username VARCHAR(80) PRIMARY KEY,
    zone_id VARCHAR(80) NOT NULL,
    working_days VARCHAR(90) NOT NULL,
    opens_at TIME NOT NULL,
    closes_at TIME NOT NULL,
    break_start_at TIME,
    break_end_at TIME,
    blocked_dates TEXT NOT NULL DEFAULT '',
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT clinician_availability_hours CHECK (opens_at < closes_at),
    CONSTRAINT clinician_availability_break CHECK (
        (break_start_at IS NULL AND break_end_at IS NULL) OR
        (break_start_at IS NOT NULL AND break_end_at IS NOT NULL
            AND break_start_at >= opens_at AND break_start_at < break_end_at
            AND break_end_at <= closes_at)
    )
);
