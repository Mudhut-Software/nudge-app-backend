package com.mudhut.nudge.servicesoffered.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.nudge.config.JsonAccessDeniedHandler
import com.mudhut.nudge.config.JsonAuthenticationEntryPoint
import com.mudhut.nudge.config.PassThroughJwtFilterConfig
import com.mudhut.nudge.config.SecurityConfig
import com.mudhut.nudge.servicesoffered.models.CreateServiceAddonRequest
import com.mudhut.nudge.servicesoffered.models.ReorderAddonsRequest
import com.mudhut.nudge.servicesoffered.models.ServiceAddonResponse
import com.mudhut.nudge.servicesoffered.services.ServiceAddonService
import com.mudhut.nudge.users.services.helpers.NudgeUserDetailsService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithAnonymousUser
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal

@WebMvcTest(ServiceAddonController::class)
@Import(SecurityConfig::class, PassThroughJwtFilterConfig::class, JsonAuthenticationEntryPoint::class, JsonAccessDeniedHandler::class)
@AutoConfigureMockMvc
class ServiceAddonControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockitoBean
    private lateinit var service: ServiceAddonService

    @MockitoBean
    private lateinit var userDetailsService: NudgeUserDetailsService

    private fun response(id: Long = 1L) = ServiceAddonResponse(
        id = id, serviceId = 10L, title = "Extra tray", description = null,
        coverImageUrl = null, coverImagePublicId = null,
        priceDelta = BigDecimal("5000"), priceUnit = null,
        defaultSelected = false, quantifiable = false,
        defaultQuantity = 1, maxQuantity = null, position = 0,
    )

    @Test
    @WithAnonymousUser
    fun `GET list returns 401 anon`() {
        mockMvc.perform(get("/api/v1/services/10/addons"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    @WithMockUser(username = "owner@example.com")
    fun `GET list returns 200 with body`() {
        whenever(service.list(eq(10L), eq("owner@example.com"))).thenReturn(listOf(response()))

        mockMvc.perform(get("/api/v1/services/10/addons"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].title").value("Extra tray"))
    }

    @Test
    @WithMockUser(username = "owner@example.com")
    fun `POST create returns 201`() {
        whenever(service.create(eq(10L), eq("owner@example.com"), any())).thenReturn(response())

        val body = CreateServiceAddonRequest(title = "Extra tray", priceDelta = BigDecimal("5000"))
        mockMvc.perform(
            post("/api/v1/services/10/addons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.title").value("Extra tray"))
    }

    @Test
    @WithMockUser(username = "owner@example.com")
    fun `POST create with maxQuantity less than defaultQuantity returns 400`() {
        val body = mapOf(
            "title" to "Bad",
            "defaultQuantity" to 5,
            "maxQuantity" to 2,
        )
        mockMvc.perform(
            post("/api/v1/services/10/addons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        ).andExpect(status().isBadRequest)
    }

    @Test
    @WithMockUser(username = "owner@example.com")
    fun `POST create with negative priceDelta returns 400`() {
        val body = mapOf("title" to "Bad", "priceDelta" to -1)
        mockMvc.perform(
            post("/api/v1/services/10/addons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
        ).andExpect(status().isBadRequest)
    }

    @Test
    @WithMockUser(username = "owner@example.com")
    fun `PATCH update returns 200`() {
        whenever(service.update(eq(10L), eq(5L), eq("owner@example.com"), any())).thenReturn(response(5L))

        mockMvc.perform(
            patch("/api/v1/services/10/addons/5")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"Renamed"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(5))
    }

    @Test
    @WithMockUser(username = "owner@example.com")
    fun `DELETE returns 204`() {
        mockMvc.perform(delete("/api/v1/services/10/addons/5"))
            .andExpect(status().isNoContent)
    }

    @Test
    @WithMockUser(username = "owner@example.com")
    fun `PUT reorder returns 200`() {
        whenever(service.reorder(eq(10L), any(), eq("owner@example.com")))
            .thenReturn(listOf(response(2L), response(1L)))

        mockMvc.perform(
            put("/api/v1/services/10/addons/reorder")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ReorderAddonsRequest(orderedIds = listOf(2, 1))))
        ).andExpect(status().isOk)
    }
}
