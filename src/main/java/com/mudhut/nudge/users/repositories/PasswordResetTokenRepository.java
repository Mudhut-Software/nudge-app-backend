package com.mudhut.nudge.users.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mudhut.nudge.users.entities.PasswordResetToken;
import com.mudhut.nudge.users.entities.User;

import java.util.List;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByToken(String token);

    List<PasswordResetToken> findAllByUserAndUsedFalse(User user);
}
