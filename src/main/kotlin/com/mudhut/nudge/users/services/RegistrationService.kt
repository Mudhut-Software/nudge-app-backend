package com.mudhut.nudge.users.services

import com.mudhut.nudge.businesses.services.BusinessInvitationService
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.entities.UserRole
import com.mudhut.nudge.users.models.RegisterRequest
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.users.services.helpers.PasswordValidator
import com.mudhut.nudge.utils.exceptions.UserAlreadyExistsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.util.regex.Pattern

@Service
class RegistrationService(
    private val userRepository: UserRepository,
    private val passwordValidator: PasswordValidator,
    private val passwordEncoder: PasswordEncoder,
    private val verificationService: VerificationService,
    private val businessInvitationService: BusinessInvitationService
) {
    companion object {
        private const val EMAIL_PATTERN = "^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@" +
                "[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$"
        private const val PHONE_PATTERN = "^\\+?[1-9]\\d{1,14}$"
    }

    fun createUser(request: RegisterRequest): User {
        if (!Pattern.compile(EMAIL_PATTERN).matcher(request.email!!).matches()) {
            throw IllegalArgumentException("Invalid email format")
        }

        if (!request.phoneNumber.isNullOrEmpty() &&
            !Pattern.compile(PHONE_PATTERN).matcher(request.phoneNumber!!).matches()
        ) {
            throw IllegalArgumentException("Invalid phone number format")
        }

        if (userRepository.existsByEmail(request.email!!)) {
            throw UserAlreadyExistsException("Email already registered: ${request.email}")
        }

        if (userRepository.existsByUsername(request.username!!)) {
            throw UserAlreadyExistsException("Username already taken: ${request.username}")
        }

        if (!request.phoneNumber.isNullOrEmpty() &&
            userRepository.existsByPhoneNumber(request.phoneNumber!!)
        ) {
            throw UserAlreadyExistsException("Phone number already registered")
        }

        passwordValidator.validatePassword(request.password!!)

        val newUser = User().apply {
            email = request.email
            username = request.username
            password = passwordEncoder.encode(request.password)
            phoneNumber = request.phoneNumber
            isEmailVerified = false
            isPhoneVerified = false
            isActive = true
            role = request.role ?: UserRole.BASIC_USER
        }

        try {
            val savedUser = userRepository.save(newUser)
            businessInvitationService.resolveInvitationsForNewUser(savedUser.email!!)
            val token = verificationService.createVerificationToken(newUser)
            verificationService.sendVerificationEmail(savedUser, token)
            return savedUser
        } catch (e: Exception) {
            throw RuntimeException("Error occurred while creating user", e)
        }
    }
}
