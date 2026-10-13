package com.pulseos.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pending_staff_registrations")
public class PendingRegistration {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, length = 80, unique = true)
    private String username;
    @Column(nullable = false, length = 254, unique = true)
    private String email;
    @Column(name = "account_role", nullable = false, length = 32)
    private String accountRole = "CLINICIAN";
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;
    @Column(name = "verification_hash", nullable = false, length = 64)
    private String verificationHash;
    @Column(nullable = false)
    private int attempts = 0;
    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected PendingRegistration() {}
    public PendingRegistration(String username, String email, String passwordHash,
                               String verificationHash, Instant sentAt, Instant expiresAt) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.verificationHash = verificationHash;
        this.sentAt = sentAt;
        this.expiresAt = expiresAt;
    }
    public PendingRegistration(String username,String email,String passwordHash,String verificationHash,Instant sentAt,Instant expiresAt,StaffRole role){
      this(username,email,passwordHash,verificationHash,sentAt,expiresAt);
      this.accountRole=role.name();
    }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getAccountRole() { return accountRole; }
    public String getVerificationHash() { return verificationHash; }
    public int getAttempts() { return attempts; }
    public Instant getSentAt() { return sentAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void failedAttempt() { attempts++; }
    public void replace(String username, String passwordHash, String verificationHash, Instant now) {
        replace(username,passwordHash,verificationHash,now,StaffRole.valueOf(accountRole));
    }
    public void replace(String username, String passwordHash, String verificationHash, Instant now, StaffRole role) {
        this.accountRole = role.name();
        this.username = username;
        this.passwordHash = passwordHash;
        this.verificationHash = verificationHash;
        this.sentAt = now;
        this.expiresAt = now.plusSeconds(600);
        this.attempts = 0;
    }
}
