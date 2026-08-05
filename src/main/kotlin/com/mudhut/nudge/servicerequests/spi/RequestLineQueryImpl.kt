package com.mudhut.nudge.servicerequests.spi

import com.mudhut.nudge.invoices.spi.RequestLineData
import com.mudhut.nudge.invoices.spi.RequestLineItem
import com.mudhut.nudge.invoices.spi.RequestLineQuery
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/** Supplies invoices' request-line derivation without invoices depending on servicerequests internals. */
@Component
class RequestLineQueryImpl(
    private val repo: ServiceRequestRepository,
) : RequestLineQuery {

    @Transactional(readOnly = true)
    override fun forRequest(businessId: Long, requestId: Long): RequestLineData? {
        val req = repo.findByIdAndBusinessId(requestId, businessId).orElse(null) ?: return null
        if (req.status != ServiceRequestStatus.COMPLETED) return null
        val currency = req.items.firstNotNullOfOrNull { it.snapshotPriceCurrency } ?: "USD"
        val lines = buildList {
            req.items.forEach { item ->
                add(RequestLineItem(item.snapshotTitle ?: "Item", item.snapshotPriceAmount ?: BigDecimal.ZERO, 1))
                item.addons.forEach { a ->
                    add(RequestLineItem("+ " + (a.snapshotTitle ?: "Add-on"), a.snapshotPriceDelta ?: BigDecimal.ZERO, a.quantity))
                }
            }
        }
        return RequestLineData(customerId = req.customer!!.id!!, currency = currency, lines = lines)
    }
}
