package com.mudhut.nudge.businesses.entities

enum class BusinessStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED,

    /**
     * Terminal. Set when the owner deletes their account — distinct from
     * INACTIVE, which reads as "paused, coming back".
     *
     * Discovery filters on ACTIVE, so a closed business leaves browse and search
     * with no extra filtering.
     */
    CLOSED,
}
