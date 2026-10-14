package com.pulseos.appointments;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "demo_appointments")
public class DemoAppointment {
    public enum Status { SCHEDULED, CHECKED_IN, IN_CARE, COMPLETED, CANCELLED }
    public enum VisitType { FOLLOW_UP, ANNUAL_EXAM, CONSULTATION }

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "created_by", nullable = false, length = 80)
    private String createdBy;

    @Column(name = "patient_id", nullable = false, length = 32)
    private String patientId;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "visit_type", nullable = false, length = 40)
    private VisitType visitType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.SCHEDULED;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Version @Column(nullable = false) private long version;

    protected DemoAppointment() {}

    DemoAppointment(String createdBy, String patientId, Instant scheduledAt, VisitType visitType) {
        this.createdBy = createdBy;
        this.patientId = patientId;
        this.scheduledAt = scheduledAt;
        this.visitType = visitType;
    }

    public UUID getId() { return id; }
    public String getCreatedBy() { return createdBy; }
    public String getPatientId() { return patientId; }
    public Instant getScheduledAt() { return scheduledAt; }
    public VisitType getVisitType() { return visitType; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }

    public void reschedule(Instant newTime) {
        if(status!=Status.SCHEDULED)throw new IllegalStateException("Only scheduled visits can be rescheduled");
        if(newTime==null||!newTime.isAfter(Instant.now()))throw new IllegalArgumentException("Select a future appointment time");
        scheduledAt=newTime;
    }
    public void cancel() { if(status!=Status.SCHEDULED&&status!=Status.CHECKED_IN)throw new IllegalStateException("Cannot cancel an active visit");status = Status.CANCELLED; }
    public void checkIn(){if(status!=Status.SCHEDULED)throw new IllegalStateException("Visit already checked in");status=Status.CHECKED_IN;}
    public void advance(){if(status==Status.CHECKED_IN)status=Status.IN_CARE;else if(status==Status.IN_CARE)status=Status.COMPLETED;else throw new IllegalStateException("Visit cannot advance");}
}
