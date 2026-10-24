package com.mudhut.nudge.servicerequests.controllers

import com.mudhut.nudge.config.JsonAccessDeniedHandler
import com.mudhut.nudge.config.JsonAuthenticationEntryPoint
import com.mudhut.nudge.config.PassThroughJwtFilterConfig
import com.mudhut.nudge.config.SecurityConfig
import com.mudhut.nudge.servicerequests.services.JobQuotaService
import com.mudhut.nudge.servicerequests.services.QuotaSnapshot
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.users.services.NudgeUserDetailsService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.Optional

@WebMvcTest(QuotaController::class)
@Import(
    SecurityConfig::class,
    PassThroughJwtFilterConfig::class,
    JsonAuthenticationEntryPoint::class,
    JsonAccessDeniedHandler::class,
)
@AutoConfigureMockMvc
class QuotaControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var quotaService: JobQuotaService

    @MockitoBean
    private lateinit var userRepository: UserRepository

    @MockitoBean
    private lateinit var userDetailsService: NudgeUserDetailsService

    @Test
    @WithMockUser(username = "owner@example.com")
    fun `GET quota reports the free allowance`() {
        whenever(userRepository.findByEmail("owner@example.com"))
            .thenReturn(Optional.of(User(id = 1L, email = "owner@example.com", username = "Owner", password = "x")))
        whenever(quotaService.snapshotFor(any()))
            .thenReturn(
                QuotaSnapshot(
                    false, 10, 4, 6,
                    Instant.parse("2026-03-08T00:00:00Z"),
                    Instant.parse("2026-04-08T00:00:00Z"),
                ),
            )

        mockMvc.perform(get("/api/v1/me/quota"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.limit").value(10))
            .andExpect(jsonPath("$.remaining").value(6))
            .andExpect(jsonPath("$.unlimited").value(false))
            .andExpect(jsonPath("$.periodEndsAt").value("2026-04-08T00:00:00Z"))
    }

    @Test
    @WithMockUser(username = "owner@example.com")
    fun `GET quota reports null, not a number, for pro`() {
        whenever(userRepository.findByEmail("owner@example.com"))
            .thenReturn(Optional.of(User(id = 1L, email = "owner@example.com", username = "Owner", password = "x")))
        whenever(quotaService.snapshotFor(any()))
            .thenReturn(
                QuotaSnapshot(
                    true, null, 0, null,
                    Instant.parse("2026-03-08T00:00:00Z"),
                    Instant.parse("2026-04-08T00:00:00Z"),
                ),
            )

        mockMvc.perform(get("/api/v1/me/quota"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.unlimited").value(true))
            .andExpect(jsonPath("$.remaining").doesNotExist())
    }

    @Test
    fun `GET quota returns 401 anonymous`() {
        mockMvc.perform(get("/api/v1/me/quota")).andExpect(status().isUnauthorized)
    }
}
