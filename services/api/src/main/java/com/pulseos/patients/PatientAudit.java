package com.pulseos.patients;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="patient_audit")
public class PatientAudit {
  @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
  @Column(name="patient_id",nullable=false) private UUID patientId;
  @Column(nullable=false,length=80) private String actor;
  @Column(nullable=false,length=64) private String action;
  @Column(name="happened_at",nullable=false) private Instant happenedAt=Instant.now();
  protected PatientAudit(){}
  public PatientAudit(UUID patientId,String actor,String action){this.patientId=patientId;this.actor=actor;this.action=action;}
  public UUID getId(){return id;}
  public UUID getPatientId(){return patientId;}
  public String getActor(){return actor;}
  public String getAction(){return action;}
  public Instant getHappenedAt(){return happenedAt;}
}
