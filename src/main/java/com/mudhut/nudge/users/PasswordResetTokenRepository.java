package com.mudhut.nudge.users;

import org.springframework.data.jpa.repository.JpaRepository;
import com.mudhut.nudge.users.models.PasswordResetToken;
import com.mudhut.nudge.users.models.User;

import java.util.List;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByToken(String token);

    List<PasswordResetToken> findAllByUserAndUsedFalse(User user);
}
