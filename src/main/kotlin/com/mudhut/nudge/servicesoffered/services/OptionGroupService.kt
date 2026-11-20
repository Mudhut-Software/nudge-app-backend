package com.mudhut.nudge.servicesoffered.services

import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.services.BusinessService
import com.mudhut.nudge.servicesoffered.entities.GroupChoice
import com.mudhut.nudge.servicesoffered.entities.GroupSelection
import com.mudhut.nudge.servicesoffered.entities.OptionGroup
import com.mudhut.nudge.servicesoffered.entities.ServiceOffered
import com.mudhut.nudge.servicesoffered.events.GroupChoicesDeletedEvent
import com.mudhut.nudge.servicesoffered.models.GroupChoiceInput
import com.mudhut.nudge.servicesoffered.models.OptionGroupInput
import com.mudhut.nudge.servicesoffered.models.OptionGroupResponse
import com.mudhut.nudge.servicesoffered.models.UsedInResponse
import com.mudhut.nudge.servicesoffered.repositories.GroupChoiceRepository
import com.mudhut.nudge.servicesoffered.repositories.ServiceOfferedRepository
import jakarta.persistence.EntityNotFoundException
import jakarta.transaction.Transactional
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class OptionGroupService(
    private val serviceRepo: ServiceOfferedRepository,
    private val choiceRepo: GroupChoiceRepository,
    private val businessService: BusinessService,
    private val events: ApplicationEventPublisher,
) {

    /**
     * Replaces the item's whole group set with [groups] (list order = position). Rows sent with
     * an id are updated in place, new ones are added, missing ones are removed; the ids of every
     * removed choice are published in a [GroupChoicesDeletedEvent] before any row goes.
     */
    @Transactional
    fun replace(serviceId: Long, userEmail: String, groups: List<OptionGroupInput>): List<OptionGroupResponse> {
        val item = requireService(serviceId)
        businessService.requireRole(item.business!!.id!!, userEmail, BusinessRole.MANAGER)

        val linked = loadLinkedItems(item, groups)
        groups.forEach { validate(item, it, linked) }

        val existingGroups = item.groups.associateBy { it.id }
        groups.forEach { input ->
            if (input.id != null) {
                require(existingGroups.containsKey(input.id)) {
                    "Group '${input.name.trim()}': id ${input.id} does not belong to service $serviceId"
                }
            }
        }
        groups.filter { it.id == null }.forEach { input ->
            input.choices.forEach { c ->
                require(c.id == null) { "Group '${input.name.trim()}': choice ${c.id} does not belong to it" }
            }
        }
        val keptGroupIds = groups.mapNotNull { it.id }.toSet()
        val removedGroups = item.groups.filter { it.id !in keptGroupIds }

        // Work out removed choices up front so the event can go out before anything is removed.
        val removedChoices = removedGroups.flatMap { it.choices } + groups.mapNotNull { input ->
            input.id?.let { gid ->
                val group = existingGroups.getValue(gid)
                input.choices.forEach { c ->
                    if (c.id != null) require(group.choices.any { it.id == c.id }) {
                        "Group '${input.name.trim()}': choice ${c.id} does not belong to it"
                    }
                }
                val keptChoiceIds = keptChoiceIds(group, input.choices)
                group.choices.filter { it.id !in keptChoiceIds }
            }
        }.flatten()
        val removedIds = removedChoices.mapNotNull { it.id }
        if (removedIds.isNotEmpty()) events.publishEvent(GroupChoicesDeletedEvent(removedIds))

        item.groups.removeAll(removedGroups.toSet())
        groups.forEachIndexed { index, input ->
            val group = input.id?.let { existingGroups.getValue(it) }
                ?: OptionGroup(item = item).also { item.groups.add(it) }
            group.name = input.name.trim()
            group.selection = input.selection
            group.required = input.required
            group.minPicks = input.minPicks
            group.maxPicks = input.maxPicks
            group.position = index
            applyChoices(group, input.choices, linked)
        }

        // `item` is managed: flush cascades persist to the new children (stamping ids on the
        // very instances we map from). save() would merge and persist copies instead.
        serviceRepo.flush()
        return item.groups.sortedBy { it.position }.map { it.toResponse() }
    }

    fun usedIn(serviceId: Long, userEmail: String): List<UsedInResponse> {
        val item = requireService(serviceId)
        businessService.requireRole(item.business!!.id!!, userEmail, BusinessRole.STAFF)
        return choiceRepo.findAllByItemId(serviceId).map {
            val group = it.group!!
            UsedInResponse(
                itemId = group.item!!.id!!,
                itemTitle = group.item!!.title!!,
                groupName = group.name!!,
            )
        }
    }

    private fun applyChoices(group: OptionGroup, inputs: List<GroupChoiceInput>, linked: Map<Long, ServiceOffered>) {
        val existing = group.choices.associateBy { it.id }
        val keptIds = keptChoiceIds(group, inputs)
        group.choices.removeAll { it.id !in keptIds }
        inputs.forEachIndexed { index, input ->
            val choice = input.id?.takeIf { it in keptIds }?.let { existing.getValue(it) }
                ?: GroupChoice(group = group).also { group.choices.add(it) }
            choice.item = linked.getValue(input.itemId)
            choice.priceOverride = input.priceOverride
            choice.quantityEnabled = input.quantityEnabled
            choice.maxQuantity = if (input.quantityEnabled) input.maxQuantity else null
            choice.position = index
        }
    }

    /**
     * Ids of [group]'s choices that [inputs] keep: sent with their id and still linking the same
     * item. A kept id re-pointed at another item is a remove plus an add, so picks of the old item
     * are released via [GroupChoicesDeletedEvent] instead of silently switching to the new one.
     */
    private fun keptChoiceIds(group: OptionGroup, inputs: List<GroupChoiceInput>): Set<Long> {
        val existing = group.choices.associateBy { it.id }
        return inputs.filter { it.id != null && existing[it.id]?.item?.id == it.itemId }
            .mapNotNull { it.id }.toSet()
    }

    private fun loadLinkedItems(parent: ServiceOffered, groups: List<OptionGroupInput>): Map<Long, ServiceOffered> {
        val ids = groups.flatMap { g -> g.choices.map { it.itemId } }.distinct()
        if (ids.isEmpty()) return emptyMap()
        return serviceRepo.findAllById(ids).associateBy { it.id!! }
    }

    private fun validate(parent: ServiceOffered, input: OptionGroupInput, linked: Map<Long, ServiceOffered>) {
        val name = input.name.trim()
        fun fail(reason: String): Nothing = throw IllegalArgumentException("Group '$name': $reason")

        if (name.isEmpty()) throw IllegalArgumentException("A group needs a name")
        if (name.length > 80) fail("name must be at most 80 characters")

        val min = input.minPicks
        val max = input.maxPicks
        if (input.selection == GroupSelection.SINGLE) {
            if (min != null || max != null) fail("a single-choice group cannot set minPicks or maxPicks")
            if (input.choices.any { it.quantityEnabled }) fail("a single-choice group cannot have quantity-enabled choices")
        }
        if (min != null && min < 0) fail("minPicks cannot be negative")
        if (max != null && max < 1) fail("maxPicks must be at least 1")
        if (min != null && max != null && min > max) fail("minPicks cannot be greater than maxPicks")

        val seen = HashSet<Long>()
        input.choices.forEach { choice ->
            if (!seen.add(choice.itemId)) {
                fail("item ${linked[choice.itemId]?.title ?: choice.itemId} appears more than once")
            }
            if (choice.itemId == parent.id) fail("an item cannot be linked into its own groups")
            val child = linked[choice.itemId] ?: fail("item ${choice.itemId} not found")
            if (child.business?.id != parent.business?.id) fail("item ${child.title} belongs to another business")
            if (choice.priceOverride != null && choice.priceOverride.signum() < 0) {
                fail("priceOverride for ${child.title} cannot be negative")
            }
            if (choice.quantityEnabled && choice.maxQuantity != null && choice.maxQuantity < 1) {
                fail("maxQuantity for ${child.title} must be at least 1")
            }
            if (choice.priceOverride == null && child.priceAmount != null &&
                parent.priceCurrency != null && child.priceCurrency != parent.priceCurrency
            ) {
                fail(
                    "${child.title} is priced in ${child.priceCurrency}, not ${parent.priceCurrency}; " +
                        "set a priceOverride for it"
                )
            }
        }
    }

    private fun requireService(id: Long): ServiceOffered =
        serviceRepo.findById(id).orElseThrow { EntityNotFoundException("Service not found: $id") }
}
