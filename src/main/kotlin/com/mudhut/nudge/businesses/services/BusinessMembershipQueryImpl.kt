package com.mudhut.nudge.businesses.services

import com.mudhut.nudge.businesses.repositories.BusinessMemberRepository
import com.mudhut.nudge.users.spi.UserBusinessMembershipQuery
import com.mudhut.nudge.users.spi.UserBusinessSummary
import org.springframework.stereotype.Service

/**
 * `businesses`-side implementation of the `users` SPI. Maps active BusinessMember rows into the
 * users-owned summary (enum names as strings). This is the natural businesses -> users direction.
 */
@Service
class BusinessMembershipQueryImpl(
    private val businessMemberRepository: BusinessMemberRepository,
) : UserBusinessMembershipQuery {
    override fun findActiveMembershipsFor(userId: Long): List<UserBusinessSummary> =
        businessMemberRepository.findByUserIdAndIsActiveTrue(userId).map { member ->
            val business = member.business!!
            UserBusinessSummary(
                id = business.id!!,
                status = business.status.name,
                role = member.role!!.name,
            )
        }
}
