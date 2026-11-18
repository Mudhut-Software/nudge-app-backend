package com.mudhut.nudge.servicerequests.services

import com.mudhut.nudge.servicerequests.entities.PricingState
import java.math.BigDecimal
import java.math.RoundingMode

data class PricedLine(val description: String, val unitAmount: BigDecimal, val quantity: Int)

data class PricingInputPick(
    val groupName: String,
    val title: String,
    val price: BigDecimal?,
    val quantity: Int,
)

data class PricingInputItem(
    val serviceId: Long?,
    val title: String,
    val price: BigDecimal?,
    val currency: String?,
    val isEstimate: Boolean,
    val quantity: Int,
    val picks: List<PricingInputPick>,
)

/** Single home for pricing rules: state, totals, invoice lines, analytics work, currency. Pure. */
object RequestPricing {

    private const val DEFAULT_CURRENCY = "USD"

    fun state(items: List<PricingInputItem>): PricingState = when {
        items.any { it.price == null } -> PricingState.TO_BE_CONFIRMED
        items.any { it.isEstimate } -> PricingState.ESTIMATED
        else -> PricingState.FIRM
    }

    fun total(items: List<PricingInputItem>): BigDecimal? {
        if (state(items) == PricingState.TO_BE_CONFIRMED) return null
        return items.fold(BigDecimal.ZERO) { acc, i -> acc + itemTotal(i) }
    }

    fun lines(items: List<PricingInputItem>, agreed: BigDecimal?, agreedNote: String?): List<PricedLine> {
        if (agreed != null) {
            val suffix = if (agreedNote.isNullOrBlank()) "" else ": $agreedNote"
            return listOf(PricedLine("Agreed price$suffix", agreed, 1))
        }
        return items.flatMap { item ->
            listOf(PricedLine(item.title, item.price ?: BigDecimal.ZERO, item.quantity)) +
                item.picks.map {
                    PricedLine("+ ${it.groupName}: ${it.title}", it.price ?: BigDecimal.ZERO, it.quantity)
                }
        }
    }

    fun workByItem(items: List<PricingInputItem>, agreed: BigDecimal?): Map<Long?, BigDecimal> {
        if (agreed == null) return sumByKey(items.map { it.serviceId to itemTotal(it) })
        if (items.isEmpty()) return emptyMap()
        val targets = items.filter { it.isEstimate || it.price == null }.ifEmpty { items }
        val share = agreed.divide(BigDecimal(targets.size), 2, RoundingMode.DOWN)
        val remainder = agreed.setScale(2, RoundingMode.HALF_UP) - share * BigDecimal(targets.size)
        val first = targets.first()
        return sumByKey(
            items.map { item ->
                item.serviceId to when {
                    item === first -> share + remainder
                    targets.any { it === item } -> share
                    else -> BigDecimal.ZERO.setScale(2)
                }
            },
        )
    }

    /** Sums amounts that share a key so colliding service ids never drop money. */
    private fun sumByKey(pairs: List<Pair<Long?, BigDecimal>>): Map<Long?, BigDecimal> {
        val out = LinkedHashMap<Long?, BigDecimal>()
        pairs.forEach { (k, v) -> out.merge(k, v, BigDecimal::add) }
        return out
    }

    fun currency(items: List<PricingInputItem>, agreedCurrency: String?): String =
        agreedCurrency ?: items.firstNotNullOfOrNull { it.currency } ?: DEFAULT_CURRENCY

    private fun itemTotal(item: PricingInputItem): BigDecimal =
        (item.price ?: BigDecimal.ZERO) * BigDecimal(item.quantity) +
            item.picks.fold(BigDecimal.ZERO) { acc, p ->
                acc + (p.price ?: BigDecimal.ZERO) * BigDecimal(p.quantity)
            }
}
