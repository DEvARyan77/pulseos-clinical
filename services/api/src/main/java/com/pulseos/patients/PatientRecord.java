package com.pulseos.patients;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "patient_records")
public class PatientRecord {
  public enum Status { WAITING, IN_CARE, COMPLETED }
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(nullable = false, length = 80) private String owner;
  @Column(name="contact_email",length=254) private String contactEmail;
  @Column(name = "full_name", nullable = false, length = 120) private String fullName;
  @Column(nullable = false) private int age;
  @Column(nullable = false, length = 32) private String gender;
  @Column(nullable = false, length = 240) private String reason;
  @Column(nullable = false, length = 32) private String room;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private Status status;
  @Column(nullable = false, length = 16) private String bp = "—";
  @Column(name = "heart_rate") private Integer heartRate;
  private Integer oxygen;
  @Column(nullable = false, length = 16) private String temperature = "—";
  @Column(nullable = false, length = 500) private String allergies = "";
  @Column(nullable = false, length = 500) private String diagnoses = "";
  @Column(nullable = false, length = 500) private String medications = "";
  @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();
  @Version private long version;
  protected PatientRecord() {}
  public PatientRecord(String owner) { this.owner = owner; this.status = Status.WAITING; }
  public void apply(PatientController.PatientInput input) {
    fullName = input.fullName().trim(); age = input.age(); gender = input.gender().trim();
    reason = input.reason().trim(); room = input.room().trim(); status = input.status();
    bp = input.bp().trim(); heartRate = input.heartRate(); oxygen = input.oxygen();
    temperature = input.temperature().trim(); allergies = input.allergies().trim();
    diagnoses = input.diagnoses().trim(); medications = input.medications().trim();
    contactEmail = input.contactEmail() == null || input.contactEmail().isBlank()
      ? null : input.contactEmail().strip().toLowerCase(java.util.Locale.ROOT);
    updatedAt = Instant.now();
  }
  public UUID getId(){return id;}
  public String getOwner(){return owner;}
  public String getContactEmail(){return contactEmail;}
  public String getFullName(){return fullName;}
  public int getAge(){return age;}
  public String getGender(){return gender;}
  public String getReason(){return reason;}
  public String getRoom(){return room;}
  public Status getStatus(){return status;}
  public String getBp(){return bp;}
  public Integer getHeartRate(){return heartRate;}
  public Integer getOxygen(){return oxygen;}
  public String getTemperature(){return temperature;}
  public String getAllergies(){return allergies;}
  public String getDiagnoses(){return diagnoses;}
  public String getMedications(){return medications;}
  public Instant getCreatedAt(){return createdAt;}
  public Instant getUpdatedAt(){return updatedAt;}
  public long getVersion(){return version;}
}
