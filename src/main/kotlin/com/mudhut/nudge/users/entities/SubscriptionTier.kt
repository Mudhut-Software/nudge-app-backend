package com.mudhut.nudge.users.entities

enum class SubscriptionTier {
    /** The whole app, capped by jobs per month. Assigned at registration. */
    FREE,

    /** The whole app, uncapped. */
    PRO,
}
