package com.mudhut.nudge.businesses.services

import com.mudhut.nudge.businesses.entities.BusinessPhoneNumber
import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.repositories.BusinessPhoneNumberRepository
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.utils.exceptions.BusinessNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BusinessPhoneNumberService(
    private val businessPhoneNumberRepository: BusinessPhoneNumberRepository,
    private val businessRepository: BusinessRepository,
    private val businessService: BusinessService
) {

    @Transactional
    fun addPhoneNumber(businessId: Long, phoneNumber: String, userEmail: String): BusinessPhoneNumber {
        val business = businessRepository.findById(businessId)
            .orElseThrow { BusinessNotFoundException("Business not found with id: $businessId") }

        businessService.requireRole(businessId, userEmail, BusinessRole.ADMIN)

        if (businessPhoneNumberRepository.countByBusinessId(businessId) >= 5) {
            throw IllegalArgumentException("A business can have at most 5 phone numbers")
        }

        if (businessPhoneNumberRepository.existsByBusinessIdAndPhoneNumber(businessId, phoneNumber)) {
            throw IllegalArgumentException("Phone number '$phoneNumber' already exists for this business")
        }

        val entity = BusinessPhoneNumber().apply {
            this.phoneNumber = phoneNumber
            this.business = business
        }

        return businessPhoneNumberRepository.save(entity)
    }

    @Transactional
    fun removePhoneNumber(businessId: Long, phoneNumberId: Long, userEmail: String) {
        val phoneNumber = businessPhoneNumberRepository.findById(phoneNumberId)
            .orElseThrow { IllegalArgumentException("Phone number not found with id: $phoneNumberId") }

        if (phoneNumber.business?.id != businessId) {
            throw IllegalArgumentException("Phone number does not belong to this business")
        }

        businessService.requireRole(businessId, userEmail, BusinessRole.ADMIN)

        businessPhoneNumberRepository.delete(phoneNumber)
    }
}
