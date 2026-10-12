CREATE TABLE demo_appointments (
    id UUID PRIMARY KEY,
    created_by VARCHAR(80) NOT NULL,
    patient_id VARCHAR(32) NOT NULL
        CHECK (patient_id IN ('DEMO-1001','DEMO-1002','DEMO-1003','DEMO-1004','DEMO-1005','DEMO-1006','DEMO-1007')),
    scheduled_at TIMESTAMPTZ NOT NULL,
    visit_type VARCHAR(40) NOT NULL CHECK (visit_type IN ('FOLLOW_UP','ANNUAL_EXAM','CONSULTATION')),
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED'
        CHECK (status IN ('SCHEDULED','CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_demo_appointments_owner_schedule ON demo_appointments(created_by, scheduled_at);
