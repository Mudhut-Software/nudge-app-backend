package com.mudhut.nudge.users.services;

import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.mudhut.nudge.users.entities.User;
import com.mudhut.nudge.users.models.LoginRequest;
import com.mudhut.nudge.users.repositories.UserRepository;
import com.mudhut.nudge.users.services.helpers.PasswordValidator;

import jakarta.persistence.EntityNotFoundException;

@Service
public class LoginService {

    private static final String EMAIL_PATTERN = "^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@" +
            "[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    PasswordValidator passwordValidator;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public User authenticateUser(LoginRequest loginRequest) {
        // Validate email format
        if (!Pattern.compile(EMAIL_PATTERN).matcher(loginRequest.getEmail()).matches()) {
            throw new IllegalArgumentException("Invalid email format");
        }

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
}
