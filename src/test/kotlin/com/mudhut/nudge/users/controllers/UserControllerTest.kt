package com.mudhut.nudge.users.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.entities.UserRole
import com.mudhut.nudge.users.models.RegisterRequest
import com.mudhut.nudge.users.services.ForgotPasswordService
import com.mudhut.nudge.users.services.LoginService
import com.mudhut.nudge.users.services.RegistrationService
import com.mudhut.nudge.users.services.VerificationService
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

@SpringBootTest
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

    @Autowired
    private lateinit var objectMapper: ObjectMapper

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

        Mockito.`when`(registrationService.createUser(Mockito.any(RegisterRequest::class.java))).thenReturn(user)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.email").value("test@example.com"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.password").value("Test"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.phoneNumber").value("User"))
    }
}
