package com.mudhut.nudge.discovery.services

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.discovery.entities.BusinessFavorite
import com.mudhut.nudge.discovery.models.PublicBusinessSummary
import com.mudhut.nudge.discovery.repositories.BusinessFavoriteRepository
import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedStatus
import com.mudhut.nudge.servicesoffered.repositories.ServiceOfferedRepository
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.utils.exceptions.BusinessNotFoundException
import com.mudhut.nudge.utils.exceptions.UserNotFoundException
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class FavoriteService(
    private val favoriteRepo: BusinessFavoriteRepository,
    private val businessRepo: BusinessRepository,
    private val userRepo: UserRepository,
    private val serviceRepo: ServiceOfferedRepository,
) {
    @Transactional
    fun addFavorite(email: String, businessId: Long) {
        val user = requireUser(email)
        val business = businessRepo.findById(businessId)
            .orElseThrow { BusinessNotFoundException("Business not found") }
        if (!favoriteRepo.existsByUserIdAndBusinessId(user.id!!, businessId)) {
            favoriteRepo.save(BusinessFavorite(user = user, business = business))
        }
    }

    @Transactional
    fun removeFavorite(email: String, businessId: Long) {
        val user = requireUser(email)
        favoriteRepo.deleteByUserIdAndBusinessId(user.id!!, businessId)
    }

    fun listFavorites(email: String): List<PublicBusinessSummary> {
        val user = requireUser(email)
        return favoriteRepo.findFavoritedBusinesses(user.id!!).map { it.toSummary() }
    }

    private fun requireUser(email: String): User =
        userRepo.findByEmail(email).orElseThrow { UserNotFoundException("User not found") }

    private fun Business.toSummary(): PublicBusinessSummary {
        val firstActive = serviceRepo
            .findFirstByBusinessIdAndStatusOrderByCreatedAtAsc(id!!, ServiceOfferedStatus.ACTIVE)
        return PublicBusinessSummary(
            id = id!!,
            name = name!!,
            categoryId = category!!.id!!,
            categoryName = category!!.name!!,
            address = address,
            coverImageUrl = coverImageUrl ?: firstActive?.coverImageUrl,
            serviceCount = serviceRepo.countByBusinessIdAndStatus(id!!, ServiceOfferedStatus.ACTIVE).toInt(),
            distanceKm = null,
        )
    }
}
