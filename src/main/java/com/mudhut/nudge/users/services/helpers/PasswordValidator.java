package com.mudhut.nudge.users.services.helpers;

import org.springframework.stereotype.Component;

@Component
public class PasswordValidator {

    public void validatePassword(String password) {
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
}
