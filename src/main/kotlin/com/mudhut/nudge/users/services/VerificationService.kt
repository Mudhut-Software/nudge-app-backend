package com.mudhut.nudge.users.services

import com.mudhut.nudge.email.IEmailService
import com.mudhut.nudge.users.entities.PhoneVerificationToken
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.entities.VerificationToken
import com.mudhut.nudge.users.repositories.PhoneVerificationTokenRepository
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.users.repositories.VerificationTokenRepository
import com.mudhut.nudge.utils.UrlService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import java.time.LocalDateTime
import java.util.*

private const val VERIFICATION_EMAIL_SUBJECT = "Confirm your email to get started"

@Service
class VerificationService(
    private val verificationTokenRepository: VerificationTokenRepository,
    private val phoneVerificationTokenRepository: PhoneVerificationTokenRepository,
    private val userRepository: UserRepository,
    private val emailService: IEmailService,
    private val urlService: UrlService,
    private val templateEngine: TemplateEngine,
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
        val displayName = user.username ?: "there"

        val context = Context().apply {
            setVariable("displayName", displayName)
            setVariable("verificationUrl", verificationUrl)
            setVariable("subject", VERIFICATION_EMAIL_SUBJECT)
        }
        val html = templateEngine.process("emails/verification", context)
        val text = plainTextVerificationEmail(displayName, verificationUrl)

        emailService.sendHtmlEmail(user.email!!, VERIFICATION_EMAIL_SUBJECT, html, text)
    }

    private fun plainTextVerificationEmail(displayName: String, verificationUrl: String): String =
        """
        Hi $displayName,

        Thanks for signing up for Nudge. Click the link below to verify your email address and activate your account:

        $verificationUrl

        This link expires in 24 hours.

        If you didn't sign up for Nudge, you can safely ignore this email.

        — The Nudge team
        """.trimIndent()

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
