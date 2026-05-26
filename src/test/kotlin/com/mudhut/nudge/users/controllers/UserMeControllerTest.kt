package com.mudhut.nudge.users.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.nudge.config.JsonAccessDeniedHandler
import com.mudhut.nudge.config.JsonAuthenticationEntryPoint
import com.mudhut.nudge.config.PassThroughJwtFilterConfig
import com.mudhut.nudge.config.SecurityConfig
import com.mudhut.nudge.users.models.UpdateUserRequest
import com.mudhut.nudge.users.models.UserResponse
import com.mudhut.nudge.users.services.UserMeService
import com.mudhut.nudge.users.services.helpers.NudgeUserDetailsService
import com.mudhut.nudge.utils.exceptions.UserAlreadyExistsException
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

@WebMvcTest(UserMeController::class)
@Import(SecurityConfig::class, PassThroughJwtFilterConfig::class, JsonAuthenticationEntryPoint::class, JsonAccessDeniedHandler::class)
@AutoConfigureMockMvc
class UserMeControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockitoBean
    private lateinit var userMeService: UserMeService

    @MockitoBean
    private lateinit var userDetailsService: NudgeUserDetailsService

    private fun sampleResponse(
        username: String = "alice",
        location: String? = "Kampala",
        website: String? = "https://alice.dev",
    ) = UserResponse(
        id = 1L,
        email = "alice@example.com",
        username = username,
        phoneNumber = "+256700000000",
        role = null,
        isEmailVerified = true,
        isPhoneVerified = true,
        isActive = true,
        location = location,
        website = website,
        avatarUrl = null,
        avatarPublicId = null,
        createdAt = LocalDateTime.now(),
    )

    @Test
    fun `PATCH users me returns 401 when anonymous`() {
        mockMvc.perform(
            patch("/api/v1/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"alice"}"""),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    @WithMockUser(username = "alice@example.com")
    fun `PATCH users me delegates to the service and returns the updated user`() {
        whenever(userMeService.updateMe(eq("alice@example.com"), any<UpdateUserRequest>()))
            .thenReturn(sampleResponse(location = "Mengo"))

        mockMvc.perform(
            patch("/api/v1/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"location":"Mengo"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.location").value("Mengo"))
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.email").value("alice@example.com"))
    }

    @Test
    @WithMockUser(username = "alice@example.com")
    fun `PATCH users me returns 409 when the service throws UserAlreadyExistsException`() {
        whenever(userMeService.updateMe(eq("alice@example.com"), any<UpdateUserRequest>()))
            .thenThrow(UserAlreadyExistsException("Username already taken: bob"))

        mockMvc.perform(
            patch("/api/v1/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"bob"}"""),
        ).andExpect(status().isConflict)
    }

    @Test
    @WithMockUser(username = "alice@example.com")
    fun `PATCH users me returns 400 for invalid website pattern`() {
        mockMvc.perform(
            patch("/api/v1/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"website":"not-a-url"}"""),
        ).andExpect(status().isBadRequest)
    }

    @Test
    @WithMockUser(username = "alice@example.com")
    fun `PATCH users me returns 400 for a too-short username`() {
        mockMvc.perform(
            patch("/api/v1/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"a"}"""),
        ).andExpect(status().isBadRequest)
    }

    @Test
    @WithMockUser(username = "alice@example.com")
    fun `PATCH users me ignores unknown body fields like email and phoneNumber`() {
        // Both fields are intentionally absent from the DTO — Jackson drops them.
        // Service is called with all-null fields; nothing changes.
        whenever(userMeService.updateMe(eq("alice@example.com"), any<UpdateUserRequest>()))
            .thenReturn(sampleResponse())

        mockMvc.perform(
            patch("/api/v1/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"new@example.com","phoneNumber":"+256000000000"}"""),
        ).andExpect(status().isOk)
    }
}
