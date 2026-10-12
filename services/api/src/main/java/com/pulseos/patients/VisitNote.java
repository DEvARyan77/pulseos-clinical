package com.pulseos.patients;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="visit_notes")
public class VisitNote {
  @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
  @Column(name="patient_id",nullable=false) private UUID patientId;
  @Column(nullable=false,length=80) private String owner;
  @Column(nullable=false,length=4000) private String content;
  @Column(name="created_at",nullable=false) private Instant createdAt=Instant.now();
  protected VisitNote(){}
  public VisitNote(UUID patientId,String owner,String content){this.patientId=patientId;this.owner=owner;this.content=content;}
  public UUID getId(){return id;}
  public UUID getPatientId(){return patientId;}
  public String getOwner(){return owner;}
  public String getContent(){return content;}
  public Instant getCreatedAt(){return createdAt;}
}
