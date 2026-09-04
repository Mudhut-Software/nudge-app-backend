package com.mudhut.nudge.servicerequests.events

/**
 * Which side caused a status change.
 *
 * Needed because the target status alone no longer identifies who should be
 * told. A customer accepting a proposed time produces CONFIRMED, but it is the
 * provider who needs the email — not the customer who just clicked the button.
 * The same applies to DECLINED, which a provider or a customer can both reach.
 */
enum class RequestActor {
    PROVIDER,
    CUSTOMER,
}
