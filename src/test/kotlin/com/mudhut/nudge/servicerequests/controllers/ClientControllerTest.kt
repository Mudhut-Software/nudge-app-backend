package com.mudhut.nudge.servicerequests.controllers

import com.mudhut.nudge.config.JsonAccessDeniedHandler
import com.mudhut.nudge.config.JsonAuthenticationEntryPoint
import com.mudhut.nudge.config.PassThroughJwtFilterConfig
import com.mudhut.nudge.config.SecurityConfig
import com.mudhut.nudge.servicerequests.models.ClientDetailResponse
import com.mudhut.nudge.servicerequests.models.ClientStatusCounts
import com.mudhut.nudge.servicerequests.models.ClientSummaryResponse
import com.mudhut.nudge.servicerequests.services.ClientService
import com.mudhut.nudge.users.services.NudgeUserDetailsService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageImpl
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ClientController::class)
@Import(
    SecurityConfig::class,
    PassThroughJwtFilterConfig::class,
    JsonAuthenticationEntryPoint::class,
    JsonAccessDeniedHandler::class,
)
@AutoConfigureMockMvc
class ClientControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var service: ClientService

    @MockitoBean
    private lateinit var userDetailsService: NudgeUserDetailsService

    @Test
    @WithMockUser(username = "owner@e.com")
    fun `GET clients returns page`() {
        whenever(service.listClients(eq("owner@e.com"), eq(1L), eq(null), any(), any()))
            .thenReturn(PageImpl(listOf(ClientSummaryResponse(9L, "Alice", "a@e.com", null, "Kampala", 3, null, null))))
        mockMvc.perform(get("/api/v1/businesses/1/clients"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].customerId").value(9))
            .andExpect(jsonPath("$.content[0].name").value("Alice"))
    }

    @Test
    @WithMockUser(username = "owner@e.com")
    fun `GET client detail returns detail`() {
        whenever(service.getClient(eq("owner@e.com"), eq(1L), eq(9L)))
            .thenReturn(
                ClientDetailResponse(
                    9L, "Alice", "a@e.com", null, "Kampala", 3, null, null,
                    ClientStatusCounts(completed = 2), emptyList(),
                )
            )
        mockMvc.perform(get("/api/v1/businesses/1/clients/9"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.statusCounts.completed").value(2))
    }
}
