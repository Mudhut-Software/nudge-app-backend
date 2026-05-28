package com.mudhut.nudge.servicesoffered.services

import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.services.BusinessService
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestItemAddonRepository
import com.mudhut.nudge.servicesoffered.entities.PendingMediaDeletion
import com.mudhut.nudge.servicesoffered.entities.ServiceAddon
import com.mudhut.nudge.servicesoffered.entities.ServiceOffered
import com.mudhut.nudge.servicesoffered.models.CreateServiceAddonRequest
import com.mudhut.nudge.servicesoffered.models.ReorderAddonsRequest
import com.mudhut.nudge.servicesoffered.models.ServiceAddonResponse
import com.mudhut.nudge.servicesoffered.models.UpdateServiceAddonRequest
import com.mudhut.nudge.servicesoffered.repositories.PendingMediaDeletionRepository
import com.mudhut.nudge.servicesoffered.repositories.ServiceAddonRepository
import com.mudhut.nudge.servicesoffered.repositories.ServiceOfferedRepository
import com.mudhut.nudge.utils.exceptions.ServiceAddonNotFoundException
import jakarta.persistence.EntityNotFoundException
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class ServiceAddonService(
    private val addonRepo: ServiceAddonRepository,
    private val serviceRepo: ServiceOfferedRepository,
    private val pendingMediaDeletionRepo: PendingMediaDeletionRepository,
    private val requestItemAddonRepo: ServiceRequestItemAddonRepository,
    private val businessService: BusinessService,
) {

    fun list(serviceId: Long, userEmail: String): List<ServiceAddonResponse> {
        val service = requireService(serviceId)
        businessService.requireRole(service.business!!.id!!, userEmail, BusinessRole.STAFF)
        return addonRepo.findAllByServiceIdOrderByPositionAsc(serviceId).map { it.toResponse() }
    }

    @Transactional
    fun create(serviceId: Long, userEmail: String, request: CreateServiceAddonRequest): ServiceAddonResponse {
        val service = requireService(serviceId)
        businessService.requireRole(service.business!!.id!!, userEmail, BusinessRole.MANAGER)

        val nextPosition = addonRepo.findMaxPositionByServiceId(serviceId) + 1
        val (defaultQty, maxQty) = normalizeQuantity(
            quantifiable = request.quantifiable,
            defaultQuantity = request.defaultQuantity,
            maxQuantity = request.maxQuantity,
        )

        val saved = addonRepo.save(
            ServiceAddon(
                service = service,
                title = request.title,
                description = request.description,
                coverImageUrl = request.coverImageUrl,
                coverImagePublicId = request.coverImagePublicId,
                priceDelta = request.priceDelta,
                priceUnit = request.priceUnit,
                defaultSelected = request.defaultSelected,
                quantifiable = request.quantifiable,
                defaultQuantity = defaultQty,
                maxQuantity = maxQty,
                position = nextPosition,
            )
        )
        return saved.toResponse()
    }

    @Transactional
    fun update(
        serviceId: Long,
        addonId: Long,
        userEmail: String,
        request: UpdateServiceAddonRequest,
    ): ServiceAddonResponse {
        val existing = addonRepo.findById(addonId)
            .orElseThrow { ServiceAddonNotFoundException("Addon not found: $addonId") }
        require(existing.service?.id == serviceId) { "Addon $addonId does not belong to service $serviceId" }
        businessService.requireRole(existing.service!!.business!!.id!!, userEmail, BusinessRole.MANAGER)

        request.title?.let { existing.title = it }
        request.description?.let { existing.description = it }

        if (request.coverImageUrl != null || request.coverImagePublicId != null) {
            val previous = existing.coverImagePublicId
            val incomingPublicId = request.coverImagePublicId ?: existing.coverImagePublicId
            if (!previous.isNullOrBlank() && previous != incomingPublicId) {
                pendingMediaDeletionRepo.save(PendingMediaDeletion(publicId = previous))
            }
            request.coverImageUrl?.let { existing.coverImageUrl = it }
            request.coverImagePublicId?.let { existing.coverImagePublicId = it }
        }

        request.priceDelta?.let { existing.priceDelta = it }
        request.priceUnit?.let { existing.priceUnit = it }
        request.defaultSelected?.let { existing.defaultSelected = it }
        request.quantifiable?.let { existing.quantifiable = it }
        request.defaultQuantity?.let { existing.defaultQuantity = it }
        if (request.maxQuantity != null) existing.maxQuantity = request.maxQuantity

        val (defQty, maxQty) = normalizeQuantity(
            quantifiable = existing.quantifiable,
            defaultQuantity = existing.defaultQuantity,
            maxQuantity = existing.maxQuantity,
        )
        existing.defaultQuantity = defQty
        existing.maxQuantity = maxQty

        return existing.toResponse()
    }

    @Transactional
    fun delete(serviceId: Long, addonId: Long, userEmail: String) {
        val existing = addonRepo.findById(addonId)
            .orElseThrow { ServiceAddonNotFoundException("Addon not found: $addonId") }
        require(existing.service?.id == serviceId) { "Addon $addonId does not belong to service $serviceId" }
        businessService.requireRole(existing.service!!.business!!.id!!, userEmail, BusinessRole.MANAGER)

        existing.coverImagePublicId?.takeIf { it.isNotBlank() }?.let {
            pendingMediaDeletionRepo.save(PendingMediaDeletion(publicId = it))
        }
        requestItemAddonRepo.nullifyAddonReference(addonId)
        addonRepo.delete(existing)
    }

    @Transactional
    fun reorder(
        serviceId: Long,
        request: ReorderAddonsRequest,
        userEmail: String,
    ): List<ServiceAddonResponse> {
        val service = requireService(serviceId)
        businessService.requireRole(service.business!!.id!!, userEmail, BusinessRole.MANAGER)

        val existing = addonRepo.findAllByServiceIdOrderByPositionAsc(serviceId)
        val existingIds = existing.map { it.id!! }.toSet()
        val incomingIds = request.orderedIds.toSet()
        require(existingIds == incomingIds) {
            "orderedIds must contain exactly the existing addon ids for service $serviceId"
        }

        val byId = existing.associateBy { it.id!! }
        request.orderedIds.forEachIndexed { idx, id -> byId.getValue(id).position = idx }
        return request.orderedIds.map { byId.getValue(it).toResponse() }
    }

    private fun requireService(id: Long): ServiceOffered =
        serviceRepo.findById(id).orElseThrow { EntityNotFoundException("Service not found: $id") }

    private fun normalizeQuantity(
        quantifiable: Boolean,
        defaultQuantity: Int,
        maxQuantity: Int?,
    ): Pair<Int, Int?> =
        if (!quantifiable) Pair(1, 1) else Pair(defaultQuantity, maxQuantity)

    private fun ServiceAddon.toResponse() = ServiceAddonResponse(
        id = id!!,
        serviceId = service!!.id!!,
        title = title!!,
        description = description,
        coverImageUrl = coverImageUrl,
        coverImagePublicId = coverImagePublicId,
        priceDelta = priceDelta,
        priceUnit = priceUnit,
        defaultSelected = defaultSelected,
        quantifiable = quantifiable,
        defaultQuantity = defaultQuantity,
        maxQuantity = maxQuantity,
        position = position,
    )
}
