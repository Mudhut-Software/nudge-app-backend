package com.mudhut.nudge.services.services

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.services.BusinessService
import com.mudhut.nudge.services.entities.PriceMode
import com.mudhut.nudge.services.entities.Service as ServiceEntity
import com.mudhut.nudge.services.entities.ServiceStatus
import com.mudhut.nudge.services.models.CreateServiceRequest
import com.mudhut.nudge.services.models.MediaInput
import com.mudhut.nudge.services.repositories.ServiceRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.any
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import java.math.BigDecimal
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class BusinessOfferingServiceTest {

    @Mock
    private lateinit var serviceRepository: ServiceRepository

    @Mock
    private lateinit var businessService: BusinessService

    @InjectMocks
    private lateinit var offeringService: BusinessOfferingService

    private fun businessFixture(id: Long = 1L) =
        Business(id = id, name = "Test Biz")

    private fun fixedRequest(
        title: String = "Sofa cleaning",
        amount: BigDecimal = BigDecimal("50000.00"),
        currency: String = "UGX"
    ) = CreateServiceRequest(
        title = title,
        description = null,
        coverImage = MediaInput(
            url = "https://res.cloudinary.com/x/image/upload/nudge/services/cover.jpg",
            publicId = "nudge/services/cover"
        ),
        priceMode = PriceMode.FIXED,
        priceAmount = amount,
        priceCurrency = currency,
        priceUnit = null,
        galleryImages = emptyList()
    )

    @Test
    fun `createService persists a FIXED-priced service`() {
        val business = businessFixture()
        `when`(businessService.findBusinessEntity(1L)).thenReturn(business)
        `when`(serviceRepository.save(any<ServiceEntity>())).thenAnswer { invocation ->
            val entity = invocation.arguments[0] as ServiceEntity
            entity.id = 99L
            entity.createdAt = LocalDateTime.now()
            entity.updatedAt = LocalDateTime.now()
            entity
        }

        val response = offeringService.createService(
            businessId = 1L,
            userEmail = "owner@test.com",
            request = fixedRequest()
        )

        verify(businessService).requireRole(1L, "owner@test.com", BusinessRole.MANAGER)
        assertEquals(99L, response.id)
        assertEquals(1L, response.businessId)
        assertEquals("Sofa cleaning", response.title)
        assertEquals(PriceMode.FIXED, response.priceMode)
        assertEquals(BigDecimal("50000.00"), response.priceAmount)
        assertEquals("UGX", response.priceCurrency)
        assertNull(response.priceUnit)
        assertEquals(ServiceStatus.ACTIVE, response.status)
        assertEquals("nudge/services/cover", response.coverImage.publicId)
        assertTrue(response.galleryImages.isEmpty())
    }

    private fun perUnitRequest() = fixedRequest().copy(
        priceMode = PriceMode.PER_UNIT,
        priceUnit = "plate"
    )

    private fun quoteRequest() = fixedRequest().copy(
        priceMode = PriceMode.QUOTE,
        priceAmount = null,
        priceCurrency = null,
        priceUnit = null
    )

    @Test
    fun `createService persists a PER_UNIT-priced service`() {
        val business = businessFixture()
        `when`(businessService.findBusinessEntity(1L)).thenReturn(business)
        `when`(serviceRepository.save(any<ServiceEntity>())).thenAnswer { invocation ->
            (invocation.arguments[0] as ServiceEntity).apply {
                id = 100L
                createdAt = LocalDateTime.now()
                updatedAt = LocalDateTime.now()
            }
        }

        val response = offeringService.createService(1L, "owner@test.com", perUnitRequest())

        assertEquals(PriceMode.PER_UNIT, response.priceMode)
        assertEquals("plate", response.priceUnit)
    }

    @Test
    fun `createService persists a QUOTE service with no price fields`() {
        val business = businessFixture()
        `when`(businessService.findBusinessEntity(1L)).thenReturn(business)
        `when`(serviceRepository.save(any<ServiceEntity>())).thenAnswer { invocation ->
            (invocation.arguments[0] as ServiceEntity).apply {
                id = 101L
                createdAt = LocalDateTime.now()
                updatedAt = LocalDateTime.now()
            }
        }

        val response = offeringService.createService(1L, "owner@test.com", quoteRequest())

        assertEquals(PriceMode.QUOTE, response.priceMode)
        assertNull(response.priceAmount)
        assertNull(response.priceCurrency)
        assertNull(response.priceUnit)
    }

    @Test
    fun `createService rejects FIXED without amount`() {
        val request = fixedRequest().copy(priceAmount = null)
        assertThrows(IllegalArgumentException::class.java) {
            offeringService.createService(1L, "owner@test.com", request)
        }
    }

    @Test
    fun `createService rejects FIXED with a unit`() {
        val request = fixedRequest().copy(priceUnit = "plate")
        assertThrows(IllegalArgumentException::class.java) {
            offeringService.createService(1L, "owner@test.com", request)
        }
    }

    @Test
    fun `createService rejects PER_UNIT without unit`() {
        val request = perUnitRequest().copy(priceUnit = null)
        assertThrows(IllegalArgumentException::class.java) {
            offeringService.createService(1L, "owner@test.com", request)
        }
    }

    @Test
    fun `createService rejects QUOTE with an amount`() {
        val request = quoteRequest().copy(priceAmount = BigDecimal("100.00"))
        assertThrows(IllegalArgumentException::class.java) {
            offeringService.createService(1L, "owner@test.com", request)
        }
    }

    @Test
    fun `createService rejects non-positive amount`() {
        val request = fixedRequest().copy(priceAmount = BigDecimal.ZERO)
        assertThrows(IllegalArgumentException::class.java) {
            offeringService.createService(1L, "owner@test.com", request)
        }
    }

    @Test
    fun `createService rejects malformed currency`() {
        val request = fixedRequest().copy(priceCurrency = "ugx")
        assertThrows(IllegalArgumentException::class.java) {
            offeringService.createService(1L, "owner@test.com", request)
        }
    }
}
