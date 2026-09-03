package com.mudhut.nudge.servicerequests.entities

/**
 * Where a proposal ended up.
 *
 * Only one proposal per request may be OUTSTANDING at a time. That is enforced
 * in the service rather than the schema, so multi-round negotiation would need
 * no migration later.
 */
enum class ProposalOutcome {
    OUTSTANDING,
    ACCEPTED,
    REJECTED,
}
