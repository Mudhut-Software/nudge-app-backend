package com.mudhut.nudge.invoices.controllers

import tools.jackson.databind.ObjectMapper
import com.mudhut.nudge.config.JsonAccessDeniedHandler
import com.mudhut.nudge.config.JsonAuthenticationEntryPoint
import com.mudhut.nudge.config.PassThroughJwtFilterConfig
import com.mudhut.nudge.config.SecurityConfig
import com.mudhut.nudge.invoices.entities.InvoiceStatus
import com.mudhut.nudge.invoices.models.BusinessDto
import com.mudhut.nudge.invoices.models.CreateInvoiceRequest
import com.mudhut.nudge.invoices.models.CustomerDto
import com.mudhut.nudge.invoices.models.InvoiceResponse
import com.mudhut.nudge.invoices.models.LineInput
import com.mudhut.nudge.invoices.models.UpdateInvoiceRequest
import com.mudhut.nudge.invoices.services.InvoiceService
import com.mudhut.nudge.users.services.NudgeUserDetailsService
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.math.BigDecimal

@WebMvcTest(controllers = [InvoiceController::class, CustomerInvoiceController::class])
@Import(SecurityConfig::class, PassThroughJwtFilterConfig::class, JsonAuthenticationEntryPoint::class, JsonAccessDeniedHandler::class)
@AutoConfigureMockMvc
class InvoiceControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @MockitoBean private lateinit var invoiceService: InvoiceService
    @MockitoBean private lateinit var userDetailsService: NudgeUserDetailsService

    private fun <T> anyObject(): T {
        Mockito.any<T>()
        @Suppress("UNCHECKED_CAST")
        return null as T
    }

    private fun <T> eqObject(value: T): T {
        Mockito.eq(value)
        return value
    }

    private fun response() = InvoiceResponse(
        id = 1L,
        number = "INV-0001",
        status = InvoiceStatus.DRAFT,
        currency = "USD",
        issueDate = null,
        dueDate = null,
        notes = null,
        customer = CustomerDto(userId = 2L, name = "Jane", avatarUrl = null),
        business = BusinessDto(id = 1L, name = "Acme"),
        lines = emptyList(),
        total = BigDecimal("0.00"),
        sourceRequestId = null,
        overdue = false,
        createdAt = null,
        sentAt = null,
        paidAt = null,
    )

    // --- InvoiceController (business-scoped) ---

    @Test
    @WithMockUser(username = "mgr@test.com")
    fun `GET lists invoices`() {
        `when`(invoiceService.list(anyString(), eq(1L), isNull()))
            .thenReturn(listOf(response()))

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/businesses/1/invoices"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].number").value("INV-0001"))
    }

    @Test
    @WithMockUser(username = "mgr@test.com")
    fun `GET list filters by status`() {
        `when`(invoiceService.list(anyString(), eq(1L), eqObject(InvoiceStatus.SENT)))
            .thenReturn(listOf(response()))

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/businesses/1/invoices").param("status", "SENT"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].number").value("INV-0001"))
    }

    @Test
    fun `GET list is unauthorized without auth`() {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/businesses/1/invoices"))
            .andExpect(MockMvcResultMatchers.status().isUnauthorized)
    }

    @Test
    @WithMockUser(username = "mgr@test.com")
    fun `GET fetches a single invoice`() {
        `when`(invoiceService.get(anyString(), eq(1L), eq(1L))).thenReturn(response())

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/businesses/1/invoices/1"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(1))
    }

    @Test
    @WithMockUser(username = "mgr@test.com")
    fun `POST creates a blank invoice`() {
        `when`(invoiceService.createBlank(anyString(), eq(1L), anyObject())).thenReturn(response())

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/businesses/1/invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        CreateInvoiceRequest(
                            customerId = 2L,
                            currency = "USD",
                            lines = listOf(LineInput(description = "Labor", unitAmount = BigDecimal("50.00"))),
                        ),
                    ),
                ),
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.number").value("INV-0001"))
    }

    @Test
    @WithMockUser(username = "mgr@test.com")
    fun `POST create rejects an invalid body`() {
        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/businesses/1/invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("currency" to "USD"))),
        )
            .andExpect(MockMvcResultMatchers.status().isBadRequest)

        Mockito.verify(invoiceService, Mockito.never()).createBlank(anyString(), Mockito.anyLong(), anyObject())
    }

    @Test
    @WithMockUser(username = "mgr@test.com")
    fun `POST creates an invoice from a request`() {
        `when`(invoiceService.createFromRequest(anyString(), eq(1L), eq(9L))).thenReturn(response())

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/businesses/1/invoices/from-request/9"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.number").value("INV-0001"))
    }

    @Test
    @WithMockUser(username = "mgr@test.com")
    fun `PUT replaces an invoice`() {
        `when`(invoiceService.update(anyString(), eq(1L), eq(1L), anyObject())).thenReturn(response())

        mockMvc.perform(
            MockMvcRequestBuilders.put("/api/v1/businesses/1/invoices/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        UpdateInvoiceRequest(
                            currency = "USD",
                            lines = listOf(LineInput(description = "Labor", unitAmount = BigDecimal("50.00"))),
                        ),
                    ),
                ),
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.number").value("INV-0001"))
    }

    @Test
    @WithMockUser(username = "mgr@test.com")
    fun `PATCH issue routes to the service`() {
        `when`(invoiceService.issue(anyString(), eq(1L), eq(1L))).thenReturn(response())

        mockMvc.perform(MockMvcRequestBuilders.patch("/api/v1/businesses/1/invoices/1/issue"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.number").value("INV-0001"))
    }

    @Test
    @WithMockUser(username = "mgr@test.com")
    fun `PATCH pay routes to the service`() {
        `when`(invoiceService.markPaid(anyString(), eq(1L), eq(1L))).thenReturn(response())

        mockMvc.perform(MockMvcRequestBuilders.patch("/api/v1/businesses/1/invoices/1/pay"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.number").value("INV-0001"))
    }

    @Test
    @WithMockUser(username = "mgr@test.com")
    fun `PATCH void routes to the service`() {
        `when`(invoiceService.void(anyString(), eq(1L), eq(1L))).thenReturn(response())

        mockMvc.perform(MockMvcRequestBuilders.patch("/api/v1/businesses/1/invoices/1/void"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.number").value("INV-0001"))
    }

    @Test
    @WithMockUser(username = "mgr@test.com")
    fun `DELETE removes an invoice`() {
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/businesses/1/invoices/1"))
            .andExpect(MockMvcResultMatchers.status().isNoContent)

        Mockito.verify(invoiceService).delete("mgr@test.com", 1L, 1L)
    }

    // --- CustomerInvoiceController ---

    @Test
    @WithMockUser(username = "cust@test.com")
    fun `GET customer view routes to getForCustomer`() {
        `when`(invoiceService.getForCustomer(anyString(), eq(1L))).thenReturn(response())

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/invoices/1"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(1))

        Mockito.verify(invoiceService).getForCustomer("cust@test.com", 1L)
    }

    @Test
    fun `GET customer view is unauthorized without auth`() {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/invoices/1"))
            .andExpect(MockMvcResultMatchers.status().isUnauthorized)
    }
}
