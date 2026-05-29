package com.mudhut.nudge.businesses.publicapi.services

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.publicapi.models.BusinessSort
import com.mudhut.nudge.businesses.publicapi.models.PublicBusinessDetail
import com.mudhut.nudge.businesses.publicapi.models.PublicBusinessSummary
import com.mudhut.nudge.businesses.publicapi.models.PublicPackageSummary
import com.mudhut.nudge.businesses.publicapi.models.PublicServiceSummary
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.packagesoffered.entities.PackageOffered
import com.mudhut.nudge.packagesoffered.repositories.PackageOfferedRepository
import com.mudhut.nudge.servicesoffered.entities.ServiceOffered
import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedStatus
import com.mudhut.nudge.servicesoffered.repositories.ServiceOfferedRepository
import com.mudhut.nudge.utils.exceptions.BusinessNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class PublicBrowseService(
    private val businessRepository: BusinessRepository,
    private val serviceRepository: ServiceOfferedRepository,
    private val packageRepository: PackageOfferedRepository,
) {

    fun list(
        categoryId: Long?,
        sort: BusinessSort,
        lat: Double?,
        lng: Double?,
        pageable: Pageable,
    ): Page<PublicBusinessSummary> = when (sort) {
        BusinessSort.NEWEST -> businessRepository
            .findPublicQualifiedNewest(categoryId, pageable)
            .map { toSummary(it) }

        BusinessSort.POPULAR -> businessRepository
            .findPublicQualifiedPopular(categoryId, pageable)
            .map { toSummary(it) }

        BusinessSort.NEAREST -> nearestPage(categoryId, lat, lng, pageable)
    }

    private fun nearestPage(
        categoryId: Long?,
        lat: Double?,
        lng: Double?,
        pageable: Pageable,
    ): Page<PublicBusinessSummary> {
        require(lat != null && lng != null) { "sort=nearest requires lat and lng" }

        val page = businessRepository.findPublicQualifiedNearest(categoryId, lat, lng, pageable)
        if (page.isEmpty) return PageImpl(emptyList(), pageable, page.totalElements)

        val distancesById = page.content.associate { it.id to it.distanceKm }
        val byId = businessRepository.findAllById(distancesById.keys).associateBy { it.id!! }

        val summaries = page.content.mapNotNull { row ->
            byId[row.id]?.let { biz -> toSummary(biz, distancesById[row.id]) }
        }

        return PageImpl(summaries, pageable, page.totalElements)
    }

    fun detail(id: Long): PublicBusinessDetail {
        val biz = businessRepository.findById(id)
            .orElseThrow { BusinessNotFoundException("Business not found") }
        val today = LocalDate.now()

        val activeServices = serviceRepository
            .findTop20ByBusinessIdAndStatusOrderByCreatedAtDesc(biz.id!!, ServiceOfferedStatus.ACTIVE)
        val currentlyActivePackages = packageRepository
            .findTop20CurrentlyActiveByBusinessIdOrderByCreatedAtDesc(biz.id!!, today)

        if (activeServices.isEmpty() && currentlyActivePackages.isEmpty()) {
            throw BusinessNotFoundException("Business not found")
        }

        return PublicBusinessDetail(
            id = biz.id!!,
            name = biz.name!!,
            description = biz.description,
            logoUrl = biz.logoUrl,
            categoryId = biz.category!!.id!!,
            categoryName = biz.category!!.name!!,
            address = biz.address,
            phoneNumbers = biz.phoneNumbers.mapNotNull { it.phoneNumber },
            email = biz.email,
            serviceAreas = biz.serviceAreas.toList(),
            coverImageUrl = deriveCover(biz, activeServices.firstOrNull()),
            services = activeServices.map { toServiceSummary(it) },
            packages = currentlyActivePackages.map { toPackageSummary(it) },
        )
    }

    private fun toSummary(biz: Business, distanceKm: Double? = null): PublicBusinessSummary {
        val firstActive = serviceRepository
            .findFirstByBusinessIdAndStatusOrderByCreatedAtAsc(biz.id!!, ServiceOfferedStatus.ACTIVE)
        return PublicBusinessSummary(
            id = biz.id!!,
            name = biz.name!!,
            categoryId = biz.category!!.id!!,
            categoryName = biz.category!!.name!!,
            address = biz.address,
            coverImageUrl = deriveCover(biz, firstActive),
            serviceCount = serviceRepository
                .countByBusinessIdAndStatus(biz.id!!, ServiceOfferedStatus.ACTIVE).toInt(),
            packageCount = packageRepository
                .countCurrentlyActiveByBusinessId(biz.id!!, LocalDate.now()).toInt(),
            distanceKm = distanceKm,
        )
    }

    private fun deriveCover(biz: Business, firstActiveService: ServiceOffered?): String? {
        return biz.coverImageUrl ?: firstActiveService?.coverImageUrl
    }

    private fun toServiceSummary(s: ServiceOffered): PublicServiceSummary = PublicServiceSummary(
        id = s.id!!,
        title = s.title!!,
        description = s.description,
        priceMode = s.priceMode!!,
        priceAmount = s.priceAmount,
        priceCurrency = s.priceCurrency,
        priceUnit = s.priceUnit,
        coverImageUrl = s.coverImageUrl!!,
        galleryImageUrls = s.galleryImages
            .sortedBy { it.position }
            .mapNotNull { it.url },
        addons = s.addons.sortedBy { it.position }.map { a ->
            com.mudhut.nudge.servicesoffered.models.PublicServiceAddon(
                id = a.id!!,
                title = a.title!!,
                description = a.description,
                coverImageUrl = a.coverImageUrl,
                priceDelta = a.priceDelta,
                priceUnit = a.priceUnit,
                defaultSelected = a.defaultSelected,
                quantifiable = a.quantifiable,
                defaultQuantity = a.defaultQuantity,
                maxQuantity = a.maxQuantity,
                position = a.position,
            )
        },
    )

    private fun toPackageSummary(p: PackageOffered): PublicPackageSummary = PublicPackageSummary(
        id = p.id!!,
        title = p.title!!,
        items = p.items
            .filter { it.service?.status == ServiceOfferedStatus.ACTIVE }
            .sortedBy { it.position }
            .map { toServiceSummary(it.service!!) },
        priceAmount = p.priceAmount!!,
        priceCurrency = p.priceCurrency!!,
        tag = p.tag,
        validFrom = p.validFrom,
        validUntil = p.validUntil,
    )
}
