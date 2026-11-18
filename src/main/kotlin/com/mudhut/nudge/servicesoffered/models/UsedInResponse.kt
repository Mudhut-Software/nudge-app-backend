package com.mudhut.nudge.servicesoffered.models

/** An item that links the queried item into one of its groups. */
data class UsedInResponse(
    val itemId: Long,
    val itemTitle: String,
    val groupName: String,
)
