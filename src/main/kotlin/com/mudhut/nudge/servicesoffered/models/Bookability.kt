package com.mudhut.nudge.servicesoffered.models

import com.mudhut.nudge.servicesoffered.entities.OptionGroup
import com.mudhut.nudge.servicesoffered.entities.ServiceOffered
import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedStatus

/**
 * Whether an item can still be ordered: every option group must be satisfiable from the choices
 * that are currently active (linked item ACTIVE). Pure; lives in `models` so `discovery` may use it.
 */
object Bookability {

    /** Name of the first group (by position) that can no longer be satisfied, or null. */
    fun blockingGroup(item: ServiceOffered): String? =
        item.groups.sortedBy { it.position }.firstOrNull { isBlocked(it) }?.name

    private fun isBlocked(group: OptionGroup): Boolean {
        val active = group.choices.filter { it.item?.status == ServiceOfferedStatus.ACTIVE }
        if (group.required && active.isEmpty()) return true
        // A required group always needs at least one pick, even if minPicks says 0 (see PickValidator).
        val min = (if (group.required) maxOf(group.minPicks ?: 1, 1) else group.minPicks) ?: return false
        // Capacity: a stepper choice contributes up to its max quantity (unbounded if none).
        var capacity = 0L
        for (c in active) {
            if (c.quantityEnabled) {
                val max = c.maxQuantity ?: return false
                capacity += max
            } else {
                capacity += 1
            }
        }
        return min > capacity
    }
}
