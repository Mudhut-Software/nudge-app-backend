package com.mudhut.nudge.servicerequests.controllers

import com.mudhut.nudge.config.JsonAccessDeniedHandler
import com.mudhut.nudge.config.JsonAuthenticationEntryPoint
import com.mudhut.nudge.config.PassThroughJwtFilterConfig
import com.mudhut.nudge.config.SecurityConfig
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.entities.PricingState
import com.mudhut.nudge.servicerequests.models.AttachmentResponse
import com.mudhut.nudge.servicerequests.models.ServiceRequestItemResponse
import com.mudhut.nudge.servicerequests.models.ServiceRequestResponse
import com.mudhut.nudge.servicerequests.services.ProviderRequestService
import com.mudhut.nudge.users.services.NudgeUserDetailsService
import com.mudhut.nudge.utils.exceptions.PriceNotSettledException
import java.math.BigDecimal
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
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
                serviceId = 100L,
                title = "Deep Clean", priceAmount = null, priceCurrency = null,
                priceIsEstimate = false, estimateReason = null, quantity = 1, quantityUnit = null,
                coverImageUrl = null, position = 0, picks = emptyList(),
            )
        ),
        requestedDate = null,
        serviceLocation = null, serviceLatitude = null, serviceLongitude = null,
        note = null, accessDirections = null, declineReason = null, cancellationReason = null,
        proposal = null, attachments = emptyList<AttachmentResponse>(),
            pricing = PricingState.TO_BE_CONFIRMED, total = null,
        agreedPrice = null, priceProposal = null, priceSettled = false,
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
    fun `GET calendar returns jobs in the window`() {
        whenever(
            service.calendar(
                eq("owner@example.com"), eq(10L),
                eq(LocalDate.of(2026, 3, 1)), eq(LocalDate.of(2026, 4, 1)),
            ),
        ).thenReturn(listOf(sample(ServiceRequestStatus.CONFIRMED)))

        mockMvc.perform(
            get("/api/v1/businesses/10/requests/calendar")
                .param("from", "2026-03-01")
                .param("to", "2026-04-01"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(1))
    }

    @Test
    fun `GET calendar returns 401 anonymous`() {
        mockMvc.perform(
            get("/api/v1/businesses/10/requests/calendar")
                .param("from", "2026-03-01")
                .param("to", "2026-04-01"),
        ).andExpect(status().isUnauthorized)
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
    fun `POST propose returns REVISION_REQUESTED`() {
        whenever(
            service.propose(
                eq("owner@example.com"),
                eq(10L),
                eq(1L),
                eq(LocalDateTime.of(2027, 1, 15, 10, 0)),
                eq("Fully booked Monday"),
            )
        ).thenReturn(sample(status = ServiceRequestStatus.REVISION_REQUESTED))

        mockMvc.perform(
            post("/api/v1/businesses/10/requests/1/propose")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"proposedDate":"2027-01-15T10:00:00","note":"Fully booked Monday"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("REVISION_REQUESTED"))
    }

    @Test
    @WithMockUser(username = "owner@example.com")
    fun `POST propose rejects a missing proposedDate`() {
        mockMvc.perform(
            post("/api/v1/businesses/10/requests/1/propose")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"note":"no date"}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `POST propose returns 401 anonymous`() {
        mockMvc.perform(
            post("/api/v1/businesses/10/requests/1/propose")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"proposedDate":"2027-01-15T10:00:00"}""")
        ).andExpect(status().isUnauthorized)
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

    @Test
    @WithMockUser(username = "owner@example.com")
    fun `POST price passes amount, currency and note through`() {
        whenever(
            service.proposePrice(
                eq("owner@example.com"), eq(10L), eq(1L), eq(BigDecimal("4200000")), eq("UGX"), eq("canopy"),
            ),
        ).thenReturn(sample(status = ServiceRequestStatus.CONFIRMED))
        mockMvc.perform(
            post("/api/v1/businesses/10/requests/1/price")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":4200000,"currency":"UGX","note":"canopy"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.priceSettled").value(false))
    }

    @Test
    @WithMockUser(username = "owner@example.com")
    fun `POST price rejects a zero amount, a bad currency and a long note`() {
        listOf(
            """{"amount":0,"currency":"UGX"}""",
            """{"amount":100,"currency":"ugx"}""",
            """{"amount":100}""",
            """{"currency":"UGX"}""",
            """{"amount":100,"currency":"UGX","note":"${"x".repeat(501)}"}""",
        ).forEach { body ->
            mockMvc.perform(
                post("/api/v1/businesses/10/requests/1/price")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
            ).andExpect(status().isBadRequest)
        }
    }

    @Test
    @WithMockUser(username = "owner@example.com")
    fun `POST price maps a refused state to 400`() {
        whenever(service.proposePrice(any(), any(), any(), any(), any(), anyOrNull()))
            .thenThrow(IllegalArgumentException("This booking's price is fixed"))
        mockMvc.perform(
            post("/api/v1/businesses/10/requests/1/price")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":100,"currency":"UGX"}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `POST price returns 401 anonymous`() {
        mockMvc.perform(
            post("/api/v1/businesses/10/requests/1/price")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":100,"currency":"UGX"}""")
        ).andExpect(status().isUnauthorized)
    }

    @Test
    @WithMockUser(username = "owner@example.com")
    fun `POST complete maps an unsettled price to 409`() {
        whenever(service.complete("owner@example.com", 10L, 1L))
            .thenThrow(PriceNotSettledException("The price for this booking hasn't been agreed yet"))
        mockMvc.perform(post("/api/v1/businesses/10/requests/1/complete"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("The price for this booking hasn't been agreed yet"))
    }
}
