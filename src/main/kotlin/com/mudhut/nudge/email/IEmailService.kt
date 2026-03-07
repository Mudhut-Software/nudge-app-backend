package com.mudhut.nudge.email

interface IEmailService {
    fun sendEmail(to: String, subject: String, content: String)
}
