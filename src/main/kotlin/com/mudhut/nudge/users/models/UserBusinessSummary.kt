package com.mudhut.nudge.users.models

import com.mudhut.nudge.businesses.entities.BusinessMember
import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.entities.BusinessStatus

data class UserBusinessSummary(
    val id: Long,
    val status: BusinessStatus,
    val role: BusinessRole
) {
    companion object {
        fun from(member: BusinessMember): UserBusinessSummary {
            val business = member.business!!
            return UserBusinessSummary(
                id = business.id!!,
                status = business.status,
                role = member.role!!
            )
        }
    }
}
