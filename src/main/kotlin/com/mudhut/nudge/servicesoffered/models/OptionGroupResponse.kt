package com.mudhut.nudge.servicesoffered.models

import com.mudhut.nudge.servicesoffered.entities.GroupSelection
import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedStatus
import java.math.BigDecimal

data class OptionGroupResponse(
    val id: Long,
    val name: String,
    val selection: GroupSelection,
    val required: Boolean,
    val minPicks: Int?,
    val maxPicks: Int?,
    val choices: List<GroupChoiceResponse>,
)

data class GroupChoiceResponse(
    val id: Long,
    val itemId: Long,
    val title: String,
    val coverImageUrl: String?,
    val itemPriceAmount: BigDecimal?,
    val priceOverride: BigDecimal?,
    /** priceOverride, else the item's own price; null means "included". */
    val effectivePrice: BigDecimal?,
    val quantityEnabled: Boolean,
    val maxQuantity: Int?,
    val itemStatus: ServiceOfferedStatus,
)
