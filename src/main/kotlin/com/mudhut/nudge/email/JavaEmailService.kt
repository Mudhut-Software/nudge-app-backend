package com.mudhut.nudge.email

import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Service

@Service
class JavaEmailService(
    private val emailSender: JavaMailSender
) : IEmailService {

    override fun sendEmail(to: String, subject: String, content: String) {
        val message = SimpleMailMessage().apply {
            setFrom("mudhutsoftware@gmail.com")
            setTo(to)
            setSubject(subject)
            setText(content)
        }
        emailSender.send(message)
    }
}
