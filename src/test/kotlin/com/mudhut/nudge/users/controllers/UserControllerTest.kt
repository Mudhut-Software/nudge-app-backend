package com.mudhut.nudge.users.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.entities.BusinessStatus
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.entities.UserRole
import com.mudhut.nudge.users.models.*
import com.mudhut.nudge.users.services.ForgotPasswordService
import com.mudhut.nudge.users.services.GoogleAuthService
import com.mudhut.nudge.users.services.LoginService
import com.mudhut.nudge.users.services.RegistrationService
import com.mudhut.nudge.users.services.UserService
import com.mudhut.nudge.users.services.VerificationService
import com.mudhut.nudge.config.EnvConfig
import com.mudhut.nudge.config.JwtAuthenticationFilter
import com.mudhut.nudge.config.SecurityConfig
import com.mudhut.nudge.users.services.JwtService
import com.mudhut.nudge.users.services.helpers.NudgeUserDetailsService
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

@WebMvcTest(UserController::class)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var registrationService: RegistrationService

    @MockitoBean
    private lateinit var loginService: LoginService

    @MockitoBean
    private lateinit var verificationService: VerificationService

    @MockitoBean
    private lateinit var forgotPasswordService: ForgotPasswordService

    @MockitoBean
    private lateinit var googleAuthService: GoogleAuthService

    @MockitoBean
    private lateinit var userService: UserService

    @MockitoBean
    private lateinit var jwtService: JwtService

    @MockitoBean
    private lateinit var userDetailsService: NudgeUserDetailsService

    @MockitoBean
    private lateinit var envConfig: EnvConfig

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private fun <T> anyObject(): T {
        Mockito.any<T>()
        @Suppress("UNCHECKED_CAST")
        return null as T
    }

    // --- POST /register ---

    @Test
    fun testRegisterUser_Success() {
        val request = RegisterRequest(
            email = "test@example.com",
            username = "testuser",
            password = "Password123!",
            phoneNumber = "+256759123321",
            role = UserRole.BASIC_USER
        )

        val user = User().apply {
            id = 1L
            email = "test@example.com"
            username = "testuser"
            password = "hashedpassword"
            phoneNumber = "+256759123321"
            role = UserRole.BASIC_USER
            isEmailVerified = false
            isPhoneVerified = false
            isActive = false
        }

        Mockito.`when`(registrationService.createUser(anyObject())).thenReturn(user)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.email").value("test@example.com"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.username").value("testuser"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.phoneNumber").value("+256759123321"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.role").value("BASIC_USER"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.isActive").value(false))
            .andExpect(MockMvcResultMatchers.jsonPath("$.password").doesNotExist())
            .andExpect(MockMvcResultMatchers.jsonPath("$.businesses").isArray)
            .andExpect(MockMvcResultMatchers.jsonPath("$.businesses.length()").value(0))
    }

    @Test
    fun testRegisterUser_ValidationError_MissingEmail() {
        val request = RegisterRequest(
            username = "testuser",
            password = "Password123!",
            phoneNumber = "+256759123321"
        )

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
    }

    @Test
    fun testRegisterUser_ValidationError_MissingUsername() {
        val request = RegisterRequest(
            email = "test@example.com",
            password = "Password123!",
            phoneNumber = "+256759123321"
        )

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
    }

    @Test
    fun testRegisterUser_ValidationError_MissingPassword() {
        val request = RegisterRequest(
            email = "test@example.com",
            username = "testuser",
            phoneNumber = "+256759123321"
        )

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
    }

    // --- POST /login ---

    @Test
    fun testLogin_Success() {
        val request = LoginRequest(
            email = "test@example.com",
            password = "Password123!"
        )

        val userResponse = UserResponse(
            id = 1L,
            email = "test@example.com",
            username = "testuser",
            phoneNumber = "+256759123321",
            role = UserRole.BASIC_USER,
            isEmailVerified = true,
            isPhoneVerified = false,
            isActive = true
        )

        val authResponse = AuthResponse.builder()
            .accessToken("jwt-access-token")
            .refreshToken("jwt-refresh-token")
            .user(userResponse)
            .build()

        Mockito.`when`(loginService.authenticateUser(anyObject())).thenReturn(authResponse)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.accessToken").value("jwt-access-token"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.refreshToken").value("jwt-refresh-token"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.user.id").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.user.username").value("testuser"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.user.email").value("test@example.com"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.user.businesses").isArray)
            .andExpect(MockMvcResultMatchers.jsonPath("$.user.businesses.length()").value(0))
    }

    @Test
    fun testLogin_Success_IncludesBusinessMemberships() {
        val request = LoginRequest(
            email = "owner@example.com",
            password = "Password123!"
        )

        val userResponse = UserResponse(
            id = 7L,
            email = "owner@example.com",
            username = "owner",
            phoneNumber = "+256759123321",
            role = UserRole.BASIC_USER,
            isEmailVerified = true,
            isPhoneVerified = false,
            isActive = true,
            businesses = listOf(
                UserBusinessSummary(id = 42L, status = BusinessStatus.ACTIVE, role = BusinessRole.OWNER),
                UserBusinessSummary(id = 43L, status = BusinessStatus.SUSPENDED, role = BusinessRole.STAFF)
            )
        )

        val authResponse = AuthResponse.builder()
            .accessToken("jwt-access-token")
            .refreshToken("jwt-refresh-token")
            .user(userResponse)
            .build()

        Mockito.`when`(loginService.authenticateUser(anyObject())).thenReturn(authResponse)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.user.businesses.length()").value(2))
            .andExpect(MockMvcResultMatchers.jsonPath("$.user.businesses[0].id").value(42))
            .andExpect(MockMvcResultMatchers.jsonPath("$.user.businesses[0].status").value("ACTIVE"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.user.businesses[0].role").value("OWNER"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.user.businesses[1].id").value(43))
            .andExpect(MockMvcResultMatchers.jsonPath("$.user.businesses[1].status").value("SUSPENDED"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.user.businesses[1].role").value("STAFF"))
    }

    @Test
    fun testLogin_ValidationError_MissingEmail() {
        val request = LoginRequest(password = "Password123!")

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
    }

    @Test
    fun testLogin_ValidationError_MissingPassword() {
        val request = LoginRequest(email = "test@example.com")

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
    }

    // --- POST /verify-email ---

    @Test
    fun testVerifyEmail_Success() {
        Mockito.doNothing().`when`(verificationService).verifyEmail(Mockito.anyString())

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/auth/verify-email")
                .param("token", "valid-token")
        )
            .andExpect(MockMvcResultMatchers.status().isOk)

        Mockito.verify(verificationService).verifyEmail("valid-token")
    }

    // --- POST /verify-phone ---

    @Test
    fun testVerifyPhone_Success() {
        Mockito.doNothing().`when`(verificationService).verifyPhone(Mockito.anyString())

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/auth/verify-phone")
                .param("code", "123456")
        )
            .andExpect(MockMvcResultMatchers.status().isOk)

        Mockito.verify(verificationService).verifyPhone("123456")
    }

    // --- POST /forgot-password ---

    @Test
    fun testForgotPassword_Success() {
        Mockito.doNothing().`when`(forgotPasswordService).initiateForgotPassword(Mockito.anyString())

        val request = ForgotPasswordRequest(email = "test@example.com")

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.message")
                .value("An email has been sent to you with password reset instructions"))

        Mockito.verify(forgotPasswordService).initiateForgotPassword("test@example.com")
    }

    @Test
    fun testForgotPassword_ValidationError_InvalidEmail() {
        val request = ForgotPasswordRequest(email = "not-an-email")

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
    }

    // --- POST /reset-password ---

    @Test
    fun testResetPassword_Success() {
        Mockito.doNothing().`when`(forgotPasswordService).resetPassword(anyObject())

        val request = ResetPasswordRequest(
            token = "valid-reset-token",
            newPassword = "NewPassword123!",
            confirmPassword = "NewPassword123!"
        )

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.message")
                .value("Your password has been reset successfully"))
    }

    @Test
    fun testResetPassword_ValidationError_MissingToken() {
        val request = ResetPasswordRequest(
            newPassword = "NewPassword123!",
            confirmPassword = "NewPassword123!"
        )

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
    }

    @Test
    fun testResetPassword_ValidationError_ShortPassword() {
        val request = ResetPasswordRequest(
            token = "valid-reset-token",
            newPassword = "short",
            confirmPassword = "short"
        )

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
    }

    // --- POST /google ---

    @Test
    fun testGoogleAuth_Success() {
        val authResponse = AuthResponse(
            accessToken = "access-token",
            refreshToken = "refresh-token",
            user = UserResponse(
                id = 1L,
                email = "alice@example.com",
                username = "alice",
                phoneNumber = null,
                role = UserRole.BASIC_USER,
                isEmailVerified = true,
                isPhoneVerified = false,
                isActive = true
            )
        )
        Mockito.`when`(googleAuthService.authenticate(Mockito.anyString())).thenReturn(authResponse)

        val request = GoogleAuthRequest(idToken = "fake-google-id-token")

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.accessToken").value("access-token"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.user.email").value("alice@example.com"))

        Mockito.verify(googleAuthService).authenticate("fake-google-id-token")
    }

    @Test
    fun testGoogleAuth_ValidationError_MissingToken() {
        val request = GoogleAuthRequest()

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
    }
}
