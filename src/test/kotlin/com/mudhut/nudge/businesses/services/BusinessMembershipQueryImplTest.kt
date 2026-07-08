package com.mudhut.nudge.businesses.services

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.entities.BusinessMember
import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.entities.BusinessStatus
import com.mudhut.nudge.businesses.repositories.BusinessMemberRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class BusinessMembershipQueryImplTest {

    private val repo: BusinessMemberRepository = mock()
    private val sut = BusinessMembershipQueryImpl(repo)

    @Test
    fun `maps active memberships to summaries with enum names as strings`() {
        val biz = Business(id = 3L, name = "Biz", status = BusinessStatus.ACTIVE)
        val member = BusinessMember(id = 1L, user = null, business = biz, role = BusinessRole.OWNER)
        whenever(repo.findByUserIdAndIsActiveTrue(9L)).thenReturn(listOf(member))

        val result = sut.findActiveMembershipsFor(9L)

        assertThat(result).hasSize(1)
        assertThat(result[0].id).isEqualTo(3L)
        assertThat(result[0].status).isEqualTo("ACTIVE")
        assertThat(result[0].role).isEqualTo("OWNER")
    }

    @Test
    fun `returns empty when no memberships`() {
        whenever(repo.findByUserIdAndIsActiveTrue(9L)).thenReturn(emptyList())
        assertThat(sut.findActiveMembershipsFor(9L)).isEmpty()
    }
}
