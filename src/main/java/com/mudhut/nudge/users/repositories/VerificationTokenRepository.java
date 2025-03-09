package com.mudhut.nudge.users.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mudhut.nudge.users.entities.VerificationToken;

import java.util.Optional;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {
    Optional<VerificationToken> findByToken(String token);
}
