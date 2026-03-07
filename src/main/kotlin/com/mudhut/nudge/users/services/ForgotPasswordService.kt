package com.mudhut.nudge.users.services

import com.mudhut.nudge.email.JavaEmailService
import com.mudhut.nudge.users.entities.PasswordResetToken
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.models.ResetPasswordRequest
import com.mudhut.nudge.users.repositories.PasswordResetTokenRepository
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.users.services.helpers.PasswordValidator
import com.mudhut.nudge.utils.UrlService
import com.mudhut.nudge.utils.exceptions.UserNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
class ForgotPasswordService(
    private val userRepository: UserRepository,
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val emailService: JavaEmailService,
    private val urlService: UrlService,
    private val passwordValidator: PasswordValidator,
    private val passwordEncoder: PasswordEncoder
) {

    private fun invalidateExistingPasswordResetTokens(user: User) {
        val existingTokens = passwordResetTokenRepository.findAllByUserAndUsedFalse(user)
        for (token in existingTokens) {
            token.used = true
            passwordResetTokenRepository.save(token)
        }
    }

    private fun sendPasswordResetEmail(email: String, token: String) {
        val resetUrl = urlService.buildUrlWithParam("/reset-password", "token", token)

        val subject = "Reset Your Password"

        val content = buildString {
            append("Dear User,\n\n")
            append("You have requested to reset your password. Please click the link below to set a new password:\n")
            append(resetUrl).append("\n\n")
            append("This link will expire in 24 hours.\n\n")
            append("If you did not request a password reset, please ignore this email or contact support if you have concerns.\n\n")
            append("Thank you,\n")
            append("The Nudge App Team")
        }

        emailService.sendEmail(email, subject, content)
    }

    @Transactional
    fun initiateForgotPassword(email: String) {
        val user = userRepository.findByEmail(email)
            .orElseThrow { UserNotFoundException("User not found with email: $email") }

        invalidateExistingPasswordResetTokens(user)

        val token = UUID.randomUUID().toString()
        val resetToken = PasswordResetToken(token, user)
        passwordResetTokenRepository.save(resetToken)

        sendPasswordResetEmail(user.email!!, token)
    }

    @Transactional
    fun resetPassword(request: ResetPasswordRequest) {
        val resetToken = passwordResetTokenRepository.findByToken(request.token!!)
            .orElseThrow { IllegalArgumentException("Invalid reset token") }

        if (resetToken.used) {
            throw IllegalStateException("Reset token has already been used")
        }

        if (resetToken.expiryDate!!.isBefore(LocalDateTime.now())) {
            throw IllegalStateException("Reset token has expired")
        }

        val user = resetToken.user!!
        passwordValidator.validatePassword(request.newPassword!!)

        user.password = passwordEncoder.encode(request.newPassword)
        resetToken.used = true

        userRepository.save(user)
        passwordResetTokenRepository.save(resetToken)
    }
}
