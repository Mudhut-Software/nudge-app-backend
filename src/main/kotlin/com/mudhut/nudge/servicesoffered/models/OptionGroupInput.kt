package com.mudhut.nudge.servicesoffered.models

import com.mudhut.nudge.servicesoffered.entities.GroupSelection
import java.math.BigDecimal

data class OptionGroupInput(
    /** Present = update that group in place; null = new group. */
    val id: Long? = null,
    val name: String,
    val selection: GroupSelection,
    val required: Boolean = false,
    val minPicks: Int? = null,
    val maxPicks: Int? = null,
    val choices: List<GroupChoiceInput> = emptyList(),
)

data class GroupChoiceInput(
    val id: Long? = null,
    val itemId: Long,
    val priceOverride: BigDecimal? = null,
    val quantityEnabled: Boolean = false,
    val maxQuantity: Int? = null,
)
