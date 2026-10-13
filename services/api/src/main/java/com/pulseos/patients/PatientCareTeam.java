package com.pulseos.patients;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "patient_care_teams")
public class PatientCareTeam {
    @Id
    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "clinician_username", length = 80)
    private String clinicianUsername;

    @Column(name = "supervisor_username", length = 80)
    private String supervisorUsername;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PatientCareTeam() {}

    public PatientCareTeam(UUID patientId, String clinicianUsername, String supervisorUsername) {
        this.patientId = patientId;
        change(clinicianUsername, supervisorUsername);
    }

    public void change(String clinicianUsername, String supervisorUsername) {
        this.clinicianUsername = clinicianUsername;
        this.supervisorUsername = supervisorUsername;
        this.updatedAt = Instant.now();
    }

    public UUID getPatientId() { return patientId; }
    public String getClinicianUsername() { return clinicianUsername; }
    public String getSupervisorUsername() { return supervisorUsername; }
    public Instant getUpdatedAt() { return updatedAt; }
}
