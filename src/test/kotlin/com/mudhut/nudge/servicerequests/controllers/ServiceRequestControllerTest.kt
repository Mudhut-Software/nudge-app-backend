package com.mudhut.nudge.servicerequests.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.nudge.config.JsonAccessDeniedHandler
import com.mudhut.nudge.config.JsonAuthenticationEntryPoint
import com.mudhut.nudge.config.PassThroughJwtFilterConfig
import com.mudhut.nudge.config.SecurityConfig
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.models.AttachmentResponse
import com.mudhut.nudge.servicerequests.models.ServiceRequestItemResponse
import com.mudhut.nudge.servicerequests.models.ServiceRequestResponse
import com.mudhut.nudge.servicerequests.services.ServiceRequestService
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
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

@Suppress("unused")
@WebMvcTest(ServiceRequestController::class)
@Import(
    SecurityConfig::class,
    PassThroughJwtFilterConfig::class,
    JsonAuthenticationEntryPoint::class,
    JsonAccessDeniedHandler::class,
)
@AutoConfigureMockMvc
class ServiceRequestControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockitoBean
    private lateinit var service: ServiceRequestService

    @MockitoBean
    private lateinit var userDetailsService: NudgeUserDetailsService

    private fun sampleResponse(id: Long = 1L, status: ServiceRequestStatus = ServiceRequestStatus.DRAFT) =
        ServiceRequestResponse(
            id = id,
            customerId = 1L,
            customerName = "Alice",
            customerEmail = "alice@example.com",
            customerPhone = "+256700000000",
            businessId = 10L,
            businessName = "SparkleClean",
            status = status,
            items = listOf(
                ServiceRequestItemResponse(
                    kind = "service",
                    serviceId = 100L, packageId = null,
                    title = "Deep Clean", priceAmount = null, priceCurrency = "UGX",
                    coverImageUrl = null, position = 0,
                )
            ),
            requestedDate = null,
            serviceLocation = null,
            serviceLatitude = null, serviceLongitude = null,
            note = null,
            attachments = emptyList<AttachmentResponse>(),
            submittedAt = null,
            respondedAt = null,
            completedAt = null,
            cancelledAt = null,
            viewedAt = null,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )

    @Test
    fun `POST requests returns 401 anonymous`() {
        mockMvc.perform(
            post("/api/v1/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"businessId":10,"items":[{"serviceId":100}]}""")
        ).andExpect(status().isUnauthorized)
    }

    @Test
    @WithMockUser(username = "alice@example.com")
    fun `POST requests creates a draft and returns 201`() {
        whenever(service.create(eq("alice@example.com"), any())).thenReturn(sampleResponse())
        mockMvc.perform(
            post("/api/v1/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"businessId":10,"items":[{"serviceId":100}]}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.status").value("DRAFT"))
    }

    @Test
    @WithMockUser(username = "alice@example.com")
    fun `POST requests with empty items returns 400`() {
        mockMvc.perform(
            post("/api/v1/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"businessId":10,"items":[]}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    @WithMockUser(username = "alice@example.com")
    fun `GET requests my paginates`() {
        whenever(service.list(eq("alice@example.com"), eq(10L), eq(null), any<Pageable>()))
            .thenReturn(PageImpl(listOf(sampleResponse())))
        mockMvc.perform(get("/api/v1/requests/my?businessId=10"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].id").value(1))
    }

    @Test
    @WithMockUser(username = "alice@example.com")
    fun `PATCH requests applies updates`() {
        whenever(service.patch(eq("alice@example.com"), eq(1L), any()))
            .thenReturn(sampleResponse().copy(note = "bring keys"))
        mockMvc.perform(
            patch("/api/v1/requests/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"note":"bring keys"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.note").value("bring keys"))
    }

    @Test
    @WithMockUser(username = "alice@example.com")
    fun `POST submit transitions to PENDING`() {
        whenever(service.submit(eq("alice@example.com"), eq(1L)))
            .thenReturn(sampleResponse(status = ServiceRequestStatus.PENDING))
        mockMvc.perform(post("/api/v1/requests/1/submit"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("PENDING"))
    }

    @Test
    @WithMockUser(username = "alice@example.com")
    fun `POST withdraw transitions to DRAFT`() {
        whenever(service.withdraw(eq("alice@example.com"), eq(1L)))
            .thenReturn(sampleResponse(status = ServiceRequestStatus.DRAFT))
        mockMvc.perform(post("/api/v1/requests/1/withdraw"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("DRAFT"))
    }

    @Test
    @WithMockUser(username = "alice@example.com")
    fun `POST cancel works with no body`() {
        whenever(service.cancel(eq("alice@example.com"), eq(1L), eq(null)))
            .thenReturn(sampleResponse(status = ServiceRequestStatus.CANCELLED))
        mockMvc.perform(
            post("/api/v1/requests/1/cancel")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CANCELLED"))
    }

    @Test
    @WithMockUser(username = "alice@example.com")
    fun `DELETE returns 204`() {
        mockMvc.perform(delete("/api/v1/requests/1"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `POST duplicate returns 401 anonymous`() {
        mockMvc.perform(post("/api/v1/requests/1/duplicate"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    @WithMockUser(username = "alice@example.com")
    fun `POST duplicate returns 201 with the new DRAFT and unavailable list`() {
        whenever(service.duplicate(eq("alice@example.com"), eq(1L)))
            .thenReturn(
                com.mudhut.nudge.servicerequests.models.DuplicateResponse(
                    request = sampleResponse(id = 99L),
                    unavailableItems = listOf("Sofa Cleaning"),
                )
            )
        mockMvc.perform(post("/api/v1/requests/1/duplicate"))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.request.id").value(99))
            .andExpect(jsonPath("$.request.status").value("DRAFT"))
            .andExpect(jsonPath("$.unavailableItems[0]").value("Sofa Cleaning"))
    }
}
