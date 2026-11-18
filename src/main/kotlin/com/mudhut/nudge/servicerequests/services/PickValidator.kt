package com.mudhut.nudge.servicerequests.services

import com.mudhut.nudge.servicerequests.models.PickInput
import com.mudhut.nudge.servicesoffered.entities.GroupChoice
import com.mudhut.nudge.servicesoffered.entities.GroupSelection
import com.mudhut.nudge.servicesoffered.entities.OptionGroup
import com.mudhut.nudge.servicesoffered.entities.ServiceOffered
import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedStatus

/**
 * Checks one booked item's quantity and picks against the live catalogue. Pure.
 *
 * Returns customer-facing problems, each prefixed with the group's name (or the item's title for
 * its own quantity). Input that no honest client could send (a choice from another item, the same
 * choice twice, a quantity below 1) fails a `require` instead.
 *
 * Rules: the item's own quantity range applies because it is booked directly; inside a group only
 * the choice's stepper applies. Pick quantities count toward a group's min/max. A SINGLE group
 * takes at most one pick of quantity 1, and exactly one when required.
 */
object PickValidator {

    /**
     * @param missingGroupIds groups in which a previously picked choice has since been deleted; each
     *   is reported once as "no longer available" and its limits are not checked on top.
     */
    fun problems(
        item: ServiceOffered,
        quantity: Int,
        picks: List<PickInput>,
        missingGroupIds: Set<Long> = emptySet(),
    ): List<String> {
        val choicesById: Map<Long, GroupChoice> = item.groups.flatMap { it.choices }
            .filter { it.id != null }.associateBy { it.id!! }
        picks.forEach { p ->
            require(p.choiceId in choicesById) { "Choice ${p.choiceId} is not an option of service ${item.id}" }
            require(p.quantity >= 1) { "Pick quantity must be at least 1" }
        }
        require(picks.map { it.choiceId }.distinct().size == picks.size) { "The same choice is picked twice" }

        val out = mutableListOf<String>()
        quantityProblem(item, quantity)?.let(out::add)

        item.groups.sortedBy { it.position }.forEach { group ->
            val ids = group.choices.mapNotNull { it.id }.toSet()
            val inGroup = picks.filter { it.choiceId in ids }
            out += groupProblems(group, inGroup, choicesById, group.id in missingGroupIds)
        }
        return out
    }

    private fun quantityProblem(item: ServiceOffered, quantity: Int): String? {
        val title = item.title ?: "This item"
        if (!item.quantityEnabled) {
            return if (quantity == 1) null else "$title: quantity can't be changed for this item"
        }
        val min = item.quantityMin
        val max = item.quantityMax
        return when {
            max != null && (quantity < min || quantity > max) -> "$title: quantity must be between $min and $max"
            max == null && quantity < min -> "$title: quantity must be at least $min"
            else -> null
        }
    }

    private fun groupProblems(
        group: OptionGroup,
        picks: List<PickInput>,
        choicesById: Map<Long, GroupChoice>,
        hasMissingChoice: Boolean,
    ): List<String> {
        val name = group.name ?: "Options"
        val unavailable = hasMissingChoice ||
            picks.any { choicesById[it.choiceId]?.item?.status != ServiceOfferedStatus.ACTIVE }
        if (unavailable) return listOf("$name: this choice is no longer available")

        val out = mutableListOf<String>()
        picks.forEach { p ->
            val choice = choicesById.getValue(p.choiceId)
            val title = choice.item?.title ?: "this choice"
            when {
                !choice.quantityEnabled && p.quantity != 1 -> out += "$name: $title can be picked once"
                choice.quantityEnabled && choice.maxQuantity != null && p.quantity > choice.maxQuantity!! ->
                    out += "$name: at most ${choice.maxQuantity} of $title"
            }
        }
        if (out.isNotEmpty()) return out

        val count = picks.sumOf { it.quantity }
        if (group.selection == GroupSelection.SINGLE) {
            if (count > 1 || (group.required && count == 0)) out += "$name: choose one"
            return out
        }
        // A required group always needs at least one pick, even if minPicks says 0 (see Bookability).
        val min = if (group.required) maxOf(group.minPicks ?: 1, 1) else group.minPicks
        val max = group.maxPicks
        when {
            min != null && min == max && count != min -> out += "$name: choose exactly $min"
            min != null && count < min -> out += "$name: pick at least $min"
            max != null && count > max -> out += "$name: pick at most $max"
        }
        return out
    }
}
