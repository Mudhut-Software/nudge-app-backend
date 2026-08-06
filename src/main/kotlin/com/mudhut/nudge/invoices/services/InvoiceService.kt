package com.mudhut.nudge.invoices.services

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.businesses.services.BusinessService
import com.mudhut.nudge.invoices.entities.Invoice
import com.mudhut.nudge.invoices.entities.InvoiceLine
import com.mudhut.nudge.invoices.entities.InvoiceStatus
import com.mudhut.nudge.invoices.events.InvoiceIssuedEvent
import com.mudhut.nudge.invoices.models.BusinessDto
import com.mudhut.nudge.invoices.models.CreateInvoiceRequest
import com.mudhut.nudge.invoices.models.CustomerDto
import com.mudhut.nudge.invoices.models.InvoiceResponse
import com.mudhut.nudge.invoices.models.LineInput
import com.mudhut.nudge.invoices.models.LineResponse
import com.mudhut.nudge.invoices.models.UpdateInvoiceRequest
import com.mudhut.nudge.invoices.repositories.InvoiceRepository
import com.mudhut.nudge.invoices.spi.RequestLineItem
import com.mudhut.nudge.invoices.spi.RequestLineQuery
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.utils.exceptions.BusinessAccessDeniedException
import jakarta.persistence.EntityNotFoundException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class InvoiceService(
    private val invoiceRepository: InvoiceRepository,
    private val businessService: BusinessService,
    private val businessRepository: BusinessRepository,
    private val userRepository: UserRepository,
    private val requestLineQuery: RequestLineQuery,
    private val eventPublisher: ApplicationEventPublisher,
) {

    @Transactional(readOnly = true)
    fun list(email: String, businessId: Long, status: InvoiceStatus?): List<InvoiceResponse> {
        businessService.requireRole(businessId, email, BusinessRole.STAFF)
        val businessDto = businessDto(businessId)
        val invoices = status?.let {
            invoiceRepository.findByBusinessIdAndStatusOrderByCreatedAtDescIdDesc(businessId, it)
        } ?: invoiceRepository.findByBusinessIdOrderByCreatedAtDescIdDesc(businessId)
        return invoices.map { toResponse(it, businessDto) }
    }

    @Transactional(readOnly = true)
    fun get(email: String, businessId: Long, id: Long): InvoiceResponse {
        businessService.requireRole(businessId, email, BusinessRole.STAFF)
        val invoice = invoiceRepository.findByIdAndBusinessId(id, businessId)
            .orElseThrow { EntityNotFoundException("Invoice not found") }
        return toResponse(invoice, businessDto(businessId))
    }

    @Transactional
    fun createBlank(email: String, businessId: Long, req: CreateInvoiceRequest): InvoiceResponse {
        businessService.requireRole(businessId, email, BusinessRole.MANAGER)
        val creator = userRepository.findByEmail(email)
            .orElseThrow { EntityNotFoundException("User not found") }
        val business = requireBusiness(businessId)
        val customer = userRepository.findById(req.customerId)
            .orElseThrow { IllegalArgumentException("Customer ${req.customerId} not found") }

        val invoice = Invoice(
            business = business,
            customer = customer,
            currency = req.currency,
            dueDate = req.dueDate,
            notes = req.notes,
            createdBy = creator,
        )
        setLines(invoice, req.lines)

        val saved = invoiceRepository.save(invoice)
        return toResponse(saved, BusinessDto(business.id!!, business.name))
    }

    @Transactional
    fun createFromRequest(email: String, businessId: Long, requestId: Long): InvoiceResponse {
        businessService.requireRole(businessId, email, BusinessRole.MANAGER)
        val creator = userRepository.findByEmail(email)
            .orElseThrow { EntityNotFoundException("User not found") }
        val data = requestLineQuery.forRequest(businessId, requestId)
            ?: throw IllegalArgumentException("Request is not an invoiceable completed job")
        val business = requireBusiness(businessId)
        val customer = userRepository.findById(data.customerId)
            .orElseThrow { EntityNotFoundException("Customer not found") }

        val invoice = Invoice(
            business = business,
            customer = customer,
            sourceRequestId = requestId,
            currency = data.currency,
            createdBy = creator,
        )
        setLinesFromRequestData(invoice, data.lines)

        val saved = invoiceRepository.save(invoice)
        return toResponse(saved, BusinessDto(business.id!!, business.name))
    }

    @Transactional
    fun update(email: String, businessId: Long, id: Long, req: UpdateInvoiceRequest): InvoiceResponse {
        businessService.requireRole(businessId, email, BusinessRole.MANAGER)
        val invoice = requireInvoice(businessId, id)
        require(invoice.status == InvoiceStatus.DRAFT) { "Only draft invoices can be edited" }
        invoice.currency = req.currency
        invoice.dueDate = req.dueDate
        invoice.notes = req.notes
        setLines(invoice, req.lines)

        val saved = invoiceRepository.save(invoice)
        return toResponse(saved, businessDto(businessId))
    }

    @Transactional
    fun issue(email: String, businessId: Long, id: Long): InvoiceResponse {
        businessService.requireRole(businessId, email, BusinessRole.MANAGER)
        val invoice = requireInvoice(businessId, id)
        require(invoice.status == InvoiceStatus.DRAFT) { "Only draft invoices can be issued" }
        require(invoice.lines.isNotEmpty()) { "Add at least one line before issuing" }

        val seq = (invoiceRepository.findMaxSequenceForBusiness(businessId) ?: 0) + 1
        invoice.sequenceNo = seq
        invoice.number = "INV-" + seq.toString().padStart(4, '0')
        invoice.status = InvoiceStatus.SENT
        invoice.issueDate = LocalDate.now()
        invoice.sentAt = LocalDateTime.now()

        val saved = invoiceRepository.save(invoice)
        eventPublisher.publishEvent(
            InvoiceIssuedEvent(
                invoiceId = saved.id!!,
                businessId = businessId,
                customerId = saved.customer!!.id!!,
                number = saved.number!!,
                total = total(saved),
                currency = saved.currency,
            ),
        )
        return toResponse(saved, businessDto(businessId))
    }

    @Transactional
    fun markPaid(email: String, businessId: Long, id: Long): InvoiceResponse {
        businessService.requireRole(businessId, email, BusinessRole.MANAGER)
        val invoice = requireInvoice(businessId, id)
        require(invoice.status == InvoiceStatus.SENT) { "Only sent invoices can be marked paid" }
        invoice.status = InvoiceStatus.PAID
        invoice.paidAt = LocalDateTime.now()

        val saved = invoiceRepository.save(invoice)
        return toResponse(saved, businessDto(businessId))
    }

    @Transactional
    fun void(email: String, businessId: Long, id: Long): InvoiceResponse {
        businessService.requireRole(businessId, email, BusinessRole.MANAGER)
        val invoice = requireInvoice(businessId, id)
        require(invoice.status == InvoiceStatus.DRAFT || invoice.status == InvoiceStatus.SENT) {
            "This invoice can't be voided"
        }
        invoice.status = InvoiceStatus.VOID

        val saved = invoiceRepository.save(invoice)
        return toResponse(saved, businessDto(businessId))
    }

    @Transactional
    fun delete(email: String, businessId: Long, id: Long) {
        businessService.requireRole(businessId, email, BusinessRole.MANAGER)
        val invoice = requireInvoice(businessId, id)
        require(invoice.status == InvoiceStatus.DRAFT) { "Only draft invoices can be deleted" }
        invoiceRepository.delete(invoice)
    }

    @Transactional(readOnly = true)
    fun getForCustomer(email: String, invoiceId: Long): InvoiceResponse {
        val user = userRepository.findByEmail(email)
            .orElseThrow { EntityNotFoundException("User not found") }
        val invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow { EntityNotFoundException("Invoice not found") }
        if (invoice.customer?.id != user.id || invoice.status == InvoiceStatus.DRAFT) {
            throw BusinessAccessDeniedException("Not your invoice")
        }
        return toResponse(invoice, businessDto(invoice.business!!.id!!))
    }

    // --- shared helpers (also used by Task 4) ---

    private fun requireInvoice(businessId: Long, id: Long): Invoice =
        invoiceRepository.findByIdAndBusinessId(id, businessId)
            .orElseThrow { EntityNotFoundException("Invoice not found") }

    private fun total(invoice: Invoice): BigDecimal =
        invoice.lines.fold(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)) { acc, l ->
            acc.add(l.unitAmount!!.multiply(BigDecimal(l.quantity)).setScale(2, RoundingMode.HALF_UP))
        }

    internal fun setLines(invoice: Invoice, inputs: List<LineInput>) {
        invoice.lines.clear()
        inputs.forEachIndexed { i, li ->
            invoice.lines.add(
                InvoiceLine(
                    invoice = invoice,
                    description = li.description,
                    unitAmount = li.unitAmount,
                    quantity = li.quantity.coerceAtLeast(1),
                    position = i,
                ),
            )
        }
    }

    private fun setLinesFromRequestData(invoice: Invoice, items: List<RequestLineItem>) {
        invoice.lines.clear()
        items.forEachIndexed { i, item ->
            invoice.lines.add(
                InvoiceLine(
                    invoice = invoice,
                    description = item.description,
                    unitAmount = item.unitAmount,
                    quantity = item.quantity.coerceAtLeast(1),
                    position = i,
                ),
            )
        }
    }

    private fun requireBusiness(businessId: Long): Business =
        businessRepository.findById(businessId)
            .orElseThrow { EntityNotFoundException("Business not found") }

    private fun businessDto(businessId: Long): BusinessDto {
        val business = requireBusiness(businessId)
        return BusinessDto(business.id!!, business.name)
    }

    internal fun toResponse(invoice: Invoice, business: BusinessDto): InvoiceResponse {
        val lines = invoice.lines.map {
            LineResponse(
                id = it.id!!,
                description = it.description!!,
                unitAmount = it.unitAmount!!,
                quantity = it.quantity,
                lineTotal = it.unitAmount!!.multiply(BigDecimal(it.quantity)).setScale(2, RoundingMode.HALF_UP),
            )
        }
        val total = lines.fold(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)) { acc, l -> acc.add(l.lineTotal) }
        val overdue = invoice.status == InvoiceStatus.SENT &&
            invoice.dueDate != null &&
            invoice.dueDate!!.isBefore(LocalDate.now())
        val customer = invoice.customer!!.let {
            CustomerDto(it.id!!, it.username, it.avatarUrl)
        }

        return InvoiceResponse(
            id = invoice.id!!,
            number = invoice.number,
            status = invoice.status,
            currency = invoice.currency,
            issueDate = invoice.issueDate,
            dueDate = invoice.dueDate,
            notes = invoice.notes,
            customer = customer,
            business = business,
            lines = lines,
            total = total,
            sourceRequestId = invoice.sourceRequestId,
            overdue = overdue,
            createdAt = invoice.createdAt,
            sentAt = invoice.sentAt,
            paidAt = invoice.paidAt,
        )
    }
}
