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
import com.mudhut.nudge.packagesoffered.models.UpdatePackageOfferedRequest
import com.mudhut.nudge.packagesoffered.repositories.PackageOfferedItemRepository
import com.mudhut.nudge.packagesoffered.repositories.PackageOfferedRepository
import com.mudhut.nudge.servicesoffered.entities.ServiceOffered
import com.mudhut.nudge.servicesoffered.models.MediaResponse
import com.mudhut.nudge.servicesoffered.repositories.ServiceOfferedRepository
import com.mudhut.nudge.utils.exceptions.BusinessNotFoundException
import jakarta.transaction.Transactional
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
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
        validateServiceIdsAndWindow(
            businessId = businessId,
            serviceIds = request.serviceIds,
            services = services,
            validFrom = request.validFrom,
            validUntil = request.validUntil,
        )

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

    @Transactional
    fun updatePackage(
        packageId: Long,
        userEmail: String,
        request: UpdatePackageOfferedRequest,
    ): PackageOfferedResponse {
        val pkg = packageRepository.findById(packageId)
            .orElseThrow { BusinessNotFoundException("Package not found with id: $packageId") }
        businessService.requireRole(pkg.business!!.id!!, userEmail, BusinessRole.MANAGER)

        // Window cross-field check on the resulting state
        val newValidFrom = request.validFrom ?: pkg.validFrom
        val newValidUntil = request.validUntil ?: pkg.validUntil
        if (newValidFrom != null && newValidUntil != null) {
            require(!newValidFrom.isAfter(newValidUntil)) {
                "validFrom must be on or before validUntil"
            }
        }

        request.title?.let { pkg.title = it }
        request.priceAmount?.let { pkg.priceAmount = it }
        request.priceCurrency?.let { pkg.priceCurrency = it }
        request.tag?.let { pkg.tag = it }   // null means no-change (see KDoc)
        request.validFrom?.let { pkg.validFrom = it }
        request.validUntil?.let { pkg.validUntil = it }
        request.status?.let { pkg.status = it }

        request.serviceIds?.let { incoming ->
            val services = serviceRepository.findAllById(incoming)
            validateServiceIdsAndWindow(
                businessId = pkg.business!!.id!!,
                serviceIds = incoming,
                services = services,
                validFrom = newValidFrom,
                validUntil = newValidUntil,
            )
            pkg.items.clear()
            services.forEachIndexed { idx, s ->
                pkg.items.add(
                    PackageOfferedItem(
                        packageOffered = pkg,
                        service = s,
                        position = idx,
                    )
                )
            }
        }

        val saved = packageRepository.save(pkg)
        return toResponse(saved)
    }

    fun getPackage(packageId: Long, userEmail: String): PackageOfferedResponse {
        val pkg = packageRepository.findById(packageId)
            .orElseThrow { BusinessNotFoundException("Package not found with id: $packageId") }
        businessService.requireRole(pkg.business!!.id!!, userEmail, BusinessRole.STAFF)
        return toResponse(pkg)
    }

    fun listPackages(
        businessId: Long,
        userEmail: String,
        pageable: Pageable,
        statusFilter: PackageOfferedStatus?,
    ): Page<PackageOfferedResponse> {
        businessService.requireRole(businessId, userEmail, BusinessRole.STAFF)
        val page = if (statusFilter == null) {
            packageRepository.findAllByBusinessId(businessId, pageable)
        } else {
            packageRepository.findAllByBusinessIdAndStatus(businessId, statusFilter, pageable)
        }
        return page.map { toResponse(it) }
    }

    private fun validateServiceIdsAndWindow(
        businessId: Long,
        serviceIds: List<Long>,
        services: List<ServiceOffered>,
        validFrom: LocalDate?,
        validUntil: LocalDate?,
    ) {
        require(serviceIds.isNotEmpty()) { "At least one service is required" }
        require(serviceIds.size == serviceIds.toSet().size) {
            "Duplicate serviceIds are not allowed"
        }
        require(services.size == serviceIds.toSet().size) {
            "One or more serviceIds do not exist"
        }
        require(services.all { it.business?.id == businessId }) {
            "All services must belong to the same business as the package"
        }
        if (validFrom != null && validUntil != null) {
            require(!validFrom.isAfter(validUntil)) {
                "validFrom must be on or before validUntil"
            }
        }
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
