package com.mudhut.nudge.businesses.services

import com.mudhut.nudge.businesses.entities.BusinessMember
import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.models.BusinessMemberResponse
import com.mudhut.nudge.businesses.models.UpdateMemberRoleRequest
import com.mudhut.nudge.businesses.repositories.BusinessMemberRepository
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.utils.exceptions.BusinessAccessDeniedException
import com.mudhut.nudge.utils.models.GeneralRequestResponse
import jakarta.persistence.EntityNotFoundException
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class BusinessMemberService(
    private val businessMemberRepository: BusinessMemberRepository,
    private val userRepository: UserRepository,
    private val businessService: BusinessService
) {

    fun getMembers(businessId: Long, userEmail: String): List<BusinessMemberResponse> {
        businessService.requireRole(businessId, userEmail, BusinessRole.MANAGER)
        return businessMemberRepository.findByBusinessIdAndIsActiveTrue(businessId)
            .map { toResponse(it) }
    }

    @Transactional
    fun updateMemberRole(
        businessId: Long,
        memberId: Long,
        request: UpdateMemberRoleRequest,
        userEmail: String
    ): BusinessMemberResponse {
        businessService.requireRole(businessId, userEmail, BusinessRole.ADMIN)

        val actingUser = userRepository.findByEmail(userEmail)
            .orElseThrow { EntityNotFoundException("User not found") }

        val actingMember = businessMemberRepository.findByBusinessIdAndUserId(businessId, actingUser.id!!)
            .orElseThrow { BusinessAccessDeniedException("You are not a member of this business") }

        val targetMember = businessMemberRepository.findById(memberId)
            .orElseThrow { EntityNotFoundException("Member not found with id: $memberId") }

        if (targetMember.business?.id != businessId) {
            throw IllegalArgumentException("Member does not belong to this business")
        }

        if (targetMember.role == BusinessRole.OWNER) {
            throw BusinessAccessDeniedException("Cannot change the owner's role")
        }

        if (request.role == BusinessRole.OWNER) {
            throw BusinessAccessDeniedException("Cannot assign OWNER role")
        }

        if (actingMember.role == BusinessRole.ADMIN && targetMember.role == BusinessRole.ADMIN) {
            throw BusinessAccessDeniedException("An ADMIN cannot change another ADMIN's role")
        }

        targetMember.role = request.role
        val saved = businessMemberRepository.save(targetMember)
        return toResponse(saved)
    }

    @Transactional
    fun removeMember(businessId: Long, memberId: Long, userEmail: String): GeneralRequestResponse {
        businessService.requireRole(businessId, userEmail, BusinessRole.ADMIN)

        val actingUser = userRepository.findByEmail(userEmail)
            .orElseThrow { EntityNotFoundException("User not found") }

        val actingMember = businessMemberRepository.findByBusinessIdAndUserId(businessId, actingUser.id!!)
            .orElseThrow { BusinessAccessDeniedException("You are not a member of this business") }

        val targetMember = businessMemberRepository.findById(memberId)
            .orElseThrow { EntityNotFoundException("Member not found with id: $memberId") }

        if (targetMember.business?.id != businessId) {
            throw IllegalArgumentException("Member does not belong to this business")
        }

        if (targetMember.role == BusinessRole.OWNER) {
            throw BusinessAccessDeniedException("Cannot remove the owner")
        }

        if (actingMember.role == BusinessRole.ADMIN && targetMember.role == BusinessRole.ADMIN) {
            throw BusinessAccessDeniedException("An ADMIN cannot remove another ADMIN")
        }

        targetMember.isActive = false
        businessMemberRepository.save(targetMember)
        return GeneralRequestResponse("Member removed successfully")
    }

    @Transactional
    fun leaveBusiness(businessId: Long, userEmail: String): GeneralRequestResponse {
        val user = userRepository.findByEmail(userEmail)
            .orElseThrow { EntityNotFoundException("User not found") }

        val member = businessMemberRepository.findByBusinessIdAndUserId(businessId, user.id!!)
            .orElseThrow { EntityNotFoundException("You are not a member of this business") }

        if (member.role == BusinessRole.OWNER) {
            throw BusinessAccessDeniedException("Owner cannot leave the business. Transfer ownership or delete the business instead.")
        }

        member.isActive = false
        businessMemberRepository.save(member)
        return GeneralRequestResponse("Successfully left the business")
    }

    fun getUserMemberships(userEmail: String): List<BusinessMemberResponse> {
        val user = userRepository.findByEmail(userEmail)
            .orElseThrow { EntityNotFoundException("User not found") }
        return businessMemberRepository.findByUserIdAndIsActiveTrue(user.id!!)
            .map { toResponse(it) }
    }

    private fun toResponse(member: BusinessMember): BusinessMemberResponse {
        return BusinessMemberResponse(
            id = member.id!!,
            userId = member.user!!.id!!,
            userEmail = member.user!!.email!!,
            businessId = member.business!!.id!!,
            role = member.role!!,
            isActive = member.isActive,
            joinedAt = member.joinedAt
        )
    }
}
