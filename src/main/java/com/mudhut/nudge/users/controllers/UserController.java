package com.mudhut.nudge.users.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.mudhut.nudge.users.entities.User;
import com.mudhut.nudge.users.models.ForgotPasswordRequest;
import com.mudhut.nudge.users.models.LoginRequest;
import com.mudhut.nudge.users.models.RegisterRequest;
import com.mudhut.nudge.users.models.ResetPasswordRequest;
import com.mudhut.nudge.users.services.UserService;
import com.mudhut.nudge.utils.models.GeneralRequestResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public ResponseEntity<User> registerUser(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(userService.createUser(request));
    }

    @PostMapping("/login")
    public ResponseEntity<User> authenticateUser(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(userService.authenticateUser(request));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestParam String token) {
        userService.verifyEmail(token);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify-phone")
    public ResponseEntity<?> verifyPhone(@RequestParam String code) {
        userService.verifyPhone(code);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<GeneralRequestResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        userService.initiateForgotPassword(request.getEmail());
        GeneralRequestResponse response = new GeneralRequestResponse(
                "An email has been sent to you with password reset instructions");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<GeneralRequestResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(request);
        GeneralRequestResponse response = new GeneralRequestResponse(
                "Your password has been reset successfully");
        return ResponseEntity.ok(response);
    }
}
