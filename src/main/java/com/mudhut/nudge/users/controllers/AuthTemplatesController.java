package com.mudhut.nudge.users.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.mudhut.nudge.users.services.UserService;

@Controller
public class AuthTemplatesController {

    @Autowired
    UserService userService;

    @GetMapping("/reset-password")
    public String showResetPasswordForm(@RequestParam String token, Model model) {
        model.addAttribute("token", token);
        return "reset-password";
    }

    @GetMapping("/verify-email")
    public String verifyEmail(@RequestParam String token, Model model) {
        try {
            userService.verifyEmail(token);
            model.addAttribute("message", "Your email has been verified successfully. Your account is now active.");
            model.addAttribute("status", "success");
        } catch (IllegalArgumentException e) {
            model.addAttribute("message", "Invalid verification token.");
            model.addAttribute("status", "error");
        } catch (IllegalStateException e) {
            model.addAttribute("message", e.getMessage());
            model.addAttribute("status", "error");
        }
        return "verification-result";
    }
}
