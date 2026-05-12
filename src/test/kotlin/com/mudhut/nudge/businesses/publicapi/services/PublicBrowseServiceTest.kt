package com.mudhut.nudge.businesses.publicapi.services

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.entities.BusinessCategory
import com.mudhut.nudge.businesses.entities.BusinessStatus
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.packagesoffered.entities.PackageOffered
import com.mudhut.nudge.packagesoffered.entities.PackageOfferedItem
import com.mudhut.nudge.packagesoffered.entities.PackageOfferedStatus
import com.mudhut.nudge.packagesoffered.repositories.PackageOfferedRepository
import com.mudhut.nudge.servicesoffered.entities.PriceMode
import com.mudhut.nudge.servicesoffered.entities.ServiceOffered
import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedStatus
import com.mudhut.nudge.servicesoffered.repositories.ServiceOfferedRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.web.server.ResponseStatusException
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

    @Test
    fun `lanes groups qualified businesses by category and caps at 10 per lane`() {
        val cateringBusinesses = (1L..12L).map { business(id = it, categoryId = 1, categoryName = "Catering") }
        val pharmacy = business(id = 100, categoryId = 2, categoryName = "Pharmacy")

        whenever(businessRepository.findAllPublicQualified()).thenReturn(cateringBusinesses + pharmacy)
        whenever(serviceRepository.findFirstByBusinessIdAndStatusOrderByCreatedAtAsc(any(), eq(ServiceOfferedStatus.ACTIVE)))
            .thenAnswer { service(id = 999, biz = cateringBusinesses[0]) }
        whenever(serviceRepository.countByBusinessIdAndStatus(any(), eq(ServiceOfferedStatus.ACTIVE)))
            .thenReturn(3L)
        whenever(packageRepository.countCurrentlyActiveByBusinessId(any(), any())).thenReturn(0L)

        val lanes = sut.lanes()

        assertEquals(2, lanes.size)
        val cateringLane = lanes.first { it.categoryName == "Catering" }
        assertEquals(10, cateringLane.businesses.size)
        val pharmacyLane = lanes.first { it.categoryName == "Pharmacy" }
        assertEquals(1, pharmacyLane.businesses.size)
    }

    @Test
    fun `lanes omits categories with zero qualified businesses`() {
        whenever(businessRepository.findAllPublicQualified()).thenReturn(emptyList())

        val lanes = sut.lanes()

        assertTrue(lanes.isEmpty())
    }

    @Test
    fun `summary cover falls back to first active service when business cover is null`() {
        val biz = business(id = 5, coverImageUrl = null)
        val firstActiveService = service(id = 50, biz = biz, coverUrl = "https://cdn/svc-50.jpg")
        whenever(businessRepository.findAllPublicQualified()).thenReturn(listOf(biz))
        whenever(serviceRepository.findFirstByBusinessIdAndStatusOrderByCreatedAtAsc(eq(5), eq(ServiceOfferedStatus.ACTIVE)))
            .thenReturn(firstActiveService)
        whenever(serviceRepository.countByBusinessIdAndStatus(eq(5), eq(ServiceOfferedStatus.ACTIVE))).thenReturn(1L)
        whenever(packageRepository.countCurrentlyActiveByBusinessId(eq(5), any())).thenReturn(0L)

        val lanes = sut.lanes()
        val summary = lanes.single().businesses.single()

        assertEquals("https://cdn/svc-50.jpg", summary.coverImageUrl)
    }

    @Test
    fun `summary cover prefers business coverImageUrl when set`() {
        val biz = business(id = 5, coverImageUrl = "https://cdn/biz-5.jpg")
        whenever(businessRepository.findAllPublicQualified()).thenReturn(listOf(biz))
        whenever(serviceRepository.findFirstByBusinessIdAndStatusOrderByCreatedAtAsc(eq(5), eq(ServiceOfferedStatus.ACTIVE)))
            .thenReturn(service(id = 50, biz = biz, coverUrl = "https://cdn/svc-50.jpg"))
        whenever(serviceRepository.countByBusinessIdAndStatus(eq(5), eq(ServiceOfferedStatus.ACTIVE))).thenReturn(1L)
        whenever(packageRepository.countCurrentlyActiveByBusinessId(eq(5), any())).thenReturn(0L)

        val summary = sut.lanes().single().businesses.single()

        assertEquals("https://cdn/biz-5.jpg", summary.coverImageUrl)
    }

    @Test
    fun `byCategory delegates to findPublicByCategory`() {
        val biz = business(id = 7, categoryId = 1, categoryName = "Catering")
        whenever(businessRepository.findPublicByCategory(eq(1L), any())).thenReturn(PageImpl(listOf(biz)))
        whenever(serviceRepository.findFirstByBusinessIdAndStatusOrderByCreatedAtAsc(eq(7), any()))
            .thenReturn(service(id = 70, biz = biz))
        whenever(serviceRepository.countByBusinessIdAndStatus(eq(7), any())).thenReturn(2L)
        whenever(packageRepository.countCurrentlyActiveByBusinessId(eq(7), any())).thenReturn(1L)

        val page = sut.byCategory(1L, Pageable.ofSize(20))

        assertEquals(1, page.totalElements)
        assertEquals(7L, page.content.single().id)
        assertEquals(2, page.content.single().serviceCount)
        assertEquals(1, page.content.single().packageCount)
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

        val ex = assertThrows(ResponseStatusException::class.java) { sut.detail(404) }
        assertEquals(404, ex.statusCode.value())
    }

    @Test
    fun `detail throws 404 when business has no active services and no currently-active packages`() {
        val biz = business(id = 50)
        whenever(businessRepository.findById(50)).thenReturn(Optional.of(biz))
        whenever(serviceRepository.findTop20ByBusinessIdAndStatusOrderByCreatedAtDesc(eq(50), eq(ServiceOfferedStatus.ACTIVE)))
            .thenReturn(emptyList())
        whenever(packageRepository.findTop20CurrentlyActiveByBusinessIdOrderByCreatedAtDesc(eq(50), any()))
            .thenReturn(emptyList())

        val ex = assertThrows(ResponseStatusException::class.java) { sut.detail(50) }
        assertEquals(404, ex.statusCode.value())
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
