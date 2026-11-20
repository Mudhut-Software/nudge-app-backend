package com.mudhut.nudge.servicerequests.spi

import com.mudhut.nudge.invoices.spi.RequestLineData
import com.mudhut.nudge.invoices.spi.RequestLineItem
import com.mudhut.nudge.invoices.spi.RequestLineQuery
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestRepository
import com.mudhut.nudge.servicerequests.services.RequestItemMapping
import com.mudhut.nudge.servicerequests.services.RequestPricing
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** Supplies invoices' request-line derivation without invoices depending on servicerequests internals. */
@Component
class RequestLineQueryImpl(
    private val repo: ServiceRequestRepository,
) : RequestLineQuery {

    @Transactional(readOnly = true)
    override fun forRequest(businessId: Long, requestId: Long): RequestLineData? {
        val req = repo.findByIdAndBusinessId(requestId, businessId).orElse(null) ?: return null
        if (req.status != ServiceRequestStatus.COMPLETED) return null
        val input = RequestItemMapping.pricingInput(req.items)
        val lines = RequestPricing.lines(input, req.agreedPriceAmount, req.agreedPriceNote)
            .map { RequestLineItem(it.description, it.unitAmount, it.quantity) }
        val currency = RequestPricing.currency(input, req.agreedPriceCurrency)
        return RequestLineData(customerId = req.customer!!.id!!, currency = currency, lines = lines)
    }
}
