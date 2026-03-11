package com.mudhut.nudge.email

import com.mailersend.sdk.MailerSend
import com.mailersend.sdk.emails.Email
import com.mailersend.sdk.exceptions.MailerSendException
import com.mudhut.nudge.config.EnvConfig
import org.springframework.stereotype.Service

@Service
class MailerSendEmailService(
    private val envConfig: EnvConfig
) : IEmailService {

    override fun sendEmail(to: String, subject: String, content: String) {
        val email = Email().apply {
            setFrom("name", "mudhutsoftware@gmail.com")
            addRecipient("name", to)
            setSubject(subject)
            setPlain(content)
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
