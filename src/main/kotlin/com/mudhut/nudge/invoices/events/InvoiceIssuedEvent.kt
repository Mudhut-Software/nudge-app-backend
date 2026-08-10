package com.mudhut.nudge.invoices.events

import java.math.BigDecimal

/** Published when an invoice moves DRAFT -> SENT (a display number is assigned). */
data class InvoiceIssuedEvent(
    val invoiceId: Long,
    val businessId: Long,
    val customerId: Long,
    val number: String,
    val total: BigDecimal,
    val currency: String,
    val issuedByUserId: Long,
)
