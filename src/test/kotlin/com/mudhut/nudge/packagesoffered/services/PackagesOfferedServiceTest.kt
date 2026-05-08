package com.mudhut.nudge.packagesoffered.services

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.services.BusinessService
import com.mudhut.nudge.packagesoffered.entities.PackageOffered
import com.mudhut.nudge.packagesoffered.entities.PackageOfferedStatus
import com.mudhut.nudge.packagesoffered.models.CreatePackageOfferedRequest
import com.mudhut.nudge.packagesoffered.repositories.PackageOfferedItemRepository
import com.mudhut.nudge.packagesoffered.repositories.PackageOfferedRepository
import com.mudhut.nudge.servicesoffered.entities.PriceMode
import com.mudhut.nudge.servicesoffered.entities.ServiceOffered
import com.mudhut.nudge.servicesoffered.repositories.ServiceOfferedRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDate
import org.junit.jupiter.api.extension.ExtendWith
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
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class PackagesOfferedServiceTest {

    @Mock
    private lateinit var packageRepository: PackageOfferedRepository

    @Mock
    private lateinit var packageItemRepository: PackageOfferedItemRepository

    @Mock
    private lateinit var serviceRepository: ServiceOfferedRepository

    @Mock
    private lateinit var businessService: BusinessService

    @InjectMocks
    private lateinit var packagesService: PackagesOfferedService

    private fun businessFixture(id: Long = 1L) = Business(id = id, name = "Test Biz")

    private fun serviceFixture(
        id: Long,
        business: Business,
        title: String = "Sofa cleaning",
        amount: BigDecimal = BigDecimal("50000.00"),
    ) = ServiceOffered(
        id = id,
        business = business,
        title = title,
        coverImageUrl = "u",
        coverImagePublicId = "nudge/images/u",
        priceMode = PriceMode.FIXED,
        priceAmount = amount,
        priceCurrency = "UGX",
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now(),
    )

    @Test
    fun `createPackage persists a package with a single service`() {
        val business = businessFixture()
        val service = serviceFixture(id = 10L, business = business)

        `when`(businessService.findBusinessEntity(1L)).thenReturn(business)
        `when`(serviceRepository.findAllById(listOf(10L))).thenReturn(listOf(service))
        `when`(packageRepository.save(any<PackageOffered>())).thenAnswer { invocation ->
            (invocation.arguments[0] as PackageOffered).apply {
                id = 99L
                createdAt = LocalDateTime.now()
                updatedAt = LocalDateTime.now()
            }
        }

        val response = packagesService.createPackage(
            businessId = 1L,
            userEmail = "owner@test.com",
            request = CreatePackageOfferedRequest(
                title = "Solo bundle",
                serviceIds = listOf(10L),
                priceAmount = BigDecimal("45000.00"),
                priceCurrency = "UGX",
            ),
        )

        verify(businessService).requireRole(1L, "owner@test.com", BusinessRole.MANAGER)
        assertEquals(99L, response.id)
        assertEquals(1L, response.businessId)
        assertEquals("Solo bundle", response.title)
        assertEquals(BigDecimal("45000.00"), response.priceAmount)
        assertEquals("UGX", response.priceCurrency)
        assertNull(response.tag)
        assertNull(response.validFrom)
        assertNull(response.validUntil)
        assertEquals(PackageOfferedStatus.ACTIVE, response.status)
        assertTrue(response.isCurrentlyActive)
        assertEquals(1, response.items.size)
        assertEquals(10L, response.items[0].service.id)
        assertEquals(0, response.items[0].position)
    }

    @Test
    fun `createPackage rejects empty serviceIds`() {
        val business = businessFixture()
        `when`(businessService.findBusinessEntity(1L)).thenReturn(business)
        `when`(serviceRepository.findAllById(emptyList<Long>())).thenReturn(emptyList())
        val request = CreatePackageOfferedRequest(
            title = "X",
            serviceIds = emptyList(),
            priceAmount = BigDecimal("100.00"),
            priceCurrency = "UGX",
        )
        assertThrows(IllegalArgumentException::class.java) {
            packagesService.createPackage(1L, "owner@test.com", request)
        }
    }

    @Test
    fun `createPackage rejects duplicate serviceIds in the request`() {
        val business = businessFixture()
        val service = serviceFixture(id = 10L, business = business)
        `when`(businessService.findBusinessEntity(1L)).thenReturn(business)
        `when`(serviceRepository.findAllById(listOf(10L, 10L))).thenReturn(listOf(service))
        val request = CreatePackageOfferedRequest(
            title = "X",
            serviceIds = listOf(10L, 10L),
            priceAmount = BigDecimal("100.00"),
            priceCurrency = "UGX",
        )
        assertThrows(IllegalArgumentException::class.java) {
            packagesService.createPackage(1L, "owner@test.com", request)
        }
    }

    @Test
    fun `createPackage rejects when a serviceId does not exist`() {
        val business = businessFixture()
        val service = serviceFixture(id = 10L, business = business)
        `when`(businessService.findBusinessEntity(1L)).thenReturn(business)
        `when`(serviceRepository.findAllById(listOf(10L, 99L)))
            .thenReturn(listOf(service))   // 99L missing

        val request = CreatePackageOfferedRequest(
            title = "X",
            serviceIds = listOf(10L, 99L),
            priceAmount = BigDecimal("100.00"),
            priceCurrency = "UGX",
        )
        assertThrows(IllegalArgumentException::class.java) {
            packagesService.createPackage(1L, "owner@test.com", request)
        }
    }

    @Test
    fun `createPackage rejects services from a different business`() {
        val targetBusiness = businessFixture(id = 1L)
        val otherBusiness = Business(id = 2L, name = "Other Biz")
        val foreign = serviceFixture(id = 10L, business = otherBusiness)

        `when`(businessService.findBusinessEntity(1L)).thenReturn(targetBusiness)
        `when`(serviceRepository.findAllById(listOf(10L))).thenReturn(listOf(foreign))

        val request = CreatePackageOfferedRequest(
            title = "X",
            serviceIds = listOf(10L),
            priceAmount = BigDecimal("100.00"),
            priceCurrency = "UGX",
        )
        assertThrows(IllegalArgumentException::class.java) {
            packagesService.createPackage(1L, "owner@test.com", request)
        }
    }

    @Test
    fun `listPackages returns paginated packages for a business`() {
        val business = businessFixture()
        val pkg = PackageOffered(
            id = 1L,
            business = business,
            title = "A",
            priceAmount = BigDecimal("100.00"),
            priceCurrency = "UGX",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
        val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))
        `when`(packageRepository.findAllByBusinessId(1L, pageable))
            .thenReturn(PageImpl(listOf(pkg), pageable, 1))

        val page = packagesService.listPackages(1L, "owner@test.com", pageable, null)

        verify(businessService).requireRole(1L, "owner@test.com", BusinessRole.STAFF)
        assertEquals(1, page.totalElements)
        assertEquals("A", page.content[0].title)
    }

    @Test
    fun `listPackages filters by status when provided`() {
        val pageable = PageRequest.of(0, 20)
        `when`(packageRepository.findAllByBusinessIdAndStatus(1L, PackageOfferedStatus.ACTIVE, pageable))
            .thenReturn(PageImpl(emptyList(), pageable, 0))

        val page = packagesService.listPackages(1L, "owner@test.com", pageable, PackageOfferedStatus.ACTIVE)

        assertEquals(0, page.totalElements)
    }

    @Test
    fun `createPackage rejects when validFrom is after validUntil`() {
        val business = businessFixture()
        val service = serviceFixture(id = 10L, business = business)
        `when`(businessService.findBusinessEntity(1L)).thenReturn(business)
        `when`(serviceRepository.findAllById(listOf(10L))).thenReturn(listOf(service))

        val request = CreatePackageOfferedRequest(
            title = "X",
            serviceIds = listOf(10L),
            priceAmount = BigDecimal("100.00"),
            priceCurrency = "UGX",
            validFrom = LocalDate.of(2026, 12, 20),
            validUntil = LocalDate.of(2026, 12, 1),
        )
        assertThrows(IllegalArgumentException::class.java) {
            packagesService.createPackage(1L, "owner@test.com", request)
        }
    }
}
