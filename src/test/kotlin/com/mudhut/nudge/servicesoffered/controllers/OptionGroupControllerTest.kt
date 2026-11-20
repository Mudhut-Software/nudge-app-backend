package com.mudhut.nudge.servicesoffered.controllers

import com.mudhut.nudge.config.JsonAccessDeniedHandler
import com.mudhut.nudge.config.JsonAuthenticationEntryPoint
import com.mudhut.nudge.config.PassThroughJwtFilterConfig
import com.mudhut.nudge.config.SecurityConfig
import com.mudhut.nudge.servicesoffered.entities.GroupSelection
import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedStatus
import com.mudhut.nudge.servicesoffered.models.GroupChoiceResponse
import com.mudhut.nudge.servicesoffered.models.OptionGroupResponse
import com.mudhut.nudge.servicesoffered.models.UsedInResponse
import com.mudhut.nudge.servicesoffered.services.OptionGroupService
import com.mudhut.nudge.users.services.NudgeUserDetailsService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithAnonymousUser
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal

@WebMvcTest(OptionGroupController::class)
@Import(SecurityConfig::class, PassThroughJwtFilterConfig::class, JsonAuthenticationEntryPoint::class, JsonAccessDeniedHandler::class)
@AutoConfigureMockMvc
class OptionGroupControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var service: OptionGroupService

    @MockitoBean
    private lateinit var userDetailsService: NudgeUserDetailsService

    private val body = """
        [{"name":"Flavours","selection":"MULTIPLE","required":true,"minPicks":1,"maxPicks":3,
          "choices":[{"itemId":20,"priceOverride":"3000.00","quantityEnabled":true,"maxQuantity":2}]}]
    """.trimIndent()

    @Test
    @WithAnonymousUser
    fun `PUT groups returns 401 anon`() {
        mockMvc.perform(put("/api/v1/services/10/groups").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized)
    }

    @Test
    @WithMockUser(username = "owner@example.com")
    fun `PUT groups replaces and returns 200 with the groups`() {
        whenever(service.replace(eq(10L), eq("owner@example.com"), any())).thenReturn(
            listOf(
                OptionGroupResponse(
                    id = 5L, name = "Flavours", selection = GroupSelection.MULTIPLE, required = true,
                    minPicks = 1, maxPicks = 3,
                    choices = listOf(
                        GroupChoiceResponse(
                            id = 50L, itemId = 20L, title = "Mango", coverImageUrl = null,
                            itemPriceAmount = BigDecimal("2000.00"), priceOverride = BigDecimal("3000.00"),
                            effectivePrice = BigDecimal("3000.00"), quantityEnabled = true, maxQuantity = 2,
                            itemStatus = ServiceOfferedStatus.ACTIVE,
                        )
                    ),
                )
            )
        )

        mockMvc.perform(put("/api/v1/services/10/groups").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(5))
            .andExpect(jsonPath("$[0].choices[0].title").value("Mango"))
            .andExpect(jsonPath("$[0].choices[0].itemStatus").value("ACTIVE"))
    }

    @Test
    @WithMockUser(username = "owner@example.com")
    fun `PUT groups maps IllegalArgumentException to 400`() {
        whenever(service.replace(eq(10L), eq("owner@example.com"), any()))
            .thenThrow(IllegalArgumentException("Group 'Flavours': minPicks cannot exceed maxPicks"))

        mockMvc.perform(put("/api/v1/services/10/groups").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest)
    }

    @Test
    @WithAnonymousUser
    fun `GET used-in returns 401 anon`() {
        mockMvc.perform(get("/api/v1/services/10/used-in")).andExpect(status().isUnauthorized)
    }

    @Test
    @WithMockUser(username = "owner@example.com")
    fun `GET used-in returns 200 with body`() {
        whenever(service.usedIn(eq(10L), eq("owner@example.com")))
            .thenReturn(listOf(UsedInResponse(itemId = 11L, itemTitle = "Platter", groupName = "Flavours")))

        mockMvc.perform(get("/api/v1/services/10/used-in"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].itemTitle").value("Platter"))
            .andExpect(jsonPath("$[0].groupName").value("Flavours"))
    }
}
