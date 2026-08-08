package com.mudhut.nudge.invoices.spi

import java.math.BigDecimal

/** One derived invoice line from a service request (item or addon). */
data class RequestLineItem(
    val description: String,
    val unitAmount: BigDecimal,
    val quantity: Int,
)

/** Line data for generating an invoice from a service request, supplied by servicerequests. */
data class RequestLineData(
    val customerId: Long,
    val currency: String,
    val lines: List<RequestLineItem>,
)
