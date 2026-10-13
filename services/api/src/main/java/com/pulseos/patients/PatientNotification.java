package com.pulseos.patients;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="patient_notifications")
public class PatientNotification {
  @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
  @Column(name="patient_id",nullable=false) private UUID patientId;
  @Column(nullable=false,length=80) private String owner;
  @Column(nullable=false,length=20) private String channel;
  @Column(nullable=false,length=20) private String status;
  @Column(nullable=false,length=240) private String message;
  @Column(name="created_at",nullable=false) private Instant createdAt=Instant.now();
  protected PatientNotification(){}
  public PatientNotification(UUID patientId,String owner,String channel,String status,String message){
    this.patientId=patientId;this.owner=owner;this.channel=channel;this.status=status;this.message=message;
  }
  public UUID getId(){return id;}
  public UUID getPatientId(){return patientId;}
  public String getOwner(){return owner;}
  public String getChannel(){return channel;}
  public String getStatus(){return status;}
  public String getMessage(){return message;}
  public Instant getCreatedAt(){return createdAt;}
}
