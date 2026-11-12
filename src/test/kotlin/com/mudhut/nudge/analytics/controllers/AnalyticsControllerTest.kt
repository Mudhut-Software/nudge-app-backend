package com.mudhut.nudge.analytics.controllers

import com.mudhut.nudge.analytics.models.AnalyticsResponse
import com.mudhut.nudge.analytics.models.MoneyDto
import com.mudhut.nudge.analytics.models.MonthFigures
import com.mudhut.nudge.analytics.models.TrendPoint
import com.mudhut.nudge.analytics.services.AnalyticsService
import com.mudhut.nudge.config.JsonAccessDeniedHandler
import com.mudhut.nudge.config.JsonAuthenticationEntryPoint
import com.mudhut.nudge.config.PassThroughJwtFilterConfig
import com.mudhut.nudge.config.SecurityConfig
import com.mudhut.nudge.users.services.NudgeUserDetailsService
import com.mudhut.nudge.utils.exceptions.BusinessAccessDeniedException
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
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
import java.math.BigDecimal

@WebMvcTest(AnalyticsController::class)
@Import(SecurityConfig::class, PassThroughJwtFilterConfig::class, JsonAuthenticationEntryPoint::class, JsonAccessDeniedHandler::class)
@AutoConfigureMockMvc
class AnalyticsControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @MockitoBean private lateinit var service: AnalyticsService
    @MockitoBean private lateinit var userDetailsService: NudgeUserDetailsService

    private val figures = MonthFigures(
        completedJobs = 14, jobsWithUnpricedItems = 2,
        completedWork = listOf(MoneyDto("UGX", "1250000.00")), paid = emptyList(),
        newClients = 4, returningClients = 7, rating = BigDecimal("4.62"), newReviews = 3,
        received = 22, accepted = 15, pending = 1, declined = 3, cancelled = 2, noShow = 1,
        medianResponseMinutes = 47,
    )
    private val response = AnalyticsResponse(
        month = "2026-10", current = figures, previous = figures,
        trend = listOf(TrendPoint("2026-10", 14, emptyList(), emptyList(), null, 0)),
        services = emptyList(),
    )

    @Test
    @WithMockUser(username = "m@e.com")
    fun `returns the analytics document`() {
        whenever(service.forMonth("m@e.com", 1L, "2026-10")).thenReturn(response)

        mockMvc.perform(get("/api/v1/businesses/1/analytics").param("month", "2026-10"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.month").value("2026-10"))
            .andExpect(jsonPath("$.current.completedWork[0].amount").value("1250000.00"))
            .andExpect(jsonPath("$.current.rating").value(4.62))
            .andExpect(jsonPath("$.current.medianResponseMinutes").value(47))
            .andExpect(jsonPath("$.trend[0].rating").doesNotExist())
    }

    @Test
    @WithMockUser(username = "m@e.com")
    fun `month is optional`() {
        whenever(service.forMonth(eq("m@e.com"), eq(1L), anyOrNull())).thenReturn(response)
        mockMvc.perform(get("/api/v1/businesses/1/analytics")).andExpect(status().isOk)
    }

    @Test
    @WithMockUser(username = "m@e.com")
    fun `bad month is a 400 validation error`() {
        whenever(service.forMonth(any(), any(), eq("banana"))).thenThrow(IllegalArgumentException("month must be in YYYY-MM format"))
        mockMvc.perform(get("/api/v1/businesses/1/analytics").param("month", "banana"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
    }

    @Test
    @WithMockUser(username = "staff@e.com")
    fun `insufficient role is 403`() {
        whenever(service.forMonth(eq("staff@e.com"), eq(1L), anyOrNull())).thenThrow(BusinessAccessDeniedException("Insufficient role. Required: MANAGER or higher"))
        mockMvc.perform(get("/api/v1/businesses/1/analytics")).andExpect(status().isForbidden)
    }

    @Test
    fun `anonymous is 401`() {
        mockMvc.perform(get("/api/v1/businesses/1/analytics")).andExpect(status().isUnauthorized)
    }
}
