package com.mudhut.nudge.businesses.services

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.entities.BusinessMember
import com.mudhut.nudge.businesses.entities.BusinessPhoneNumber
import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.models.BusinessResponse
import com.mudhut.nudge.businesses.models.CreateBusinessRequest
import com.mudhut.nudge.businesses.models.UpdateBusinessRequest
import com.mudhut.nudge.businesses.repositories.BusinessCategoryRepository
import com.mudhut.nudge.businesses.repositories.BusinessMemberRepository
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.utils.exceptions.BusinessAccessDeniedException
import com.mudhut.nudge.utils.exceptions.BusinessNotFoundException
import jakarta.persistence.EntityNotFoundException
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class BusinessService(
    private val businessRepository: BusinessRepository,
    private val businessCategoryRepository: BusinessCategoryRepository,
    private val businessMemberRepository: BusinessMemberRepository,
    private val userRepository: UserRepository
) {

    @Transactional
    fun createBusiness(request: CreateBusinessRequest, ownerEmail: String): BusinessResponse {
        val owner = userRepository.findByEmail(ownerEmail)
            .orElseThrow { EntityNotFoundException("User not found with email: $ownerEmail") }

        val category = businessCategoryRepository.findById(request.categoryId!!)
            .orElseThrow { IllegalArgumentException("Category not found with id: ${request.categoryId}") }

        val areas = request.serviceAreas
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            ?: emptyList()

        if (areas.isEmpty()) {
            throw IllegalArgumentException("At least one service area is required")
        }

        val business = Business().apply {
            name = request.name
            description = request.description
            this.owner = owner
            this.category = category
            email = request.email
            logoUrl = request.logoUrl
            address = request.address
            latitude = request.latitude
            longitude = request.longitude
            serviceAreas = areas.toMutableList()
        }

        val savedBusiness = businessRepository.save(business)

        request.phoneNumbers?.let { numbers ->
            if (numbers.size > 5) {
                throw IllegalArgumentException("A business can have at most 5 phone numbers")
            }
            val phoneEntities = numbers.map { number ->
                BusinessPhoneNumber().apply {
                    phoneNumber = number
                    this.business = savedBusiness
                }
            }
            savedBusiness.phoneNumbers.addAll(phoneEntities)
            businessRepository.save(savedBusiness)
        }

        val ownerMembership = BusinessMember().apply {
            user = owner
            this.business = savedBusiness
            role = BusinessRole.OWNER
        }
        businessMemberRepository.save(ownerMembership)

        return toResponse(savedBusiness)
    }

    fun getBusinessById(id: Long): BusinessResponse {
        val business = businessRepository.findById(id)
            .orElseThrow { BusinessNotFoundException("Business not found with id: $id") }
        return toResponse(business)
    }

    @Transactional
    fun updateBusiness(id: Long, request: UpdateBusinessRequest, userEmail: String): BusinessResponse {
        val business = businessRepository.findById(id)
            .orElseThrow { BusinessNotFoundException("Business not found with id: $id") }

        requireRole(id, userEmail, BusinessRole.ADMIN)

        request.name?.let { business.name = it }
        request.description?.let { business.description = it }
        request.categoryId?.let { categoryId ->
            val category = businessCategoryRepository.findById(categoryId)
                .orElseThrow { IllegalArgumentException("Category not found with id: $categoryId") }
            business.category = category
        }
        request.phoneNumbers?.let { numbers ->
            if (numbers.size > 5) {
                throw IllegalArgumentException("A business can have at most 5 phone numbers")
            }
            business.phoneNumbers.clear()
            val phoneEntities = numbers.map { number ->
                BusinessPhoneNumber().apply {
                    phoneNumber = number
                    this.business = business
                }
            }
            business.phoneNumbers.addAll(phoneEntities)
        }
        request.email?.let { business.email = it }
        request.logoUrl?.let { business.logoUrl = it }
        request.address?.let { business.address = it }
        request.latitude?.let { business.latitude = it }
        request.longitude?.let { business.longitude = it }
        request.serviceAreas?.let { incoming ->
            val cleaned = incoming
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            if (cleaned.isEmpty()) {
                throw IllegalArgumentException("At least one service area is required")
            }
            business.serviceAreas.clear()
            business.serviceAreas.addAll(cleaned)
        }

        val saved = businessRepository.save(business)
        return toResponse(saved)
    }

    @Transactional
    fun deleteBusiness(id: Long, userEmail: String) {
        val business = businessRepository.findById(id)
            .orElseThrow { BusinessNotFoundException("Business not found with id: $id") }

        val user = userRepository.findByEmail(userEmail)
            .orElseThrow { EntityNotFoundException("User not found") }

        if (business.owner?.id != user.id) {
            throw BusinessAccessDeniedException("Only the owner can delete this business")
        }

        businessRepository.delete(business)
    }

    fun getMyBusinesses(ownerEmail: String): List<BusinessResponse> {
        val owner = userRepository.findByEmail(ownerEmail)
            .orElseThrow { EntityNotFoundException("User not found") }
        return businessRepository.findByOwnerId(owner.id!!)
            .map { toResponse(it) }
    }

    fun requireRole(businessId: Long, userEmail: String, minimumRole: BusinessRole) {
        val user = userRepository.findByEmail(userEmail)
            .orElseThrow { EntityNotFoundException("User not found") }

        val member = businessMemberRepository.findByBusinessIdAndUserId(businessId, user.id!!)
            .orElseThrow { BusinessAccessDeniedException("You are not a member of this business") }

        if (!member.isActive) {
            throw BusinessAccessDeniedException("Your membership is inactive")
        }

        val roleHierarchy = listOf(BusinessRole.OWNER, BusinessRole.ADMIN, BusinessRole.MANAGER, BusinessRole.STAFF)
        val userRoleIndex = roleHierarchy.indexOf(member.role)
        val requiredRoleIndex = roleHierarchy.indexOf(minimumRole)

        if (userRoleIndex > requiredRoleIndex) {
            throw BusinessAccessDeniedException("Insufficient role. Required: $minimumRole or higher")
        }
    }

    private fun toResponse(business: Business): BusinessResponse {
        return BusinessResponse(
            id = business.id!!,
            name = business.name!!,
            description = business.description,
            ownerId = business.owner!!.id!!,
            ownerEmail = business.owner!!.email!!,
            categoryId = business.category!!.id!!,
            categoryName = business.category!!.name!!,
            phoneNumbers = business.phoneNumbers.map { it.phoneNumber!! },
            email = business.email,
            logoUrl = business.logoUrl,
            address = business.address,
            latitude = business.latitude,
            longitude = business.longitude,
            serviceAreas = business.serviceAreas.toList(),
            status = business.status
        )
    }
}
