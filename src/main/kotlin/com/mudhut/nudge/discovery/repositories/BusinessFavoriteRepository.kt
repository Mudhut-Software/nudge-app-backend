package com.mudhut.nudge.discovery.repositories

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.discovery.entities.BusinessFavorite
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface BusinessFavoriteRepository : JpaRepository<BusinessFavorite, Long> {

    fun existsByUserIdAndBusinessId(userId: Long, businessId: Long): Boolean

    fun deleteByUserIdAndBusinessId(userId: Long, businessId: Long)

    @Query(
        """
        SELECT f.business FROM BusinessFavorite f
        WHERE f.user.id = :userId
        ORDER BY f.createdAt DESC
        """
    )
    fun findFavoritedBusinesses(@Param("userId") userId: Long): List<Business>
}
