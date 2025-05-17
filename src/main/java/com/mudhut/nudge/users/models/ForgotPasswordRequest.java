package com.mudhut.nudge.users.models;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;

public class ForgotPasswordRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    private String email;

    // Getters and setters
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
