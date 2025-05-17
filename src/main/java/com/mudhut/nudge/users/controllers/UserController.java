package com.mudhut.nudge.users.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.mudhut.nudge.users.entities.User;
import com.mudhut.nudge.users.models.AuthResponse;
import com.mudhut.nudge.users.models.ForgotPasswordRequest;
import com.mudhut.nudge.users.models.LoginRequest;
import com.mudhut.nudge.users.models.RegisterRequest;
import com.mudhut.nudge.users.models.ResetPasswordRequest;
import com.mudhut.nudge.users.services.ForgotPasswordService;
import com.mudhut.nudge.users.services.LoginService;
import com.mudhut.nudge.users.services.RegistrationService;
import com.mudhut.nudge.users.services.UserService;
import com.mudhut.nudge.users.services.VerificationService;
import com.mudhut.nudge.utils.models.GeneralRequestResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private LoginService loginService;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private ForgotPasswordService forgotPasswordService;

    @Autowired
    private VerificationService verificationService;

    @PostMapping("/register")
    public ResponseEntity<User> registerUser(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(registrationService.createUser(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> authenticateUser(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(loginService.authenticateUser(request));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestParam String token) {
        verificationService.verifyEmail(token);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify-phone")
    public ResponseEntity<?> verifyPhone(@RequestParam String code) {
        verificationService.verifyPhone(code);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<GeneralRequestResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        forgotPasswordService.initiateForgotPassword(request.getEmail());
        GeneralRequestResponse response = new GeneralRequestResponse(
                "An email has been sent to you with password reset instructions");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<GeneralRequestResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        forgotPasswordService.resetPassword(request);
        GeneralRequestResponse response = new GeneralRequestResponse(
                "Your password has been reset successfully");
        return ResponseEntity.ok(response);
    }
}
