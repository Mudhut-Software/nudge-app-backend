package com.mudhut.nudge.utils.exceptions

/**
 * A completion-type action was refused because the booking's price is still open: a proposal is
 * awaiting the customer's answer, or a to-be-confirmed booking has no accepted price yet. 409.
 */
class PriceNotSettledException(message: String) : RuntimeException(message)
