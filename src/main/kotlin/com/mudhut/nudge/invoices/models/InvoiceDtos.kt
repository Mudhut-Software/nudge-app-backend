package com.mudhut.nudge.invoices.models

import com.mudhut.nudge.invoices.entities.InvoiceStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class CustomerDto(val userId: Long, val name: String?, val avatarUrl: String?)
data class BusinessDto(val id: Long, val name: String?)

data class LineResponse(
    val id: Long,
    val description: String,
    val unitAmount: BigDecimal,
    val quantity: Int,
    val lineTotal: BigDecimal,
)

data class InvoiceResponse(
    val id: Long,
    val number: String?,
    val status: InvoiceStatus,
    val currency: String,
    val issueDate: LocalDate?,
    val dueDate: LocalDate?,
    val notes: String?,
    val customer: CustomerDto,
    val business: BusinessDto,
    val lines: List<LineResponse>,
    val total: BigDecimal,
    val sourceRequestId: Long?,
    val overdue: Boolean,
    val createdAt: LocalDateTime?,
    val sentAt: LocalDateTime?,
    val paidAt: LocalDateTime?,
)

data class LineInput(
    @field:NotBlank @field:Size(max = 200) val description: String,
    @field:NotNull val unitAmount: BigDecimal,
    val quantity: Int = 1,
)

data class CreateInvoiceRequest(
    @field:NotNull val customerId: Long,
    @field:NotBlank @field:Size(max = 3) val currency: String,
    val dueDate: LocalDate? = null,
    val notes: String? = null,
    @field:Valid val lines: List<LineInput> = emptyList(),
)

data class UpdateInvoiceRequest(
    @field:NotBlank @field:Size(max = 3) val currency: String,
    val dueDate: LocalDate? = null,
    val notes: String? = null,
    @field:Valid val lines: List<LineInput> = emptyList(),
)
