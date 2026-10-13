package com.pulseos.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pending_password_resets")
public class PasswordResetChallenge {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "verification_hash", nullable = false, length = 64)
    private String verificationHash;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected PasswordResetChallenge() {}

    public PasswordResetChallenge(String email, String verificationHash, Instant now) {
        this.email = email;
        replace(verificationHash, now);
    }

    public String getEmail() { return email; }
    public String getVerificationHash() { return verificationHash; }
    public int getAttempts() { return attempts; }
    public Instant getSentAt() { return sentAt; }
    public Instant getExpiresAt() { return expiresAt; }

    public void failedAttempt() { attempts++; }

    public void replace(String hash, Instant now) {
        verificationHash = hash;
        attempts = 0;
        sentAt = now;
        expiresAt = now.plusSeconds(600);
    }
}
