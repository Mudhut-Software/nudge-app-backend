package com.mudhut.nudge.servicerequests.services

import com.mudhut.nudge.servicerequests.entities.PricingState
import com.mudhut.nudge.servicerequests.entities.ServiceRequest
import com.mudhut.nudge.utils.exceptions.PriceNotSettledException
import java.math.BigDecimal

/**
 * Whether a booking's price is settled enough to complete it, answer the "did this happen?"
 * prompt, or invoice it: no proposal is awaiting an answer, and a to-be-confirmed booking has an
 * accepted price. An estimate never changed is its own price, so ESTIMATED passes on its own.
 *
 * Reads the request's snapshots only (sent requests never consult the live catalogue).
 */
object PriceGate {

    const val NOT_SETTLED_MESSAGE = "The price for this booking hasn't been agreed yet"

    fun settled(request: ServiceRequest, outstandingPrice: Boolean): Boolean =
        settled(stateOf(request), request.agreedPriceAmount, outstandingPrice)

    fun settled(state: PricingState, agreedAmount: BigDecimal?, outstandingPrice: Boolean): Boolean =
        !outstandingPrice && (state != PricingState.TO_BE_CONFIRMED || agreedAmount != null)

    fun requireSettled(request: ServiceRequest, outstandingPrice: Boolean) {
        if (!settled(request, outstandingPrice)) throw PriceNotSettledException(NOT_SETTLED_MESSAGE)
    }

    fun stateOf(request: ServiceRequest): PricingState =
        RequestPricing.state(RequestItemMapping.pricingInput(request.items))
}
