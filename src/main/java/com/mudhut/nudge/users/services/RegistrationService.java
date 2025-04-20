package com.mudhut.nudge.users.services;

import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.mudhut.nudge.users.entities.User;
import com.mudhut.nudge.users.entities.UserRole;
import com.mudhut.nudge.users.models.RegisterRequest;
import com.mudhut.nudge.users.repositories.UserRepository;
import com.mudhut.nudge.users.services.helpers.PasswordValidator;
import com.mudhut.nudge.utils.exceptions.UserAlreadyExistsException;

@Service
public class RegistrationService {
    private static final String EMAIL_PATTERN = "^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@" +
            "[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$";

    private static final String PHONE_PATTERN = "^\\+?[1-9]\\d{1,14}$";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    PasswordValidator passwordValidator;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private VerificationService verificationService;

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
        passwordValidator.validatePassword(user.getPassword());

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
            User savedUser = userRepository.save(newUser);

            // Generate verification token and send verification email
            String token = verificationService.createVerificationToken(newUser);

            verificationService.sendVerificationEmail(savedUser, token);

            return savedUser;
        } catch (Exception e) {
            throw new RuntimeException("Error occurred while creating user", e);
        }
    }

}
