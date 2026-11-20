package com.mudhut.nudge.servicesoffered.services

import com.mudhut.nudge.servicesoffered.entities.GroupChoice
import com.mudhut.nudge.servicesoffered.entities.OptionGroup
import com.mudhut.nudge.servicesoffered.models.GroupChoiceResponse
import com.mudhut.nudge.servicesoffered.models.OptionGroupResponse

internal fun OptionGroup.toResponse() = OptionGroupResponse(
    id = id!!,
    name = name!!,
    selection = selection,
    required = required,
    minPicks = minPicks,
    maxPicks = maxPicks,
    choices = choices.sortedBy { it.position }.map { it.toResponse() },
)

internal fun GroupChoice.toResponse(): GroupChoiceResponse {
    val linked = item!!
    return GroupChoiceResponse(
        id = id!!,
        itemId = linked.id!!,
        title = linked.title!!,
        coverImageUrl = linked.coverImageUrl,
        itemPriceAmount = linked.priceAmount,
        priceOverride = priceOverride,
        effectivePrice = priceOverride ?: linked.priceAmount,
        quantityEnabled = quantityEnabled,
        maxQuantity = maxQuantity,
        itemStatus = linked.status,
    )
}
