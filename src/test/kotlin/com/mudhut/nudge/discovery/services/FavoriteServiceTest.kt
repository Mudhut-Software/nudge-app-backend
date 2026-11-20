package com.mudhut.nudge.discovery.services

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.entities.BusinessCategory
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.discovery.repositories.BusinessFavoriteRepository
import com.mudhut.nudge.servicesoffered.entities.OptionGroup
import com.mudhut.nudge.servicesoffered.entities.ServiceOffered
import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedStatus
import com.mudhut.nudge.servicesoffered.repositories.ServiceOfferedRepository
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.entities.UserRole
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.utils.exceptions.BusinessNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional

class FavoriteServiceTest {
    private val favoriteRepo: BusinessFavoriteRepository = mock()
    private val businessRepo: BusinessRepository = mock()
    private val userRepo: UserRepository = mock()
    private val serviceRepo: ServiceOfferedRepository = mock()
    private val sut = FavoriteService(favoriteRepo, businessRepo, userRepo, serviceRepo)

    private val user = User(
        id = 1L, username = "Alice", email = "a@e.com",
        phoneNumber = null, password = "x", role = UserRole.BASIC_USER, isActive = true,
    )

    private fun business(id: Long = 10L) =
        Business(id = id, name = "Biz", category = BusinessCategory(id = 2L, name = "Catering"))

    @Test
    fun `add saves when not already favorited`() {
        whenever(userRepo.findByEmail("a@e.com")).thenReturn(Optional.of(user))
        whenever(businessRepo.findById(10L)).thenReturn(Optional.of(business()))
        whenever(favoriteRepo.existsByUserIdAndBusinessId(1L, 10L)).thenReturn(false)

        sut.addFavorite("a@e.com", 10L)

        verify(favoriteRepo).save(any())
    }

    @Test
    fun `add is idempotent when already favorited`() {
        whenever(userRepo.findByEmail("a@e.com")).thenReturn(Optional.of(user))
        whenever(businessRepo.findById(10L)).thenReturn(Optional.of(business()))
        whenever(favoriteRepo.existsByUserIdAndBusinessId(1L, 10L)).thenReturn(true)

        sut.addFavorite("a@e.com", 10L)

        verify(favoriteRepo, never()).save(any())
    }

    @Test
    fun `add throws 404 when business missing`() {
        whenever(userRepo.findByEmail("a@e.com")).thenReturn(Optional.of(user))
        whenever(businessRepo.findById(10L)).thenReturn(Optional.empty())

        assertThatThrownBy { sut.addFavorite("a@e.com", 10L) }
            .isInstanceOf(BusinessNotFoundException::class.java)
    }

    @Test
    fun `remove delegates to repository`() {
        whenever(userRepo.findByEmail("a@e.com")).thenReturn(Optional.of(user))

        sut.removeFavorite("a@e.com", 10L)

        verify(favoriteRepo).deleteByUserIdAndBusinessId(1L, 10L)
    }

    @Test
    fun `list maps favorited businesses to summaries`() {
        whenever(userRepo.findByEmail("a@e.com")).thenReturn(Optional.of(user))
        whenever(favoriteRepo.findFavoritedBusinesses(1L)).thenReturn(listOf(business(10L)))
        whenever(serviceRepo.findAllByBusinessIdAndStatusAndSoldOnItsOwnTrueOrderByCreatedAtAsc(10L, ServiceOfferedStatus.ACTIVE))
            .thenReturn(listOf(item(1L), item(2L)))

        val result = sut.listFavorites("a@e.com")

        assertThat(result).hasSize(1)
        assertThat(result[0].id).isEqualTo(10L)
        assertThat(result[0].serviceCount).isEqualTo(2)
        assertThat(result[0].categoryName).isEqualTo("Catering")
    }

    private fun item(id: Long, cover: String = "https://cdn/$id.jpg") =
        ServiceOffered(id = id, title = "Item $id", coverImageUrl = cover, status = ServiceOfferedStatus.ACTIVE)

    private fun unbookablePackage(id: Long): ServiceOffered {
        val pkg = item(id)
        pkg.groups.add(OptionGroup(id = id, item = pkg, name = "Flavours", required = true))
        return pkg
    }

    @Test
    fun `serviceCount excludes unbookable packages`() {
        whenever(userRepo.findByEmail("a@e.com")).thenReturn(Optional.of(user))
        whenever(favoriteRepo.findFavoritedBusinesses(1L)).thenReturn(listOf(business(10L)))
        whenever(serviceRepo.findAllByBusinessIdAndStatusAndSoldOnItsOwnTrueOrderByCreatedAtAsc(10L, ServiceOfferedStatus.ACTIVE))
            .thenReturn(listOf(item(1L), unbookablePackage(2L)))

        assertThat(sut.listFavorites("a@e.com")[0].serviceCount).isEqualTo(1)
    }

    @Test
    fun `cover fallback skips an unbookable package`() {
        whenever(userRepo.findByEmail("a@e.com")).thenReturn(Optional.of(user))
        whenever(favoriteRepo.findFavoritedBusinesses(1L)).thenReturn(listOf(business(10L)))
        whenever(serviceRepo.findAllByBusinessIdAndStatusAndSoldOnItsOwnTrueOrderByCreatedAtAsc(10L, ServiceOfferedStatus.ACTIVE))
            .thenReturn(listOf(unbookablePackage(2L), item(1L, "https://cdn/ok.jpg")))

        assertThat(sut.listFavorites("a@e.com")[0].coverImageUrl).isEqualTo("https://cdn/ok.jpg")
    }
}
