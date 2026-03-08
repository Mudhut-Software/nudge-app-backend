package com.mudhut.nudge.users.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.entities.UserRole
import com.mudhut.nudge.users.models.RegisterRequest
import com.mudhut.nudge.users.services.ForgotPasswordService
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

    @Test
    fun testRegisterUser_Success() {
        val request = RegisterRequest(
            email = "test@example.com",
            password = "Password123!",
            phoneNumber = "+256759123321",
            role = UserRole.BASIC_USER
        )

        val user = User().apply {
            email = "test@example.com"
            password = "Password123!"
            phoneNumber = "+256759123321"
        }

        Mockito.`when`(registrationService.createUser(anyObject())).thenReturn(user)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.email").value("test@example.com"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.phoneNumber").value("+256759123321"))
    }
}
