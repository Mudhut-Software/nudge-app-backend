package com.mudhut.nudge.businesses.publicapi.services

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.publicapi.models.ExploreLane
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
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.time.LocalDate

private const val LANE_SIZE = 10

@Service
class PublicBrowseService(
    private val businessRepository: BusinessRepository,
    private val serviceRepository: ServiceOfferedRepository,
    private val packageRepository: PackageOfferedRepository,
) {

    fun lanes(): List<ExploreLane> {
        val qualified = businessRepository.findAllPublicQualified()
        if (qualified.isEmpty()) return emptyList()

        return qualified
            .groupBy { it.category!!.id!! to it.category!!.name!! }
            .map { (key, list) ->
                val (categoryId, categoryName) = key
                ExploreLane(
                    categoryId = categoryId,
                    categoryName = categoryName,
                    businesses = list.take(LANE_SIZE).map { toSummary(it) },
                )
            }
            .sortedBy { it.categoryName }
    }

    fun byCategory(categoryId: Long, pageable: Pageable): Page<PublicBusinessSummary> {
        return businessRepository
            .findPublicByCategory(categoryId, pageable)
            .map { toSummary(it) }
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

    private fun toSummary(biz: Business): PublicBusinessSummary {
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
