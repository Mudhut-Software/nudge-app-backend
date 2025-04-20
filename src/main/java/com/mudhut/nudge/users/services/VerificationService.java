package com.mudhut.nudge.users.services;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mudhut.nudge.email.JavaEmailService;
import com.mudhut.nudge.users.entities.PhoneVerificationToken;
import com.mudhut.nudge.users.entities.User;
import com.mudhut.nudge.users.entities.VerificationToken;
import com.mudhut.nudge.users.repositories.PhoneVerificationTokenRepository;
import com.mudhut.nudge.users.repositories.UserRepository;
import com.mudhut.nudge.users.repositories.VerificationTokenRepository;
import com.mudhut.nudge.utils.UrlService;

@Service
public class VerificationService {

    @Autowired
    VerificationTokenRepository verificationTokenRepository;

    @Autowired
    PhoneVerificationTokenRepository phoneVerificationTokenRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    private JavaEmailService emailService;

    @Autowired
    private UrlService urlService;

    private String generateRandomToken() {
        return UUID.randomUUID().toString();
    }

    // Helper method to generate and save verification token
    public String createVerificationToken(User user) {
        String token = generateRandomToken(); // Implement this method to generate a random token
        VerificationToken verificationToken = new VerificationToken(token, user);
        verificationTokenRepository.save(verificationToken);
        return token;
    }

    private String generateVerificationCode() {
        // Generate a 6-digit code
        return String.format("%06d", new Random().nextInt(1000000));
    }

    // Helper method to generate and save phone verification code
    public String createPhoneVerificationCode(User user) {
        String code = generateVerificationCode();
        PhoneVerificationToken verificationToken = new PhoneVerificationToken(code, user);
        phoneVerificationTokenRepository.save(verificationToken);
        return code;
    }

    public void sendVerificationEmail(User user, String token) {
        // Create verification URL
        String verificationUrl = urlService.buildUrlWithParam("/verify-email", "token", token);

        // Email content
        String subject = "Please verify your email address";

        StringBuilder contentBuilder = new StringBuilder();

        contentBuilder.append("Dear User,\n\n")
                .append("Please click the link below to verify your email address:\n")
                .append(verificationUrl).append("\n\n")
                .append("This link will expire in 24 hours.\n\n")
                .append("Thank you,\n")
                .append("The Nudge App Team");

        String content = contentBuilder.toString();

        // Send the email
        emailService.sendEmail(user.getEmail(), subject, content);
    }

    @Transactional
    public void verifyEmail(String token) {
        VerificationToken verificationToken = verificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid verification token"));

        if (verificationToken.isUsed()) {
            throw new IllegalStateException("Token has already been used");
        }

        if (verificationToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Token has expired");
        }

        User user = verificationToken.getUser();
        user.setEmailVerified(true);
        user.setActive(true); // Activate user account upon email verification

        verificationToken.setUsed(true);

        userRepository.save(user);
        verificationTokenRepository.save(verificationToken);
    }

    @Transactional
    public void verifyPhone(String code) {
        PhoneVerificationToken verificationToken = phoneVerificationTokenRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Invalid verification code"));

        if (verificationToken.isUsed()) {
            throw new IllegalStateException("Code has already been used");
        }

        if (verificationToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Code has expired");
        }

        User user = verificationToken.getUser();
        user.setPhoneVerified(true);

        // If email is already verified, activate the account
        if (user.isEmailVerified()) {
            user.setActive(true);
        }

        verificationToken.setUsed(true);

        userRepository.save(user);
        phoneVerificationTokenRepository.save(verificationToken);
    }
}
