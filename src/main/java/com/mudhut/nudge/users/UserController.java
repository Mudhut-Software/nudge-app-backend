package com.mudhut.nudge.users;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.mudhut.nudge.users.models.LoginRequest;
import com.mudhut.nudge.users.models.RegisterRequest;
import com.mudhut.nudge.users.models.ResetPasswordRequest;
import com.mudhut.nudge.users.models.User;

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
    public ResponseEntity<?> forgotPassword(@RequestParam String email) {
        userService.initiateForgotPassword(email);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(request);
        return ResponseEntity.ok().build();
    }
}
