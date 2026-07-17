package com.mudhut.nudge.email

import com.mailersend.sdk.MailerSend
import com.mailersend.sdk.emails.Email
import com.mailersend.sdk.exceptions.MailerSendException
import com.mudhut.nudge.config.EnvConfig
import org.springframework.stereotype.Service

private const val FROM_NAME = "name"
private const val FROM_ADDRESS = "mudhutsoftware@gmail.com"

@Service
class MailerSendEmailService(
    private val envConfig: EnvConfig
) : IEmailService {

    override fun sendEmail(to: String, subject: String, content: String) {
        send(to, subject) { setPlain(content) }
    }

    override fun sendHtmlEmail(to: String, subject: String, htmlContent: String, textContent: String) {
        send(to, subject) {
            setHtml(htmlContent)
            setPlain(textContent)
        }
    }

    private inline fun send(to: String, subject: String, configure: Email.() -> Unit) {
        val email = Email().apply {
            setFrom(FROM_NAME, FROM_ADDRESS)
            addRecipient(FROM_NAME, to)
            setSubject(subject)
            configure()
        }

        val ms = MailerSend()
        ms.setToken(envConfig.mailerSendApiToken)

        try {
            val response = ms.emails().send(email)
            println(response.messageId)
        } catch (e: MailerSendException) {
            e.printStackTrace()
        }
    }
}
