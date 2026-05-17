package com.mudhut.nudge.servicerequests.controllers

import com.mudhut.nudge.config.JsonAccessDeniedHandler
import com.mudhut.nudge.config.JsonAuthenticationEntryPoint
import com.mudhut.nudge.config.PassThroughJwtFilterConfig
import com.mudhut.nudge.config.SecurityConfig
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.models.AttachmentResponse
import com.mudhut.nudge.servicerequests.models.ServiceRequestItemResponse
import com.mudhut.nudge.servicerequests.models.ServiceRequestResponse
import com.mudhut.nudge.servicerequests.services.ProviderRequestService
import com.mudhut.nudge.users.services.helpers.NudgeUserDetailsService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

@Suppress("unused")
@WebMvcTest(ProviderServiceRequestController::class)
@Import(
    SecurityConfig::class,
    PassThroughJwtFilterConfig::class,
    JsonAuthenticationEntryPoint::class,
    JsonAccessDeniedHandler::class,
)
@AutoConfigureMockMvc
class ProviderServiceRequestControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var service: ProviderRequestService

    @MockitoBean
    private lateinit var userDetailsService: NudgeUserDetailsService

    private fun sample(status: ServiceRequestStatus = ServiceRequestStatus.PENDING) = ServiceRequestResponse(
        id = 1L,
        customerId = 1L, customerName = "Alice", customerEmail = "alice@example.com", customerPhone = null,
        businessId = 10L, businessName = "SparkleClean",
        status = status,
        items = listOf(
            ServiceRequestItemResponse(
                kind = "service", serviceId = 100L, packageId = null,
                title = "Deep Clean", priceAmount = null, priceCurrency = null,
                coverImageUrl = null, position = 0,
            )
        ),
        requestedDate = null,
        serviceLocation = null, serviceLatitude = null, serviceLongitude = null,
        note = null, attachments = emptyList<AttachmentResponse>(),
        submittedAt = LocalDateTime.now(),
        respondedAt = null, completedAt = null, cancelledAt = null, viewedAt = null,
        createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now(),
    )

    @Test
    fun `GET list returns 401 anonymous`() {
        mockMvc.perform(get("/api/v1/businesses/10/requests"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    @WithMockUser(username = "owner@example.com")
    fun `GET list returns paginated requests`() {
        whenever(service.list(eq("owner@example.com"), eq(10L), eq(null), any<Pageable>()))
            .thenReturn(PageImpl(listOf(sample())))
        mockMvc.perform(get("/api/v1/businesses/10/requests"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].id").value(1))
    }

    @Test
    @WithMockUser(username = "owner@example.com")
    fun `GET unread-count returns the count`() {
        whenever(service.unreadCount("owner@example.com", 10L)).thenReturn(7L)
        mockMvc.perform(get("/api/v1/businesses/10/requests/unread-count"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.count").value(7))
    }

    @Test
    @WithMockUser(username = "owner@example.com")
    fun `GET single triggers viewedAt side-effect via service`() {
        whenever(service.get("owner@example.com", 10L, 1L)).thenReturn(sample().copy(viewedAt = LocalDateTime.now()))
        mockMvc.perform(get("/api/v1/businesses/10/requests/1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.viewedAt").exists())
    }

    @Test
    @WithMockUser(username = "owner@example.com")
    fun `POST accept returns CONFIRMED`() {
        whenever(service.accept("owner@example.com", 10L, 1L))
            .thenReturn(sample(status = ServiceRequestStatus.CONFIRMED))
        mockMvc.perform(post("/api/v1/businesses/10/requests/1/accept"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CONFIRMED"))
    }

    @Test
    @WithMockUser(username = "owner@example.com")
    fun `POST decline returns DECLINED`() {
        whenever(service.decline("owner@example.com", 10L, 1L, null))
            .thenReturn(sample(status = ServiceRequestStatus.DECLINED))
        mockMvc.perform(post("/api/v1/businesses/10/requests/1/decline"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("DECLINED"))
    }

    @Test
    @WithMockUser(username = "owner@example.com")
    fun `POST complete returns COMPLETED`() {
        whenever(service.complete("owner@example.com", 10L, 1L))
            .thenReturn(sample(status = ServiceRequestStatus.COMPLETED))
        mockMvc.perform(post("/api/v1/businesses/10/requests/1/complete"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMPLETED"))
    }
}
