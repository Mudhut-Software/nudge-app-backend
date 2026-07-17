package com.mudhut.nudge.email

import org.springframework.context.annotation.Primary
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets

private const val FROM_ADDRESS = "mudhutsoftware@gmail.com"

@Service
@Primary
class JavaEmailService(
    private val emailSender: JavaMailSender
) : IEmailService {

    override fun sendEmail(to: String, subject: String, content: String) {
        val message = SimpleMailMessage().apply {
            from = FROM_ADDRESS
            setTo(to)
            setSubject(subject)
            text = content
        }
        emailSender.send(message)
    }

    override fun sendHtmlEmail(to: String, subject: String, htmlContent: String, textContent: String) {
        val mime = emailSender.createMimeMessage()
        // multipart=true so we can add a plain-text alternative alongside HTML.
        val helper = MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name())
        helper.setFrom(FROM_ADDRESS)
        helper.setTo(to)
        helper.setSubject(subject)
        // text first, then HTML — clients that prefer HTML still pick HTML; the order
        // here only affects MIME structure.
        helper.setText(textContent, htmlContent)
        emailSender.send(mime)
    }
}
