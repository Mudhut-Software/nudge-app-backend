package com.mudhut.nudge.servicerequests.spi

import com.mudhut.nudge.invoices.spi.CustomerDirectoryQuery
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestRepository
import org.springframework.stereotype.Component

/** Supplies invoices' customer-relationship check without invoices depending on servicerequests internals. */
@Component
class CustomerDirectoryQueryImpl(
    private val repo: ServiceRequestRepository,
) : CustomerDirectoryQuery {
    override fun isCustomerOfBusiness(businessId: Long, userId: Long): Boolean =
        repo.existsByBusinessIdAndCustomerId(businessId, userId)
}
