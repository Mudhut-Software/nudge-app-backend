package com.mudhut.nudge.servicesoffered.models

import java.math.BigDecimal

data class ServiceAddonResponse(
    val id: Long,
    val serviceId: Long,
    val title: String,
    val description: String?,
    val coverImageUrl: String?,
    val coverImagePublicId: String?,
    val priceDelta: BigDecimal?,
    val priceUnit: String?,
    val defaultSelected: Boolean,
    val quantifiable: Boolean,
    val defaultQuantity: Int,
    val maxQuantity: Int?,
    val position: Int,
)
