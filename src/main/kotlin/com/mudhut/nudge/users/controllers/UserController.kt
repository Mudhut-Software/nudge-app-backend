package com.mudhut.nudge.users.controllers

import com.mudhut.nudge.users.models.*
import com.mudhut.nudge.users.services.ForgotPasswordService
import com.mudhut.nudge.users.services.GoogleAuthService
import com.mudhut.nudge.users.services.LoginService
import com.mudhut.nudge.users.services.LogoutService
import com.mudhut.nudge.users.services.RegistrationService
import com.mudhut.nudge.users.services.UserService
import com.mudhut.nudge.users.services.VerificationService
import com.mudhut.nudge.utils.models.GeneralRequestResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth")
class UserController(
    private val userService: UserService,
    private val loginService: LoginService,
    private val registrationService: RegistrationService,
    private val forgotPasswordService: ForgotPasswordService,
    private val verificationService: VerificationService,
    private val googleAuthService: GoogleAuthService,
    private val logoutService: LogoutService,
) {

    @PostMapping("/register")
    fun registerUser(@Valid @RequestBody request: RegisterRequest): ResponseEntity<UserResponse> =
        ResponseEntity.ok(UserResponse.from(registrationService.createUser(request)))

    @PostMapping("/login")
    fun authenticateUser(@Valid @RequestBody request: LoginRequest): ResponseEntity<AuthResponse> =
        ResponseEntity.ok(loginService.authenticateUser(request))

    @PostMapping("/google")
    fun googleAuth(@Valid @RequestBody request: GoogleAuthRequest): ResponseEntity<AuthResponse> =
        ResponseEntity.ok(googleAuthService.authenticate(request.idToken!!))

    @PostMapping("/verify-email")
    fun verifyEmail(@RequestParam token: String): ResponseEntity<Any> {
        verificationService.verifyEmail(token)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/verify-phone")
    fun verifyPhone(@RequestParam code: String): ResponseEntity<Any> {
        verificationService.verifyPhone(code)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/forgot-password")
    fun forgotPassword(@Valid @RequestBody request: ForgotPasswordRequest): ResponseEntity<GeneralRequestResponse> {
        forgotPasswordService.initiateForgotPassword(request.email!!)
        return ResponseEntity.ok(
            GeneralRequestResponse("An email has been sent to you with password reset instructions")
        )
    }

    @PostMapping("/reset-password")
    fun resetPassword(@Valid @RequestBody request: ResetPasswordRequest): ResponseEntity<GeneralRequestResponse> {
        forgotPasswordService.resetPassword(request)
        return ResponseEntity.ok(
            GeneralRequestResponse("Your password has been reset successfully")
        )
    }

    @PostMapping("/logout")
    fun logout(authentication: Authentication, request: HttpServletRequest): ResponseEntity<Void> {
        logoutService.logout(authentication.name, request.getHeader("Authorization"))
        return ResponseEntity.ok().build()
    }
}
