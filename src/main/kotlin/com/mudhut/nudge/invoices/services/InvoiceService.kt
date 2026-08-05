package com.mudhut.nudge.invoices.services

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.businesses.services.BusinessService
import com.mudhut.nudge.invoices.entities.Invoice
import com.mudhut.nudge.invoices.entities.InvoiceLine
import com.mudhut.nudge.invoices.entities.InvoiceStatus
import com.mudhut.nudge.invoices.models.BusinessDto
import com.mudhut.nudge.invoices.models.CreateInvoiceRequest
import com.mudhut.nudge.invoices.models.CustomerDto
import com.mudhut.nudge.invoices.models.InvoiceResponse
import com.mudhut.nudge.invoices.models.LineInput
import com.mudhut.nudge.invoices.models.LineResponse
import com.mudhut.nudge.invoices.repositories.InvoiceRepository
import com.mudhut.nudge.invoices.spi.RequestLineItem
import com.mudhut.nudge.invoices.spi.RequestLineQuery
import com.mudhut.nudge.users.repositories.UserRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@Service
class InvoiceService(
    private val invoiceRepository: InvoiceRepository,
    private val businessService: BusinessService,
    private val businessRepository: BusinessRepository,
    private val userRepository: UserRepository,
    private val requestLineQuery: RequestLineQuery,
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

    // --- shared helpers (also used by Task 4) ---

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
                lineTotal = it.unitAmount!!.multiply(BigDecimal(it.quantity)),
            )
        }
        val total = lines.fold(BigDecimal.ZERO) { acc, l -> acc.add(l.lineTotal) }
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
