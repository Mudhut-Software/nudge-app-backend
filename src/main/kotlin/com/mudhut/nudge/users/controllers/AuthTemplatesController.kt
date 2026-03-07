package com.mudhut.nudge.users.controllers

import com.mudhut.nudge.users.services.VerificationService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@Controller
class AuthTemplatesController(
    private val verificationService: VerificationService
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
        return "verification-result"
    }
}
