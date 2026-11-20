package com.mudhut.nudge.servicerequests.services

import com.mudhut.nudge.servicerequests.entities.PricingState
import com.mudhut.nudge.servicerequests.entities.ServiceRequestItem
import com.mudhut.nudge.servicerequests.entities.ServiceRequestItemPick
import com.mudhut.nudge.servicerequests.models.ItemPickResponse
import com.mudhut.nudge.servicerequests.models.ServiceRequestItemResponse
import com.mudhut.nudge.servicesoffered.entities.GroupChoice
import java.math.BigDecimal

/**
 * Item and pick mapping shared by the customer and provider `toResponse`, so the two sides of the
 * wire read the same. A draft (`live = true`) shows the catalogue as it is now; a sent request shows
 * its snapshot and nothing else: a null snapshot price means "to be confirmed" and must stay so even
 * if the provider prices the item later. Only the title falls back, as a label, never a price.
 */
internal object RequestItemMapping {

    /** Effective price of a choice in its group: the override, else the linked item's price; null = included. */
    fun effectivePrice(choice: GroupChoice): BigDecimal? = choice.priceOverride ?: choice.item?.priceAmount

    fun items(items: List<ServiceRequestItem>, live: Boolean): List<ServiceRequestItemResponse> =
        items.sortedBy { it.position }.map { item(it, live) }

    /** Pricing input read from a sent request's snapshots only; never the live catalogue. */
    fun pricingInput(items: List<ServiceRequestItem>): List<PricingInputItem> =
        items.sortedBy { it.position }.map { item ->
            PricingInputItem(
                serviceId = item.service?.id,
                title = item.snapshotTitle ?: item.service?.title ?: "Item",
                price = item.snapshotPriceAmount,
                currency = item.snapshotPriceCurrency,
                isEstimate = item.snapshotPriceIsEstimate,
                quantity = item.quantity,
                picks = item.picks.sortedBy { it.position }.map {
                    PricingInputPick(
                        groupName = it.snapshotGroupName ?: "Choice",
                        title = it.snapshotTitle ?: "Choice",
                        price = it.snapshotPrice,
                        quantity = it.quantity,
                    )
                },
            )
        }

    fun pricing(items: List<ServiceRequestItemResponse>): Pair<PricingState, BigDecimal?> {
        val input = items.map { i ->
            PricingInputItem(
                serviceId = i.serviceId,
                title = i.title,
                price = i.priceAmount,
                currency = i.priceCurrency,
                isEstimate = i.priceIsEstimate,
                quantity = i.quantity,
                picks = i.picks.map { PricingInputPick(it.groupName, it.title, it.price, it.quantity) },
            )
        }
        return RequestPricing.state(input) to RequestPricing.total(input)
    }

    private fun item(item: ServiceRequestItem, live: Boolean): ServiceRequestItemResponse {
        val svc = item.service
        val liveUnit = svc?.quantityUnit?.takeIf { svc.quantityEnabled }
        return ServiceRequestItemResponse(
            serviceId = svc?.id,
            title = (if (live) svc?.title else item.snapshotTitle ?: svc?.title) ?: "(unknown)",
            priceAmount = if (live) svc?.priceAmount else item.snapshotPriceAmount,
            priceCurrency = if (live) svc?.priceCurrency else item.snapshotPriceCurrency,
            priceIsEstimate = if (live) svc?.priceIsEstimate ?: false else item.snapshotPriceIsEstimate,
            estimateReason = if (live) svc?.estimateReason?.takeIf { svc.priceIsEstimate } else item.snapshotEstimateReason,
            quantity = item.quantity,
            quantityUnit = if (live) liveUnit else item.snapshotQuantityUnit,
            coverImageUrl = if (live) svc?.coverImageUrl else item.snapshotCoverUrl,
            position = item.position,
            picks = item.picks.sortedBy { it.position }.map { pick(it, live) },
        )
    }

    private fun pick(pick: ServiceRequestItemPick, live: Boolean): ItemPickResponse {
        // A draft whose choice was deleted has only the label stored with the pick to show.
        val choice = pick.choice.takeIf { live }
        return ItemPickResponse(
            id = pick.id ?: 0L,
            choiceId = pick.choice?.id,
            groupId = pick.groupId,
            groupName = (choice?.group?.name ?: pick.snapshotGroupName) ?: "(unknown)",
            title = (choice?.item?.title ?: pick.snapshotTitle) ?: "(unknown)",
            price = if (choice != null) effectivePrice(choice) else pick.snapshotPrice,
            quantity = pick.quantity,
            position = pick.position,
        )
    }
}
