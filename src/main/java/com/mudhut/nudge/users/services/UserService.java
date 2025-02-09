package com.mudhut.nudge.users.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mudhut.nudge.users.models.LoginRequest;
import com.mudhut.nudge.users.models.PasswordResetToken;
import com.mudhut.nudge.users.models.PhoneVerificationToken;
import com.mudhut.nudge.users.models.RegisterRequest;
import com.mudhut.nudge.users.models.ResetPasswordRequest;
import com.mudhut.nudge.users.models.User;
import com.mudhut.nudge.users.models.UserRole;
import com.mudhut.nudge.users.models.VerificationToken;
import com.mudhut.nudge.users.repositories.PasswordResetTokenRepository;
import com.mudhut.nudge.users.repositories.PhoneVerificationTokenRepository;
import com.mudhut.nudge.users.repositories.UserRepository;
import com.mudhut.nudge.users.repositories.VerificationTokenRepository;
import com.mudhut.nudge.utils.exceptions.UserAlreadyExistsException;

import jakarta.persistence.EntityNotFoundException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private PhoneVerificationTokenRepository phoneVerificationTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String EMAIL_PATTERN = "^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@" +
            "[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$";

    private static final String PHONE_PATTERN = "^\\+?[1-9]\\d{1,14}$";

    private void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long");
        }

        boolean hasLetter = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;

        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) {
                hasLetter = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            } else if (!Character.isWhitespace(c)) {
                hasSpecial = true;
            }
        }

        if (!hasLetter || !hasDigit || !hasSpecial) {
            throw new IllegalArgumentException(
                    "Password must contain at least one letter, one number, and one special character");
        }
    }

    public User createUser(RegisterRequest user) {
        // Validate email format
        if (!Pattern.compile(EMAIL_PATTERN).matcher(user.getEmail()).matches()) {
            throw new IllegalArgumentException("Invalid email format");
        }

        // Validate phone number format (if provided)
        if (user.getPhoneNumber() != null && !user.getPhoneNumber().isEmpty() &&
                !Pattern.compile(PHONE_PATTERN).matcher(user.getPhoneNumber()).matches()) {
            throw new IllegalArgumentException("Invalid phone number format");
        }

        // Check if email already exists
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new UserAlreadyExistsException("Email already registered: " + user.getEmail());
        }

        // Check if phone number already exists (if provided)
        if (user.getPhoneNumber() != null && !user.getPhoneNumber().isEmpty() &&
                userRepository.existsByPhoneNumber(user.getPhoneNumber())) {
            throw new UserAlreadyExistsException("Phone number already registered");
        }

        // Validate password
        validatePassword(user.getPassword());

        User newUser = new User();
        newUser.setEmail(user.getEmail());
        newUser.setPassword(passwordEncoder.encode(user.getPassword()));
        newUser.setPhoneNumber(user.getPhoneNumber());
        newUser.setEmailVerified(false);
        newUser.setPhoneVerified(false);
        newUser.setActive(false);
        // If role is not set, set default role
        if (user.getRole() == null) {
            newUser.setRole(UserRole.BASIC_USER);
        } else {
            newUser.setRole(user.getRole());
        }

        try {
            return userRepository.save(newUser);
        } catch (Exception e) {
            throw new RuntimeException("Error occurred while creating user", e);
        }
    }

    public User authenticateUser(LoginRequest loginRequest) {
        User user = userRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(
                        () -> new EntityNotFoundException("User not found with email: " + loginRequest.getEmail()));

        if (!user.isActive()) {
            throw new IllegalStateException("Account is not active");
        }

        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid password");
        }

        return user;
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + id));
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User updateUser(Long id, User userDetails) {
        User user = getUserById(id);
        return userRepository.save(user);
    }

    public void deleteUser(Long id) {
        User user = getUserById(id);
        userRepository.delete(user);
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

    // Helper method to generate and save verification token
    public String createVerificationToken(User user) {
        String token = generateRandomToken(); // Implement this method to generate a random token
        VerificationToken verificationToken = new VerificationToken(token, user);
        verificationTokenRepository.save(verificationToken);
        return token;
    }

    private String generateRandomToken() {
        return UUID.randomUUID().toString();
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

    // Helper method to generate and save phone verification code
    public String createPhoneVerificationCode(User user) {
        String code = generateVerificationCode();
        PhoneVerificationToken verificationToken = new PhoneVerificationToken(code, user);
        phoneVerificationTokenRepository.save(verificationToken);
        return code;
    }

    private String generateVerificationCode() {
        // Generate a 6-digit code
        return String.format("%06d", new Random().nextInt(1000000));
    }

    // Add this method
    @Transactional
    public void initiateForgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found with email: " + email));

        // Invalidate any existing unused tokens for this user
        invalidateExistingPasswordResetTokens(user);

        // Generate new token
        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = new PasswordResetToken(token, user);
        passwordResetTokenRepository.save(resetToken);

        // Here you would typically send an email with the reset link
        // sendPasswordResetEmail(user.getEmail(), token);
    }

    private void invalidateExistingPasswordResetTokens(User user) {
        List<PasswordResetToken> existingTokens = passwordResetTokenRepository.findAllByUserAndUsedFalse(user);
        for (PasswordResetToken token : existingTokens) {
            token.setUsed(true);
            passwordResetTokenRepository.save(token);
        }
    }

    // This method will be used by the reset-password endpoint
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
        validatePassword(request.getNewPassword());

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        resetToken.setUsed(true);

        userRepository.save(user);
        passwordResetTokenRepository.save(resetToken);
    }
}
