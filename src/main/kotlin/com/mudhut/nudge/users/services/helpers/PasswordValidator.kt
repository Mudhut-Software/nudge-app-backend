package com.mudhut.nudge.users.services.helpers

import org.springframework.stereotype.Component

@Component
class PasswordValidator {

    fun validatePassword(password: String) {
        if (password.length < 8) {
            throw IllegalArgumentException("Password must be at least 8 characters long")
        }

        var hasLetter = false
        var hasDigit = false
        var hasSpecial = false

        for (c in password) {
            when {
                c.isLetter() -> hasLetter = true
                c.isDigit() -> hasDigit = true
                !c.isWhitespace() -> hasSpecial = true
            }
        }

        if (!hasLetter || !hasDigit || !hasSpecial) {
            throw IllegalArgumentException(
                "Password must contain at least one letter, one number, and one special character"
            )
        }
    }
}
