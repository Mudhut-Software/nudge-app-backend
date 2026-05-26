package com.mudhut.nudge.businesses.publicapi.services

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.entities.BusinessCategory
import com.mudhut.nudge.businesses.entities.BusinessStatus
import com.mudhut.nudge.businesses.publicapi.models.BusinessSort
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.businesses.repositories.BusinessWithDistance
import com.mudhut.nudge.packagesoffered.entities.PackageOffered
import com.mudhut.nudge.packagesoffered.entities.PackageOfferedItem
import com.mudhut.nudge.packagesoffered.entities.PackageOfferedStatus
import com.mudhut.nudge.packagesoffered.repositories.PackageOfferedRepository
import com.mudhut.nudge.servicesoffered.entities.PriceMode
import com.mudhut.nudge.servicesoffered.entities.ServiceOffered
import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedStatus
import com.mudhut.nudge.servicesoffered.repositories.ServiceOfferedRepository
import com.mudhut.nudge.utils.exceptions.BusinessNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.math.BigDecimal
import java.util.Optional

class PublicBrowseServiceTest {

    private val businessRepository: BusinessRepository = mock()
    private val serviceRepository: ServiceOfferedRepository = mock()
    private val packageRepository: PackageOfferedRepository = mock()

    private val sut = PublicBrowseService(
        businessRepository,
        serviceRepository,
        packageRepository,
    )

    private fun category(id: Long, name: String) = BusinessCategory(id = id, name = name)

    private fun business(
        id: Long,
        name: String = "Biz $id",
        categoryId: Long = 1L,
        categoryName: String = "Catering",
        coverImageUrl: String? = null,
        coverImagePublicId: String? = null,
    ): Business {
        return Business(
            id = id,
            name = name,
            description = "About $name",
            category = category(categoryId, categoryName),
            phoneNumbers = mutableListOf(),
            email = "$name@example.com",
            logoUrl = null,
            coverImageUrl = coverImageUrl,
            coverImagePublicId = coverImagePublicId,
            address = "Kampala",
            serviceAreas = mutableListOf("Kampala"),
            status = BusinessStatus.ACTIVE,
        )
    }

    private fun service(
        id: Long,
        biz: Business,
        title: String = "Service $id",
        status: ServiceOfferedStatus = ServiceOfferedStatus.ACTIVE,
        coverUrl: String = "https://cdn/svc-$id.jpg",
    ): ServiceOffered = ServiceOffered(
        id = id,
        business = biz,
        title = title,
        description = null,
        coverImageUrl = coverUrl,
        coverImagePublicId = "pid-$id",
        priceMode = PriceMode.FIXED,
        priceAmount = BigDecimal("100.00"),
        priceCurrency = "UGX",
        priceUnit = null,
        status = status,
    )

    private fun businessWithDistance(id: Long, distanceKm: Double): BusinessWithDistance =
        object : BusinessWithDistance {
            override val id = id
            override val distanceKm = distanceKm
        }

    private fun stubSummaryHelpers(bizId: Long, coverFromService: String? = null) {
        whenever(serviceRepository.findFirstByBusinessIdAndStatusOrderByCreatedAtAsc(eq(bizId), eq(ServiceOfferedStatus.ACTIVE)))
            .thenReturn(coverFromService?.let { service(id = bizId * 10, biz = business(bizId), coverUrl = it) })
        whenever(serviceRepository.countByBusinessIdAndStatus(eq(bizId), eq(ServiceOfferedStatus.ACTIVE))).thenReturn(1L)
        whenever(packageRepository.countCurrentlyActiveByBusinessId(eq(bizId), any())).thenReturn(0L)
    }

    @Test
    fun `summary cover falls back to first active service when business cover is null`() {
        val biz = business(id = 5, coverImageUrl = null)
        val firstActiveService = service(id = 50, biz = biz, coverUrl = "https://cdn/svc-50.jpg")
        whenever(businessRepository.findPublicQualifiedNewest(eq(null), any())).thenReturn(PageImpl(listOf(biz)))
        whenever(serviceRepository.findFirstByBusinessIdAndStatusOrderByCreatedAtAsc(eq(5), eq(ServiceOfferedStatus.ACTIVE)))
            .thenReturn(firstActiveService)
        whenever(serviceRepository.countByBusinessIdAndStatus(eq(5), eq(ServiceOfferedStatus.ACTIVE))).thenReturn(1L)
        whenever(packageRepository.countCurrentlyActiveByBusinessId(eq(5), any())).thenReturn(0L)

        val summary = sut.list(null, BusinessSort.NEWEST, null, null, Pageable.ofSize(20)).content.single()

        assertEquals("https://cdn/svc-50.jpg", summary.coverImageUrl)
    }

    @Test
    fun `summary cover prefers business coverImageUrl when set`() {
        val biz = business(id = 5, coverImageUrl = "https://cdn/biz-5.jpg")
        whenever(businessRepository.findPublicQualifiedNewest(eq(null), any())).thenReturn(PageImpl(listOf(biz)))
        whenever(serviceRepository.findFirstByBusinessIdAndStatusOrderByCreatedAtAsc(eq(5), eq(ServiceOfferedStatus.ACTIVE)))
            .thenReturn(service(id = 50, biz = biz, coverUrl = "https://cdn/svc-50.jpg"))
        whenever(serviceRepository.countByBusinessIdAndStatus(eq(5), eq(ServiceOfferedStatus.ACTIVE))).thenReturn(1L)
        whenever(packageRepository.countCurrentlyActiveByBusinessId(eq(5), any())).thenReturn(0L)

        val summary = sut.list(null, BusinessSort.NEWEST, null, null, Pageable.ofSize(20)).content.single()

        assertEquals("https://cdn/biz-5.jpg", summary.coverImageUrl)
    }

    @Test
    fun `list with sort=NEWEST and category delegates to findPublicQualifiedNewest`() {
        val biz = business(id = 7, categoryId = 1, categoryName = "Catering")
        whenever(businessRepository.findPublicQualifiedNewest(eq(1L), any())).thenReturn(PageImpl(listOf(biz)))
        stubSummaryHelpers(7)

        val page = sut.list(1L, BusinessSort.NEWEST, null, null, Pageable.ofSize(20))

        assertEquals(1, page.totalElements)
        assertEquals(7L, page.content.single().id)
        assertNull(page.content.single().distanceKm)
    }

    @Test
    fun `list with sort=POPULAR delegates to findPublicQualifiedPopular`() {
        val biz = business(id = 8)
        whenever(businessRepository.findPublicQualifiedPopular(eq(null), any())).thenReturn(PageImpl(listOf(biz)))
        stubSummaryHelpers(8)

        val page = sut.list(null, BusinessSort.POPULAR, null, null, Pageable.ofSize(20))

        assertEquals(8L, page.content.single().id)
        assertNull(page.content.single().distanceKm)
    }

    @Test
    fun `list with sort=NEAREST returns distanceKm and preserves DB ordering`() {
        val far = business(id = 20)
        val near = business(id = 10)
        whenever(businessRepository.findPublicQualifiedNearest(eq(null), eq(0.0), eq(0.0), any()))
            .thenReturn(
                PageImpl(
                    listOf(
                        businessWithDistance(10L, 1.5),
                        businessWithDistance(20L, 9.8),
                    )
                )
            )
        whenever(businessRepository.findAllById(setOf(10L, 20L))).thenReturn(listOf(far, near))
        stubSummaryHelpers(10)
        stubSummaryHelpers(20)

        val page = sut.list(null, BusinessSort.NEAREST, 0.0, 0.0, Pageable.ofSize(20))

        assertEquals(listOf(10L, 20L), page.content.map { it.id })
        assertEquals(1.5, page.content[0].distanceKm)
        assertEquals(9.8, page.content[1].distanceKm)
    }

    @Test
    fun `list with sort=NEAREST without lat or lng throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            sut.list(null, BusinessSort.NEAREST, null, null, Pageable.ofSize(20))
        }
        assertThrows(IllegalArgumentException::class.java) {
            sut.list(null, BusinessSort.NEAREST, 0.0, null, Pageable.ofSize(20))
        }
    }

    @Test
    fun `list with sort=NEAREST returns empty page when no qualified businesses`() {
        whenever(businessRepository.findPublicQualifiedNearest(eq(null), eq(0.0), eq(0.0), any()))
            .thenReturn(PageImpl(emptyList()))

        val page = sut.list(null, BusinessSort.NEAREST, 0.0, 0.0, Pageable.ofSize(20))

        assertTrue(page.content.isEmpty())
    }

    @Test
    fun `detail returns embedded active services sorted by createdAt desc`() {
        val biz = business(id = 9)
        val newer = service(id = 91, biz = biz, title = "Newer")
        val older = service(id = 92, biz = biz, title = "Older")

        whenever(businessRepository.findById(9)).thenReturn(Optional.of(biz))
        whenever(serviceRepository.findTop20ByBusinessIdAndStatusOrderByCreatedAtDesc(eq(9), eq(ServiceOfferedStatus.ACTIVE)))
            .thenReturn(listOf(newer, older))
        whenever(packageRepository.findTop20CurrentlyActiveByBusinessIdOrderByCreatedAtDesc(eq(9), any()))
            .thenReturn(emptyList())

        val detail = sut.detail(9)

        assertEquals(listOf(91L, 92L), detail.services.map { it.id })
        assertEquals(emptyList<Long>(), detail.packages.map { it.id })
    }

    @Test
    fun `detail filters packages to active items only`() {
        val biz = business(id = 11)
        val activeService = service(id = 110, biz = biz)
        val inactiveService = service(id = 111, biz = biz, status = ServiceOfferedStatus.INACTIVE)

        val pkg = PackageOffered(
            id = 200L,
            business = biz,
            title = "Combo",
            priceAmount = BigDecimal("250000.00"),
            priceCurrency = "UGX",
            tag = null,
            validFrom = null,
            validUntil = null,
            status = PackageOfferedStatus.ACTIVE,
        )
        pkg.items.add(PackageOfferedItem(packageOffered = pkg, service = activeService, position = 0))
        pkg.items.add(PackageOfferedItem(packageOffered = pkg, service = inactiveService, position = 1))

        whenever(businessRepository.findById(11)).thenReturn(Optional.of(biz))
        whenever(serviceRepository.findTop20ByBusinessIdAndStatusOrderByCreatedAtDesc(eq(11), eq(ServiceOfferedStatus.ACTIVE)))
            .thenReturn(listOf(activeService))
        whenever(packageRepository.findTop20CurrentlyActiveByBusinessIdOrderByCreatedAtDesc(eq(11), any()))
            .thenReturn(listOf(pkg))

        val detail = sut.detail(11)

        val pkgItems = detail.packages.single().items
        assertEquals(listOf(110L), pkgItems.map { it.id })
    }

    @Test
    fun `detail throws 404 when business not found`() {
        whenever(businessRepository.findById(404)).thenReturn(Optional.empty())

        assertThrows(BusinessNotFoundException::class.java) { sut.detail(404) }
    }

    @Test
    fun `detail throws 404 when business has no active services and no currently-active packages`() {
        val biz = business(id = 50)
        whenever(businessRepository.findById(50)).thenReturn(Optional.of(biz))
        whenever(serviceRepository.findTop20ByBusinessIdAndStatusOrderByCreatedAtDesc(eq(50), eq(ServiceOfferedStatus.ACTIVE)))
            .thenReturn(emptyList())
        whenever(packageRepository.findTop20CurrentlyActiveByBusinessIdOrderByCreatedAtDesc(eq(50), any()))
            .thenReturn(emptyList())

        assertThrows(BusinessNotFoundException::class.java) { sut.detail(50) }
    }

    @Test
    fun `detail allows business with currently-active packages but no active services`() {
        val biz = business(id = 60)
        val activeService = service(id = 600, biz = biz)
        val pkg = PackageOffered(
            id = 700L,
            business = biz,
            title = "P",
            priceAmount = BigDecimal("100.00"),
            priceCurrency = "UGX",
            tag = null, validFrom = null, validUntil = null,
            status = PackageOfferedStatus.ACTIVE,
        )
        pkg.items.add(PackageOfferedItem(packageOffered = pkg, service = activeService, position = 0))

        whenever(businessRepository.findById(60)).thenReturn(Optional.of(biz))
        whenever(serviceRepository.findTop20ByBusinessIdAndStatusOrderByCreatedAtDesc(eq(60), eq(ServiceOfferedStatus.ACTIVE)))
            .thenReturn(emptyList())
        whenever(packageRepository.findTop20CurrentlyActiveByBusinessIdOrderByCreatedAtDesc(eq(60), any()))
            .thenReturn(listOf(pkg))

        val detail = sut.detail(60)

        assertEquals(60L, detail.id)
        assertTrue(detail.services.isEmpty())
        assertEquals(700L, detail.packages.single().id)
    }
}
