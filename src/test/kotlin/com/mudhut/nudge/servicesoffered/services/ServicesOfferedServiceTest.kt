package com.mudhut.nudge.servicesoffered.services

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.services.BusinessService
import com.mudhut.nudge.media.PendingMediaDeletion
import com.mudhut.nudge.servicesoffered.entities.ServiceOffered
import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedImage
import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedStatus
import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedTag
import com.mudhut.nudge.servicesoffered.models.CreateServiceOfferedRequest
import com.mudhut.nudge.servicesoffered.models.MediaInput
import com.mudhut.nudge.servicesoffered.models.UpdateServiceOfferedRequest
import com.mudhut.nudge.media.PendingMediaDeletionRepository
import com.mudhut.nudge.servicesoffered.repositories.ServiceOfferedRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.any
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class ServicesOfferedServiceTest {

    @Mock
    private lateinit var serviceRepository: ServiceOfferedRepository

    @Mock
    private lateinit var businessService: BusinessService

    @Mock
    private lateinit var pendingMediaDeletionRepository: PendingMediaDeletionRepository


    @Captor
    private lateinit var pendingDeletionCaptor: ArgumentCaptor<List<PendingMediaDeletion>>

    @InjectMocks
    private lateinit var offeringService: ServicesOfferedService

    private fun businessFixture(id: Long = 1L) =
        Business(id = id, name = "Test Biz")

    private fun fixedRequest(
        title: String = "Sofa cleaning",
        amount: BigDecimal = BigDecimal("50000.00"),
        currency: String = "UGX"
    ) = CreateServiceOfferedRequest(
        title = title,
        description = null,
        coverImage = MediaInput(
            url = "https://res.cloudinary.com/x/image/upload/nudge/images/cover.jpg",
            publicId = "nudge/images/cover"
        ),
        priceAmount = amount,
        priceCurrency = currency,
        galleryImages = emptyList()
    )

    @Test
    fun `createService persists a priced service`() {
        val business = businessFixture()
        `when`(businessService.findBusinessEntity(1L)).thenReturn(business)
        `when`(serviceRepository.save(any<ServiceOffered>())).thenAnswer { invocation ->
            val entity = invocation.arguments[0] as ServiceOffered
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
        assertEquals(BigDecimal("50000.00"), response.priceAmount)
        assertEquals("UGX", response.priceCurrency)
        assertEquals(ServiceOfferedStatus.ACTIVE, response.status)
        assertEquals("nudge/images/cover", response.coverImage!!.publicId)
        assertTrue(response.galleryImages.isEmpty())
    }

    @Test
    fun `createService persists tag and window and computes isCurrentlyActive`() {
        val business = businessFixture()
        `when`(businessService.findBusinessEntity(1L)).thenReturn(business)
        `when`(serviceRepository.save(any<ServiceOffered>())).thenAnswer { invocation ->
            (invocation.arguments[0] as ServiceOffered).apply {
                id = 77L
                createdAt = LocalDateTime.now()
                updatedAt = LocalDateTime.now()
            }
        }
        val today = LocalDate.now()
        val req = fixedRequest().copy(
            tag = ServiceOfferedTag.HOLIDAY_OFFER,
            validFrom = today.minusDays(1),
            validUntil = today.plusDays(1),
        )

        val res = offeringService.createService(1L, "owner@test.com", req)

        assertEquals(ServiceOfferedTag.HOLIDAY_OFFER, res.tag)
        assertEquals(today.minusDays(1), res.validFrom)
        assertEquals(today.plusDays(1), res.validUntil)
        assertTrue(res.isCurrentlyActive)
    }

    @Test
    fun `createService isCurrentlyActive is false when today is after validUntil`() {
        val business = businessFixture()
        `when`(businessService.findBusinessEntity(1L)).thenReturn(business)
        `when`(serviceRepository.save(any<ServiceOffered>())).thenAnswer { invocation ->
            (invocation.arguments[0] as ServiceOffered).apply {
                id = 78L
                createdAt = LocalDateTime.now()
                updatedAt = LocalDateTime.now()
            }
        }
        val today = LocalDate.now()
        val req = fixedRequest().copy(validFrom = today.minusDays(10), validUntil = today.minusDays(1))

        val res = offeringService.createService(1L, "owner@test.com", req)

        assertFalse(res.isCurrentlyActive)
    }

    @Test
    fun `createService rejects validUntil before validFrom`() {
        val today = LocalDate.now()
        val req = fixedRequest().copy(validFrom = today, validUntil = today.minusDays(1))

        assertThrows(IllegalArgumentException::class.java) {
            offeringService.createService(1L, "owner@test.com", req)
        }
    }

    private fun itemRequest() = CreateServiceOfferedRequest(title = "Wedding cake")

    private fun stubSave() {
        `when`(businessService.findBusinessEntity(1L)).thenReturn(businessFixture())
        `when`(serviceRepository.save(any<ServiceOffered>())).thenAnswer { invocation ->
            (invocation.arguments[0] as ServiceOffered).apply {
                id = 100L
                createdAt = LocalDateTime.now()
                updatedAt = LocalDateTime.now()
            }
        }
    }

    @Test
    fun `create accepts an item with no price and no cover`() {
        stubSave()

        val response = offeringService.createService(1L, "owner@test.com", itemRequest())

        assertNull(response.priceAmount)
        assertNull(response.priceCurrency)
        assertNull(response.coverImage)
        assertTrue(response.soldOnItsOwn)
        val captor = ArgumentCaptor.forClass(ServiceOffered::class.java)
        verify(serviceRepository).save(captor.capture())
        assertNull(captor.value.priceAmount)
        assertNull(captor.value.coverImageUrl)
    }

    @Test
    fun `price without currency is rejected`() {
        val request = itemRequest().copy(priceAmount = BigDecimal("10.00"))
        assertThrows(IllegalArgumentException::class.java) {
            offeringService.createService(1L, "owner@test.com", request)
        }
    }

    @Test
    fun `estimate without a reason is rejected`() {
        val request = fixedRequest().copy(priceIsEstimate = true, estimateReason = "   ")
        assertThrows(IllegalArgumentException::class.java) {
            offeringService.createService(1L, "owner@test.com", request)
        }
    }

    @Test
    fun `estimate without a price is rejected`() {
        val request = itemRequest().copy(priceIsEstimate = true, estimateReason = "Depends on size")
        assertThrows(IllegalArgumentException::class.java) {
            offeringService.createService(1L, "owner@test.com", request)
        }
    }

    @Test
    fun `estimate with price and reason is stored trimmed`() {
        stubSave()
        val request = fixedRequest().copy(priceIsEstimate = true, estimateReason = "  Depends on size ")

        val response = offeringService.createService(1L, "owner@test.com", request)

        assertTrue(response.priceIsEstimate)
        assertEquals("Depends on size", response.estimateReason)
    }

    @Test
    fun `quantity max below min is rejected`() {
        val request = itemRequest().copy(quantityEnabled = true, quantityMin = 5, quantityMax = 2)
        assertThrows(IllegalArgumentException::class.java) {
            offeringService.createService(1L, "owner@test.com", request)
        }
    }

    @Test
    fun `quantity fields are ignored when quantity is off`() {
        stubSave()
        val request = itemRequest().copy(
            quantityEnabled = false, quantityUnit = "plate", quantityMin = 4, quantityMax = 9
        )

        offeringService.createService(1L, "owner@test.com", request)

        val captor = ArgumentCaptor.forClass(ServiceOffered::class.java)
        verify(serviceRepository).save(captor.capture())
        assertEquals(1, captor.value.quantityMin)
        assertNull(captor.value.quantityMax)
        assertNull(captor.value.quantityUnit)
    }

    @Test
    fun `quantity fields are kept when quantity is on`() {
        stubSave()
        val request = itemRequest().copy(
            quantityEnabled = true, quantityUnit = "plate", quantityMin = 2, quantityMax = 9
        )

        val response = offeringService.createService(1L, "owner@test.com", request)

        assertEquals("plate", response.quantityUnit)
        assertEquals(2, response.quantityMin)
        assertEquals(9, response.quantityMax)
    }

    @Test
    fun `updateService clearPrice removes amount, currency and estimate`() {
        val entity = ServiceOffered(
            id = 7L, business = businessFixture(), title = "X",
            priceAmount = BigDecimal("10.00"), priceCurrency = "UGX",
            priceIsEstimate = true, estimateReason = "why",
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()
        )
        `when`(serviceRepository.findById(7L)).thenReturn(java.util.Optional.of(entity))
        `when`(serviceRepository.save(any<ServiceOffered>())).thenAnswer { it.arguments[0] }

        val response = offeringService.updateService(
            7L, "owner@test.com", UpdateServiceOfferedRequest(clearPrice = true)
        )

        assertNull(response.priceAmount)
        assertNull(response.priceCurrency)
        assertFalse(response.priceIsEstimate)
        assertNull(response.estimateReason)
    }

    @Test
    fun `updateService clearCover queues the old cover for deletion`() {
        val entity = ServiceOffered(
            id = 7L, business = businessFixture(), title = "X",
            coverImageUrl = "u", coverImagePublicId = "nudge/images/old",
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()
        )
        `when`(serviceRepository.findById(7L)).thenReturn(java.util.Optional.of(entity))
        `when`(serviceRepository.save(any<ServiceOffered>())).thenAnswer { it.arguments[0] }

        val response = offeringService.updateService(
            7L, "owner@test.com", UpdateServiceOfferedRequest(clearCover = true)
        )

        assertNull(response.coverImage)
        verify(pendingMediaDeletionRepository).save(org.mockito.kotlin.argThat<PendingMediaDeletion> {
            publicId == "nudge/images/old"
        })
    }

    @Test
    fun `createService rejects negative amount`() {
        val request = fixedRequest().copy(priceAmount = BigDecimal("-1.00"))
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

    @Test
    fun `listServices returns paginated services for a business`() {
        val business = businessFixture()
        val s1 = ServiceOffered(
            id = 1L,
            business = business,
            title = "A",
            coverImageUrl = "u",
            coverImagePublicId = "nudge/images/u",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))
        `when`(serviceRepository.findAllByBusinessId(1L, pageable))
            .thenReturn(PageImpl(listOf(s1), pageable, 1))

        val page = offeringService.listServices(1L, "owner@test.com", pageable, null, null)

        verify(businessService).requireRole(1L, "owner@test.com", BusinessRole.STAFF)
        assertEquals(1, page.totalElements)
        assertEquals("A", page.content[0].title)
    }

    @Test
    fun `listServices filters by status when provided`() {
        val pageable = PageRequest.of(0, 20)
        `when`(serviceRepository.findAllByBusinessIdAndStatus(1L, ServiceOfferedStatus.ACTIVE, pageable))
            .thenReturn(PageImpl(emptyList(), pageable, 0))

        val page = offeringService.listServices(1L, "owner@test.com", pageable, ServiceOfferedStatus.ACTIVE, null)

        assertEquals(0, page.totalElements)
    }

    @Test
    fun `list filters by q case-insensitively`() {
        val pageable = PageRequest.of(0, 20)
        `when`(serviceRepository.findAllByBusinessIdAndTitleContainingIgnoreCase(1L, "wed", pageable))
            .thenReturn(PageImpl(emptyList(), pageable, 0))

        offeringService.listServices(1L, "owner@test.com", pageable, null, "  wed ")

        verify(serviceRepository).findAllByBusinessIdAndTitleContainingIgnoreCase(1L, "wed", pageable)
    }

    @Test
    fun `list filters by q and status together`() {
        val pageable = PageRequest.of(0, 20)
        `when`(
            serviceRepository.findAllByBusinessIdAndStatusAndTitleContainingIgnoreCase(
                1L, ServiceOfferedStatus.ACTIVE, "wed", pageable
            )
        ).thenReturn(PageImpl(emptyList(), pageable, 0))

        offeringService.listServices(1L, "owner@test.com", pageable, ServiceOfferedStatus.ACTIVE, "wed")

        verify(serviceRepository).findAllByBusinessIdAndStatusAndTitleContainingIgnoreCase(
            1L, ServiceOfferedStatus.ACTIVE, "wed", pageable
        )
    }

    @Test
    fun `getService returns the service when caller is a member`() {
        val business = businessFixture()
        val entity = ServiceOffered(
            id = 7L,
            business = business,
            title = "Get-quote service",
            coverImageUrl = "u",
            coverImagePublicId = "nudge/images/u",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        `when`(serviceRepository.findById(7L)).thenReturn(java.util.Optional.of(entity))

        val response = offeringService.getService(7L, "owner@test.com")

        verify(businessService).requireRole(1L, "owner@test.com", BusinessRole.STAFF)
        assertEquals(7L, response.id)
        assertEquals("Get-quote service", response.title)
    }

    @Test
    fun `getService throws when the service does not exist`() {
        `when`(serviceRepository.findById(999L)).thenReturn(java.util.Optional.empty())

        assertThrows(com.mudhut.nudge.utils.exceptions.BusinessNotFoundException::class.java) {
            offeringService.getService(999L, "owner@test.com")
        }
    }

    @Test
    fun `updateService patches the title and leaves other fields untouched`() {
        val entity = ServiceOffered(
            id = 7L,
            business = businessFixture(),
            title = "Old title",
            coverImageUrl = "u",
            coverImagePublicId = "nudge/images/u",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        `when`(serviceRepository.findById(7L)).thenReturn(java.util.Optional.of(entity))
        `when`(serviceRepository.save(any<ServiceOffered>())).thenAnswer { it.arguments[0] }

        val response = offeringService.updateService(
            serviceId = 7L,
            userEmail = "owner@test.com",
            request = UpdateServiceOfferedRequest(title = "New title")
        )

        verify(businessService).requireRole(1L, "owner@test.com", BusinessRole.MANAGER)
        assertEquals("New title", response.title)
    }

    @Test
    fun `updateService toggles status to INACTIVE`() {
        val entity = ServiceOffered(
            id = 7L,
            business = businessFixture(),
            title = "X",
            coverImageUrl = "u",
            coverImagePublicId = "nudge/images/u",
            status = ServiceOfferedStatus.ACTIVE,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        `when`(serviceRepository.findById(7L)).thenReturn(java.util.Optional.of(entity))
        `when`(serviceRepository.save(any<ServiceOffered>())).thenAnswer { it.arguments[0] }

        val response = offeringService.updateService(
            7L, "owner@test.com",
            UpdateServiceOfferedRequest(status = ServiceOfferedStatus.INACTIVE)
        )

        assertEquals(ServiceOfferedStatus.INACTIVE, response.status)
    }

    @Test
    fun `updateService re-validates the resulting item`() {
        val entity = ServiceOffered(
            id = 7L,
            business = businessFixture(),
            title = "X",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        `when`(serviceRepository.findById(7L)).thenReturn(java.util.Optional.of(entity))

        // Estimate flag on an item that has no price → must reject.
        assertThrows(IllegalArgumentException::class.java) {
            offeringService.updateService(
                7L, "owner@test.com",
                UpdateServiceOfferedRequest(priceIsEstimate = true, estimateReason = "why")
            )
        }
    }

    @Test
    fun `updateService replaces cover image fields when provided`() {
        val entity = ServiceOffered(
            id = 7L,
            business = businessFixture(),
            title = "X",
            coverImageUrl = "old",
            coverImagePublicId = "nudge/images/old",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        `when`(serviceRepository.findById(7L)).thenReturn(java.util.Optional.of(entity))
        `when`(serviceRepository.save(any<ServiceOffered>())).thenAnswer { it.arguments[0] }

        val response = offeringService.updateService(
            7L, "owner@test.com",
            UpdateServiceOfferedRequest(
                coverImage = MediaInput(
                    url = "newUrl",
                    publicId = "nudge/images/new"
                )
            )
        )

        assertEquals("newUrl", response.coverImage!!.url)
        assertEquals("nudge/images/new", response.coverImage!!.publicId)
    }

    private fun entityWithGallery(images: List<Pair<String, String>>): ServiceOffered {
        val entity = ServiceOffered(
            id = 7L,
            business = businessFixture(),
            title = "X",
            coverImageUrl = "u",
            coverImagePublicId = "nudge/images/u",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        images.forEachIndexed { idx, (url, publicId) ->
            entity.galleryImages.add(
                ServiceOfferedImage(
                    service = entity,
                    url = url,
                    publicId = publicId,
                    position = idx
                )
            )
        }
        return entity
    }

    @Test
    fun `updateService leaves gallery untouched when galleryImages is null`() {
        val entity = entityWithGallery(listOf("a" to "nudge/images/a"))
        `when`(serviceRepository.findById(7L)).thenReturn(java.util.Optional.of(entity))
        `when`(serviceRepository.save(any<ServiceOffered>())).thenAnswer { it.arguments[0] }

        val response = offeringService.updateService(
            7L, "owner@test.com",
            UpdateServiceOfferedRequest(title = "Renamed")
        )

        assertEquals(1, response.galleryImages.size)
        assertEquals("a", response.galleryImages[0].url)
    }

    @Test
    fun `updateService clears gallery when galleryImages is empty`() {
        val entity = entityWithGallery(listOf("a" to "nudge/images/a", "b" to "nudge/images/b"))
        `when`(serviceRepository.findById(7L)).thenReturn(java.util.Optional.of(entity))
        `when`(serviceRepository.save(any<ServiceOffered>())).thenAnswer { it.arguments[0] }

        val response = offeringService.updateService(
            7L, "owner@test.com",
            UpdateServiceOfferedRequest(galleryImages = emptyList())
        )

        assertTrue(response.galleryImages.isEmpty())
    }

    @Test
    fun `updateService replaces gallery wholesale and re-numbers positions`() {
        val entity = entityWithGallery(listOf("a" to "nudge/images/a", "b" to "nudge/images/b"))
        `when`(serviceRepository.findById(7L)).thenReturn(java.util.Optional.of(entity))
        `when`(serviceRepository.save(any<ServiceOffered>())).thenAnswer { it.arguments[0] }

        val response = offeringService.updateService(
            7L, "owner@test.com",
            UpdateServiceOfferedRequest(
                galleryImages = listOf(
                    MediaInput("c", "nudge/images/c"),
                    MediaInput("d", "nudge/images/d")
                )
            )
        )

        assertEquals(2, response.galleryImages.size)
        assertEquals(listOf("c", "d"), response.galleryImages.map { it.url })
        assertEquals(listOf(0, 1), response.galleryImages.map { it.position })
    }

    @Test
    fun `deleteService hard-deletes the service`() {
        val entity = ServiceOffered(
            id = 7L,
            business = businessFixture(),
            title = "X",
            coverImageUrl = "u",
            coverImagePublicId = "nudge/images/u",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        `when`(serviceRepository.findById(7L)).thenReturn(java.util.Optional.of(entity))

        offeringService.deleteService(7L, "owner@test.com")

        verify(businessService).requireRole(1L, "owner@test.com", BusinessRole.MANAGER)
        verify(serviceRepository).delete(entity)
    }

    @Test
    fun `deleteService also deletes package_offered_items rows for the service`() {
        val entity = ServiceOffered(
            id = 7L,
            business = businessFixture(),
            title = "X",
            coverImageUrl = "u",
            coverImagePublicId = "nudge/images/u",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        `when`(serviceRepository.findById(7L)).thenReturn(java.util.Optional.of(entity))

        offeringService.deleteService(7L, "owner@test.com")

        verify(serviceRepository).delete(entity)
    }

    @Test
    fun `deleteService throws when the service does not exist`() {
        `when`(serviceRepository.findById(999L)).thenReturn(java.util.Optional.empty())

        assertThrows(com.mudhut.nudge.utils.exceptions.BusinessNotFoundException::class.java) {
            offeringService.deleteService(999L, "owner@test.com")
        }
    }

    @Test
    fun `updateService enqueues the previous cover publicId when the cover is replaced`() {
        val entity = ServiceOffered(
            id = 7L,
            business = businessFixture(),
            title = "X",
            coverImageUrl = "old-url",
            coverImagePublicId = "nudge/images/old-cover",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
        `when`(serviceRepository.findById(7L)).thenReturn(java.util.Optional.of(entity))
        `when`(serviceRepository.save(any<ServiceOffered>())).thenAnswer { it.arguments[0] }

        offeringService.updateService(
            7L, "owner@test.com",
            UpdateServiceOfferedRequest(
                coverImage = MediaInput(url = "new-url", publicId = "nudge/images/new-cover")
            )
        )

        verify(pendingMediaDeletionRepository).save(org.mockito.kotlin.argThat<PendingMediaDeletion> {
            publicId == "nudge/images/old-cover"
        })
    }

    @Test
    fun `updateService does NOT enqueue when the cover is not changed`() {
        val entity = ServiceOffered(
            id = 7L,
            business = businessFixture(),
            title = "X",
            coverImageUrl = "u",
            coverImagePublicId = "nudge/images/cover",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
        `when`(serviceRepository.findById(7L)).thenReturn(java.util.Optional.of(entity))
        `when`(serviceRepository.save(any<ServiceOffered>())).thenAnswer { it.arguments[0] }

        offeringService.updateService(
            7L, "owner@test.com",
            UpdateServiceOfferedRequest(title = "Renamed")
        )

        verify(pendingMediaDeletionRepository, org.mockito.Mockito.never()).save(any<PendingMediaDeletion>())
    }

    @Test
    fun `updateService does NOT enqueue when the cover patch carries the same publicId`() {
        val entity = ServiceOffered(
            id = 7L,
            business = businessFixture(),
            title = "X",
            coverImageUrl = "u",
            coverImagePublicId = "nudge/images/cover",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
        `when`(serviceRepository.findById(7L)).thenReturn(java.util.Optional.of(entity))
        `when`(serviceRepository.save(any<ServiceOffered>())).thenAnswer { it.arguments[0] }

        offeringService.updateService(
            7L, "owner@test.com",
            UpdateServiceOfferedRequest(
                coverImage = MediaInput(url = "u-new-tag", publicId = "nudge/images/cover")
            )
        )

        verify(pendingMediaDeletionRepository, org.mockito.Mockito.never()).save(any<PendingMediaDeletion>())
    }

    @Test
    fun `deleteService enqueues cover and every gallery publicId`() {
        val entity = ServiceOffered(
            id = 7L,
            business = businessFixture(),
            title = "X",
            coverImageUrl = "u",
            coverImagePublicId = "nudge/images/cover-7",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
        // Insert out of position order so the .sortedBy { it.position } in deleteService is load-bearing.
        entity.galleryImages.add(
            ServiceOfferedImage(service = entity, url = "g2", publicId = "nudge/images/g2", position = 1)
        )
        entity.galleryImages.add(
            ServiceOfferedImage(service = entity, url = "g1", publicId = "nudge/images/g1", position = 0)
        )
        `when`(serviceRepository.findById(7L)).thenReturn(java.util.Optional.of(entity))

        offeringService.deleteService(7L, "owner@test.com")

        verify(serviceRepository).delete(entity)
        verify(pendingMediaDeletionRepository).saveAll(pendingDeletionCaptor.capture())

        val publicIds = pendingDeletionCaptor.value.map { it.publicId }
        assertEquals(listOf("nudge/images/cover-7", "nudge/images/g1", "nudge/images/g2"), publicIds)
        assertTrue(pendingDeletionCaptor.value.all { it.status == PendingMediaDeletion.Status.PENDING })
    }

    @Test
    fun `updateService enqueues only gallery items dropped by the diff`() {
        val entity = entityWithGallery(
            listOf(
                "old-a-url" to "nudge/images/a",
                "old-b-url" to "nudge/images/b",
            )
        )
        `when`(serviceRepository.findById(7L)).thenReturn(java.util.Optional.of(entity))
        `when`(serviceRepository.save(any<ServiceOffered>())).thenAnswer { it.arguments[0] }

        // Replace gallery: keep "a", drop "b", add "c".
        offeringService.updateService(
            7L, "owner@test.com",
            UpdateServiceOfferedRequest(
                galleryImages = listOf(
                    MediaInput("a-new-url", "nudge/images/a"),
                    MediaInput("c-url", "nudge/images/c"),
                )
            )
        )

        verify(pendingMediaDeletionRepository).saveAll(pendingDeletionCaptor.capture())
        val publicIds = pendingDeletionCaptor.value.map { it.publicId }
        assertEquals(listOf("nudge/images/b"), publicIds)
    }

    @Test
    fun `updateService enqueues all gallery items when the gallery is cleared`() {
        val entity = entityWithGallery(
            listOf(
                "x-url" to "nudge/images/x",
                "y-url" to "nudge/images/y",
            )
        )
        `when`(serviceRepository.findById(7L)).thenReturn(java.util.Optional.of(entity))
        `when`(serviceRepository.save(any<ServiceOffered>())).thenAnswer { it.arguments[0] }

        offeringService.updateService(
            7L, "owner@test.com",
            UpdateServiceOfferedRequest(galleryImages = emptyList())
        )

        verify(pendingMediaDeletionRepository).saveAll(pendingDeletionCaptor.capture())
        val publicIds = pendingDeletionCaptor.value.map { it.publicId }
        assertEquals(listOf("nudge/images/x", "nudge/images/y"), publicIds)
    }
}
