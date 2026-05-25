package com.mudhut.nudge.email

interface IEmailService {
    fun sendEmail(to: String, subject: String, content: String)

    /**
     * Send a multipart email with an HTML body and a plain-text fallback.
     * Used for branded transactional emails (verification, password reset, etc.).
     */
    fun sendHtmlEmail(to: String, subject: String, htmlContent: String, textContent: String)
}
