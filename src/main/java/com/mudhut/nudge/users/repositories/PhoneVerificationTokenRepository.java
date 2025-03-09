package com.mudhut.nudge.users.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mudhut.nudge.users.entities.PhoneVerificationToken;

import java.util.Optional;

public interface PhoneVerificationTokenRepository extends JpaRepository<PhoneVerificationToken, Long> {
    Optional<PhoneVerificationToken> findByCode(String code);
}
