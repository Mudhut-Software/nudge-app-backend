package com.mudhut.nudge.users.services

import com.mudhut.nudge.email.JavaEmailService
import com.mudhut.nudge.users.entities.PhoneVerificationToken
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.entities.VerificationToken
import com.mudhut.nudge.users.repositories.PhoneVerificationTokenRepository
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.users.repositories.VerificationTokenRepository
import com.mudhut.nudge.utils.UrlService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
class VerificationService(
    private val verificationTokenRepository: VerificationTokenRepository,
    private val phoneVerificationTokenRepository: PhoneVerificationTokenRepository,
    private val userRepository: UserRepository,
    private val emailService: JavaEmailService,
    private val urlService: UrlService
) {

    private fun generateRandomToken(): String = UUID.randomUUID().toString()

    fun createVerificationToken(user: User): String {
        val token = generateRandomToken()
        val verificationToken = VerificationToken(token, user)
        verificationTokenRepository.save(verificationToken)
        return token
    }

    private fun generateVerificationCode(): String =
        String.format("%06d", Random().nextInt(1000000))

    fun createPhoneVerificationCode(user: User): String {
        val code = generateVerificationCode()
        val verificationToken = PhoneVerificationToken(code, user)
        phoneVerificationTokenRepository.save(verificationToken)
        return code
    }

    fun sendVerificationEmail(user: User, token: String) {
        val verificationUrl = urlService.buildUrlWithParam("/verify-email", "token", token)

        val subject = "Please verify your email address"

        val content = buildString {
            append("Dear User,\n\n")
            append("Please click the link below to verify your email address:\n")
            append(verificationUrl).append("\n\n")
            append("This link will expire in 24 hours.\n\n")
            append("Thank you,\n")
            append("The Nudge App Team")
        }

        emailService.sendEmail(user.email!!, subject, content)
    }

    @Transactional
    fun verifyEmail(token: String) {
        val verificationToken = verificationTokenRepository.findByToken(token)
            .orElseThrow { IllegalArgumentException("Invalid verification token") }

        if (verificationToken.used) {
            throw IllegalStateException("Token has already been used")
        }

        if (verificationToken.expiryDate!!.isBefore(LocalDateTime.now())) {
            throw IllegalStateException("Token has expired")
        }

        val user = verificationToken.user!!
        user.isEmailVerified = true
        user.isActive = true

        verificationToken.used = true

        userRepository.save(user)
        verificationTokenRepository.save(verificationToken)
    }

    @Transactional
    fun verifyPhone(code: String) {
        val verificationToken = phoneVerificationTokenRepository.findByCode(code)
            .orElseThrow { IllegalArgumentException("Invalid verification code") }

        if (verificationToken.used) {
            throw IllegalStateException("Code has already been used")
        }

        if (verificationToken.expiryDate!!.isBefore(LocalDateTime.now())) {
            throw IllegalStateException("Code has expired")
        }

        val user = verificationToken.user!!
        user.isPhoneVerified = true

        if (user.isEmailVerified) {
            user.isActive = true
        }

        verificationToken.used = true

        userRepository.save(user)
        phoneVerificationTokenRepository.save(verificationToken)
    }
}
