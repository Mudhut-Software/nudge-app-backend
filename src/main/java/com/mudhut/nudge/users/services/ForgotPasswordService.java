package com.mudhut.nudge.users.services;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mudhut.nudge.email.JavaEmailService;
import com.mudhut.nudge.users.entities.PasswordResetToken;
import com.mudhut.nudge.users.entities.User;
import com.mudhut.nudge.users.models.ResetPasswordRequest;
import com.mudhut.nudge.users.repositories.PasswordResetTokenRepository;
import com.mudhut.nudge.users.repositories.UserRepository;
import com.mudhut.nudge.users.services.helpers.PasswordValidator;
import com.mudhut.nudge.utils.UrlService;
import com.mudhut.nudge.utils.exceptions.UserNotFoundException;

@Service
public class ForgotPasswordService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private JavaEmailService emailService;

    @Autowired
    private UrlService urlService;

    @Autowired
    PasswordValidator passwordValidator;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private void invalidateExistingPasswordResetTokens(User user) {
        List<PasswordResetToken> existingTokens = passwordResetTokenRepository.findAllByUserAndUsedFalse(user);
        for (PasswordResetToken token : existingTokens) {
            token.setUsed(true);
            passwordResetTokenRepository.save(token);
        }
    }

    private void sendPasswordResetEmail(String email, String token) {
        // Create password reset URL
        String resetUrl = urlService.buildUrlWithParam("/reset-password", "token", token);

        // Email content
        String subject = "Reset Your Password";

        StringBuilder contentBuilder = new StringBuilder();
        contentBuilder.append("Dear User,\n\n")
                .append("You have requested to reset your password. Please click the link below to set a new password:\n")
                .append(resetUrl).append("\n\n")
                .append("This link will expire in 24 hours.\n\n")
                .append("If you did not request a password reset, please ignore this email or contact support if you have concerns.\n\n")
                .append("Thank you,\n")
                .append("The Nudge App Team");

        String content = contentBuilder.toString();

        // Send the email
        emailService.sendEmail(email, subject, content);
    }

    @Transactional
    public void initiateForgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));

        // Invalidate any existing unused tokens for this user
        invalidateExistingPasswordResetTokens(user);

        // Generate new token
        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = new PasswordResetToken(token, user);
        passwordResetTokenRepository.save(resetToken);

        // Here you would typically send an email with the reset link
        sendPasswordResetEmail(user.getEmail(), token);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new IllegalArgumentException("Invalid reset token"));

        if (resetToken.isUsed()) {
            throw new IllegalStateException("Reset token has already been used");
        }

        if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Reset token has expired");
        }

        User user = resetToken.getUser();
        passwordValidator.validatePassword(request.getNewPassword());

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        resetToken.setUsed(true);

        userRepository.save(user);
        passwordResetTokenRepository.save(resetToken);
    }

}
