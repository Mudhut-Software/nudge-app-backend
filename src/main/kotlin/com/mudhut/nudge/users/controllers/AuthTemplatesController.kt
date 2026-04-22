package com.mudhut.nudge.users.controllers

import com.mudhut.nudge.users.services.VerificationService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@Controller
class AuthTemplatesController(
    private val verificationService: VerificationService,
    @Value("\${nudge.frontend-url:}") private val frontendUrl: String
) {

    @GetMapping("/reset-password")
    fun showResetPasswordForm(@RequestParam token: String, model: Model): String {
        model.addAttribute("token", token)
        return "reset-password"
    }

    @GetMapping("/verify-email")
    fun verifyEmail(@RequestParam token: String, model: Model): String {
        try {
            verificationService.verifyEmail(token)
            model.addAttribute("message", "Your email has been verified successfully. Your account is now active.")
            model.addAttribute("status", "success")
        } catch (e: IllegalArgumentException) {
            model.addAttribute("message", "Invalid verification token.")
            model.addAttribute("status", "error")
        } catch (e: IllegalStateException) {
            model.addAttribute("message", e.message)
            model.addAttribute("status", "error")
        }
        model.addAttribute("loginUrl", loginUrl())
        return "verification-result"
    }

    private fun loginUrl(): String {
        val base = frontendUrl.trimEnd('/')
        return if (base.isBlank()) "/login" else "$base/login"
    }
}
