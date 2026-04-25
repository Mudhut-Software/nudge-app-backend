package com.mudhut.nudge.users.services.helpers

import com.mudhut.nudge.users.repositories.UserRepository
import org.springframework.stereotype.Component

@Component
class UsernameGenerator(private val userRepository: UserRepository) {

    fun fromEmail(email: String): String {
        val base = sanitize(email.substringBefore('@'))
        if (!userRepository.existsByUsername(base)) return base

        var suffix = 2
        while (userRepository.existsByUsername("$base$suffix")) {
            suffix++
        }
        return "$base$suffix"
    }

    private fun sanitize(localPart: String): String {
        val cleaned = localPart.lowercase().replace(Regex("[^a-z0-9_-]"), "")
        val trimmed = cleaned.take(48).ifBlank { "user" }
        return if (trimmed.length < 2) trimmed.padEnd(2, '0') else trimmed
    }
}
