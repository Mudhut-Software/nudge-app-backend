package com.mudhut.nudge.packagesoffered.services

import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.services.BusinessService
import com.mudhut.nudge.packagesoffered.entities.PackageOffered
import com.mudhut.nudge.packagesoffered.entities.PackageOfferedItem
import com.mudhut.nudge.packagesoffered.entities.PackageOfferedStatus
import com.mudhut.nudge.packagesoffered.models.CreatePackageOfferedRequest
import com.mudhut.nudge.packagesoffered.models.PackageOfferedItemResponse
import com.mudhut.nudge.packagesoffered.models.PackageOfferedResponse
import com.mudhut.nudge.packagesoffered.models.ServiceSummary
import com.mudhut.nudge.packagesoffered.repositories.PackageOfferedItemRepository
import com.mudhut.nudge.packagesoffered.repositories.PackageOfferedRepository
import com.mudhut.nudge.servicesoffered.entities.ServiceOffered
import com.mudhut.nudge.servicesoffered.models.MediaResponse
import com.mudhut.nudge.servicesoffered.repositories.ServiceOfferedRepository
import com.mudhut.nudge.utils.exceptions.BusinessNotFoundException
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class PackagesOfferedService(
    private val packageRepository: PackageOfferedRepository,
    private val packageItemRepository: PackageOfferedItemRepository,
    private val serviceRepository: ServiceOfferedRepository,
    private val businessService: BusinessService,
) {

    @Transactional
    fun createPackage(
        businessId: Long,
        userEmail: String,
        request: CreatePackageOfferedRequest,
    ): PackageOfferedResponse {
        businessService.requireRole(businessId, userEmail, BusinessRole.MANAGER)
        val business = businessService.findBusinessEntity(businessId)

        val services = serviceRepository.findAllById(request.serviceIds)

        val pkg = PackageOffered(
            business = business,
            title = request.title,
            priceAmount = request.priceAmount,
            priceCurrency = request.priceCurrency,
            tag = request.tag,
            validFrom = request.validFrom,
            validUntil = request.validUntil,
        )

        services.forEachIndexed { index, service ->
            pkg.items.add(
                PackageOfferedItem(
                    packageOffered = pkg,
                    service = service,
                    position = index,
                )
            )
        }

        val saved = packageRepository.save(pkg)
        return toResponse(saved)
    }

    private fun isCurrentlyActive(
        status: PackageOfferedStatus,
        validFrom: LocalDate?,
        validUntil: LocalDate?,
    ): Boolean {
        if (status != PackageOfferedStatus.ACTIVE) return false
        val today = LocalDate.now()
        val afterStart = validFrom == null || !today.isBefore(validFrom)
        val beforeEnd = validUntil == null || !today.isAfter(validUntil)
        return afterStart && beforeEnd
    }

    private fun toResponse(pkg: PackageOffered): PackageOfferedResponse {
        return PackageOfferedResponse(
            id = pkg.id!!,
            businessId = pkg.business!!.id!!,
            title = pkg.title!!,
            items = pkg.items
                .sortedBy { it.position }
                .map {
                    PackageOfferedItemResponse(
                        service = serviceSummary(it.service!!),
                        position = it.position,
                    )
                },
            priceAmount = pkg.priceAmount!!,
            priceCurrency = pkg.priceCurrency!!,
            tag = pkg.tag,
            validFrom = pkg.validFrom,
            validUntil = pkg.validUntil,
            status = pkg.status,
            isCurrentlyActive = isCurrentlyActive(pkg.status, pkg.validFrom, pkg.validUntil),
            createdAt = pkg.createdAt!!,
            updatedAt = pkg.updatedAt!!,
        )
    }

    private fun serviceSummary(s: ServiceOffered): ServiceSummary {
        return ServiceSummary(
            id = s.id!!,
            title = s.title!!,
            priceMode = s.priceMode!!,
            priceAmount = s.priceAmount,
            priceCurrency = s.priceCurrency,
            priceUnit = s.priceUnit,
            coverImage = MediaResponse(
                url = s.coverImageUrl!!,
                publicId = s.coverImagePublicId!!,
            ),
        )
    }
}
