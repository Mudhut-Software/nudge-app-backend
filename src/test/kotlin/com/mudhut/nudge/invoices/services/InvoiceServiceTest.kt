package com.mudhut.nudge.invoices.services

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.businesses.services.BusinessService
import com.mudhut.nudge.invoices.entities.Invoice
import com.mudhut.nudge.invoices.entities.InvoiceStatus
import com.mudhut.nudge.invoices.models.CreateInvoiceRequest
import com.mudhut.nudge.invoices.models.LineInput
import com.mudhut.nudge.invoices.repositories.InvoiceRepository
import com.mudhut.nudge.invoices.spi.RequestLineData
import com.mudhut.nudge.invoices.spi.RequestLineItem
import com.mudhut.nudge.invoices.spi.RequestLineQuery
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.utils.exceptions.BusinessAccessDeniedException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class InvoiceServiceTest {

    @Mock private lateinit var invoiceRepository: InvoiceRepository
    @Mock private lateinit var businessService: BusinessService
    @Mock private lateinit var businessRepository: BusinessRepository
    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var requestLineQuery: RequestLineQuery

    @InjectMocks private lateinit var service: InvoiceService

    @Test
    fun `createFromRequest builds a draft from the SPI line data`() {
        `when`(userRepository.findByEmail("mgr@test.com")).thenReturn(Optional.of(User(id = 1L, username = "M")))
        `when`(userRepository.findById(9L)).thenReturn(Optional.of(User(id = 9L, username = "Cust")))
        `when`(businessRepository.findById(1L)).thenReturn(Optional.of(Business(id = 1L, name = "Acme")))
        `when`(requestLineQuery.forRequest(1L, 5L)).thenReturn(
            RequestLineData(customerId = 9L, currency = "UGX", lines = listOf(
                RequestLineItem("Deep clean", BigDecimal("150.00"), 1),
            )),
        )
        `when`(invoiceRepository.save(any(Invoice::class.java))).thenAnswer {
            (it.arguments[0] as Invoice).apply { id = 1L; lines.forEachIndexed { i, l -> l.id = (i + 1).toLong() } }
        }

        val result = service.createFromRequest("mgr@test.com", 1L, 5L)

        assertEquals(InvoiceStatus.DRAFT, result.status)
        assertEquals("UGX", result.currency)
        assertEquals(BigDecimal("150.00"), result.total)
        assertEquals(5L, result.sourceRequestId)
    }

    @Test
    fun `createFromRequest 400s when the request is not eligible`() {
        `when`(userRepository.findByEmail("mgr@test.com")).thenReturn(Optional.of(User(id = 1L, username = "M")))
        `when`(requestLineQuery.forRequest(1L, 5L)).thenReturn(null)

        assertThrows<IllegalArgumentException> { service.createFromRequest("mgr@test.com", 1L, 5L) }
    }

    @Test
    fun `createBlank requires MANAGER`() {
        `when`(businessService.requireRole(1L, "staff@test.com", BusinessRole.MANAGER))
            .thenThrow(BusinessAccessDeniedException("denied"))

        val req = CreateInvoiceRequest(customerId = 9L, currency = "USD")
        assertThrows<BusinessAccessDeniedException> { service.createBlank("staff@test.com", 1L, req) }
    }

    @Test
    fun `list maps total and overdue`() {
        `when`(businessRepository.findById(1L)).thenReturn(Optional.of(Business(id = 1L, name = "Acme")))
        val invoice = Invoice(
            id = 1L,
            business = Business(id = 1L, name = "Acme"),
            customer = User(id = 9L, username = "Cust"),
            createdBy = User(id = 1L, username = "M"),
            status = InvoiceStatus.SENT,
            currency = "USD",
            dueDate = LocalDate.now().minusDays(3),
        )
        `when`(invoiceRepository.findByBusinessIdOrderByCreatedAtDescIdDesc(1L)).thenReturn(listOf(invoice))

        val result = service.list("mgr@test.com", 1L, null)

        assertEquals(1, result.size)
        assertTrue(result[0].overdue)
        assertEquals(BigDecimal.ZERO.setScale(2), result[0].total.setScale(2))
    }

    @Test
    fun `toResponse normalizes invoice total to scale 2 when no lines`() {
        val invoice = Invoice(
            id = 1L,
            business = Business(id = 1L, name = "Acme"),
            customer = User(id = 9L, username = "Cust"),
            createdBy = User(id = 1L, username = "M"),
            currency = "USD",
        )
        val business = com.mudhut.nudge.invoices.models.BusinessDto(id = 1L, name = "Acme")

        val result = service.toResponse(invoice, business)

        assertEquals(BigDecimal("0.00"), result.total)
        assertEquals(2, result.total.scale())
    }
}
