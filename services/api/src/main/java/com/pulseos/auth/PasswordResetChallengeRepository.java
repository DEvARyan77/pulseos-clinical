package com.pulseos.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetChallengeRepository extends JpaRepository<PasswordResetChallenge, UUID> {
    Optional<PasswordResetChallenge> findByEmail(String email);
    long deleteByExpiresAtBefore(Instant now);
}
