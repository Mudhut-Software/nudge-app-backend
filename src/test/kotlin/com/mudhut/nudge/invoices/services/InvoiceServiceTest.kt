package com.mudhut.nudge.invoices.services

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.businesses.services.BusinessService
import com.mudhut.nudge.invoices.entities.Invoice
import com.mudhut.nudge.invoices.entities.InvoiceLine
import com.mudhut.nudge.invoices.entities.InvoiceStatus
import com.mudhut.nudge.invoices.events.InvoiceIssuedEvent
import com.mudhut.nudge.invoices.models.CreateInvoiceRequest
import com.mudhut.nudge.invoices.models.LineInput
import com.mudhut.nudge.invoices.models.UpdateInvoiceRequest
import com.mudhut.nudge.invoices.repositories.InvoiceRepository
import com.mudhut.nudge.invoices.spi.RequestLineData
import com.mudhut.nudge.invoices.spi.RequestLineItem
import com.mudhut.nudge.invoices.spi.RequestLineQuery
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.utils.exceptions.BusinessAccessDeniedException
import jakarta.persistence.EntityNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.context.ApplicationEventPublisher
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
    @Mock private lateinit var eventPublisher: ApplicationEventPublisher

    @InjectMocks private lateinit var service: InvoiceService

    private fun draftWithOneLine(id: Long, businessId: Long): Invoice {
        val invoice = Invoice(
            id = id,
            business = Business(id = businessId, name = "Acme"),
            customer = User(id = 9L, username = "Cust"),
            createdBy = User(id = 1L, username = "M"),
            status = InvoiceStatus.DRAFT,
            currency = "USD",
        )
        invoice.lines.add(
            InvoiceLine(id = 1L, invoice = invoice, description = "Deep clean", unitAmount = BigDecimal("100.00"), quantity = 1, position = 0),
        )
        return invoice
    }

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

    // --- update ---

    @Test
    fun `update replaces lines of a draft invoice`() {
        val inv = draftWithOneLine(id = 7L, businessId = 1L)
        `when`(invoiceRepository.findByIdAndBusinessId(7L, 1L)).thenReturn(Optional.of(inv))
        `when`(invoiceRepository.save(any(Invoice::class.java))).thenAnswer {
            (it.arguments[0] as Invoice).apply { lines.forEachIndexed { i, l -> l.id = (i + 1).toLong() } }
        }
        `when`(businessRepository.findById(1L)).thenReturn(Optional.of(Business(id = 1L, name = "Acme")))

        val req = UpdateInvoiceRequest(
            currency = "EUR",
            notes = "Updated",
            lines = listOf(LineInput(description = "New line", unitAmount = BigDecimal("50.00"), quantity = 2)),
        )
        val result = service.update("mgr@test.com", 1L, 7L, req)

        assertEquals("EUR", result.currency)
        assertEquals("Updated", result.notes)
        assertEquals(1, result.lines.size)
        assertEquals("New line", result.lines[0].description)
        assertEquals(BigDecimal("100.00"), result.total)
    }

    @Test
    fun `update rejects a non-draft invoice`() {
        val inv = draftWithOneLine(id = 7L, businessId = 1L).apply { status = InvoiceStatus.SENT }
        `when`(invoiceRepository.findByIdAndBusinessId(7L, 1L)).thenReturn(Optional.of(inv))

        val req = UpdateInvoiceRequest(currency = "USD", lines = emptyList())
        assertThrows<IllegalArgumentException> { service.update("mgr@test.com", 1L, 7L, req) }
    }

    @Test
    fun `update 404s when invoice not found`() {
        `when`(invoiceRepository.findByIdAndBusinessId(7L, 1L)).thenReturn(Optional.empty())

        val req = UpdateInvoiceRequest(currency = "USD", lines = emptyList())
        assertThrows<EntityNotFoundException> { service.update("mgr@test.com", 1L, 7L, req) }
    }

    // --- issue ---

    @Test
    fun `issue assigns the first number, stamps dates, and publishes the event`() {
        val inv = draftWithOneLine(id = 7L, businessId = 1L)
        `when`(invoiceRepository.findByIdAndBusinessId(7L, 1L)).thenReturn(Optional.of(inv))
        `when`(invoiceRepository.findMaxSequenceForBusiness(1L)).thenReturn(null)
        `when`(invoiceRepository.save(any(Invoice::class.java))).thenAnswer { it.arguments[0] as Invoice }
        `when`(businessRepository.findById(1L)).thenReturn(Optional.of(Business(id = 1L, name = "Acme")))

        val result = service.issue("mgr@test.com", 1L, 7L)

        assertEquals(InvoiceStatus.SENT, result.status)
        assertEquals("INV-0001", result.number)
        assertEquals(LocalDate.now(), result.issueDate)
        Mockito.verify(eventPublisher).publishEvent(any(InvoiceIssuedEvent::class.java))
    }

    @Test
    fun `issue increments the sequence when prior invoices exist`() {
        val inv = draftWithOneLine(id = 7L, businessId = 1L)
        `when`(invoiceRepository.findByIdAndBusinessId(7L, 1L)).thenReturn(Optional.of(inv))
        `when`(invoiceRepository.findMaxSequenceForBusiness(1L)).thenReturn(3)
        `when`(invoiceRepository.save(any(Invoice::class.java))).thenAnswer { it.arguments[0] as Invoice }
        `when`(businessRepository.findById(1L)).thenReturn(Optional.of(Business(id = 1L, name = "Acme")))

        val result = service.issue("mgr@test.com", 1L, 7L)

        assertEquals("INV-0004", result.number)
    }

    @Test
    fun `issue rejects a non-draft invoice`() {
        val inv = draftWithOneLine(id = 7L, businessId = 1L).apply { status = InvoiceStatus.SENT }
        `when`(invoiceRepository.findByIdAndBusinessId(7L, 1L)).thenReturn(Optional.of(inv))

        assertThrows<IllegalArgumentException> { service.issue("mgr@test.com", 1L, 7L) }
        Mockito.verify(eventPublisher, Mockito.never()).publishEvent(any())
    }

    @Test
    fun `issue rejects an invoice with no lines`() {
        val inv = draftWithOneLine(id = 7L, businessId = 1L).apply { lines.clear() }
        `when`(invoiceRepository.findByIdAndBusinessId(7L, 1L)).thenReturn(Optional.of(inv))

        assertThrows<IllegalArgumentException> { service.issue("mgr@test.com", 1L, 7L) }
        Mockito.verify(eventPublisher, Mockito.never()).publishEvent(any())
    }

    // --- markPaid ---

    @Test
    fun `markPaid moves a sent invoice to paid`() {
        val inv = draftWithOneLine(id = 7L, businessId = 1L).apply { status = InvoiceStatus.SENT }
        `when`(invoiceRepository.findByIdAndBusinessId(7L, 1L)).thenReturn(Optional.of(inv))
        `when`(invoiceRepository.save(any(Invoice::class.java))).thenAnswer { it.arguments[0] as Invoice }
        `when`(businessRepository.findById(1L)).thenReturn(Optional.of(Business(id = 1L, name = "Acme")))

        val result = service.markPaid("mgr@test.com", 1L, 7L)

        assertEquals(InvoiceStatus.PAID, result.status)
        assertTrue(result.paidAt != null)
    }

    @Test
    fun `markPaid rejects a non-sent invoice`() {
        val inv = draftWithOneLine(id = 7L, businessId = 1L)
        `when`(invoiceRepository.findByIdAndBusinessId(7L, 1L)).thenReturn(Optional.of(inv))

        assertThrows<IllegalArgumentException> { service.markPaid("mgr@test.com", 1L, 7L) }
    }

    // --- void ---

    @Test
    fun `void succeeds from draft`() {
        val inv = draftWithOneLine(id = 7L, businessId = 1L)
        `when`(invoiceRepository.findByIdAndBusinessId(7L, 1L)).thenReturn(Optional.of(inv))
        `when`(invoiceRepository.save(any(Invoice::class.java))).thenAnswer { it.arguments[0] as Invoice }
        `when`(businessRepository.findById(1L)).thenReturn(Optional.of(Business(id = 1L, name = "Acme")))

        val result = service.void("mgr@test.com", 1L, 7L)

        assertEquals(InvoiceStatus.VOID, result.status)
    }

    @Test
    fun `void succeeds from sent`() {
        val inv = draftWithOneLine(id = 7L, businessId = 1L).apply { status = InvoiceStatus.SENT }
        `when`(invoiceRepository.findByIdAndBusinessId(7L, 1L)).thenReturn(Optional.of(inv))
        `when`(invoiceRepository.save(any(Invoice::class.java))).thenAnswer { it.arguments[0] as Invoice }
        `when`(businessRepository.findById(1L)).thenReturn(Optional.of(Business(id = 1L, name = "Acme")))

        val result = service.void("mgr@test.com", 1L, 7L)

        assertEquals(InvoiceStatus.VOID, result.status)
    }

    @Test
    fun `void rejects a paid invoice`() {
        val inv = draftWithOneLine(id = 7L, businessId = 1L).apply { status = InvoiceStatus.PAID }
        `when`(invoiceRepository.findByIdAndBusinessId(7L, 1L)).thenReturn(Optional.of(inv))

        assertThrows<IllegalArgumentException> { service.void("mgr@test.com", 1L, 7L) }
    }

    @Test
    fun `void rejects an already-void invoice`() {
        val inv = draftWithOneLine(id = 7L, businessId = 1L).apply { status = InvoiceStatus.VOID }
        `when`(invoiceRepository.findByIdAndBusinessId(7L, 1L)).thenReturn(Optional.of(inv))

        assertThrows<IllegalArgumentException> { service.void("mgr@test.com", 1L, 7L) }
    }

    // --- delete ---

    @Test
    fun `delete removes a draft invoice`() {
        val inv = draftWithOneLine(id = 7L, businessId = 1L)
        `when`(invoiceRepository.findByIdAndBusinessId(7L, 1L)).thenReturn(Optional.of(inv))

        service.delete("mgr@test.com", 1L, 7L)

        Mockito.verify(invoiceRepository).delete(inv)
    }

    @Test
    fun `delete rejects a non-draft invoice`() {
        val inv = draftWithOneLine(id = 7L, businessId = 1L).apply { status = InvoiceStatus.SENT }
        `when`(invoiceRepository.findByIdAndBusinessId(7L, 1L)).thenReturn(Optional.of(inv))

        assertThrows<IllegalArgumentException> { service.delete("mgr@test.com", 1L, 7L) }
        Mockito.verify(invoiceRepository, Mockito.never()).delete(any(Invoice::class.java))
    }

    // --- getForCustomer ---

    @Test
    fun `getForCustomer returns an issued invoice for the matching customer`() {
        val inv = draftWithOneLine(id = 7L, businessId = 1L).apply { status = InvoiceStatus.SENT }
        `when`(userRepository.findByEmail("cust@test.com")).thenReturn(Optional.of(User(id = 9L, username = "Cust")))
        `when`(invoiceRepository.findById(7L)).thenReturn(Optional.of(inv))
        `when`(businessRepository.findById(1L)).thenReturn(Optional.of(Business(id = 1L, name = "Acme")))

        val result = service.getForCustomer("cust@test.com", 7L)

        assertEquals(7L, result.id)
    }

    @Test
    fun `getForCustomer 403s for a different customer`() {
        val inv = draftWithOneLine(id = 7L, businessId = 1L).apply { status = InvoiceStatus.SENT }
        `when`(userRepository.findByEmail("other@test.com")).thenReturn(Optional.of(User(id = 99L, username = "Other")))
        `when`(invoiceRepository.findById(7L)).thenReturn(Optional.of(inv))

        assertThrows<BusinessAccessDeniedException> { service.getForCustomer("other@test.com", 7L) }
    }

    @Test
    fun `getForCustomer 403s while the invoice is still draft`() {
        val inv = draftWithOneLine(id = 7L, businessId = 1L)
        `when`(userRepository.findByEmail("cust@test.com")).thenReturn(Optional.of(User(id = 9L, username = "Cust")))
        `when`(invoiceRepository.findById(7L)).thenReturn(Optional.of(inv))

        assertThrows<BusinessAccessDeniedException> { service.getForCustomer("cust@test.com", 7L) }
    }
}
