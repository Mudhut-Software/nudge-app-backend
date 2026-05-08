package com.mudhut.nudge.packagesoffered.services

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.services.BusinessService
import com.mudhut.nudge.packagesoffered.entities.PackageOffered
import com.mudhut.nudge.packagesoffered.entities.PackageOfferedStatus
import com.mudhut.nudge.packagesoffered.entities.PackageOfferedTag
import com.mudhut.nudge.packagesoffered.models.CreatePackageOfferedRequest
import com.mudhut.nudge.packagesoffered.models.UpdatePackageOfferedRequest
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
import com.mudhut.nudge.utils.exceptions.BusinessNotFoundException
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Optional

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

    private fun packageFixture(
        id: Long = 7L,
        business: Business = businessFixture(),
        title: String = "Old title",
        tag: PackageOfferedTag? = null,
        validFrom: LocalDate? = null,
        validUntil: LocalDate? = null,
        status: PackageOfferedStatus = PackageOfferedStatus.ACTIVE,
    ) = PackageOffered(
        id = id,
        business = business,
        title = title,
        priceAmount = BigDecimal("100.00"),
        priceCurrency = "UGX",
        tag = tag,
        validFrom = validFrom,
        validUntil = validUntil,
        status = status,
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now(),
    )

    @Test
    fun `updatePackage patches the title only`() {
        val pkg = packageFixture()
        `when`(packageRepository.findById(7L)).thenReturn(Optional.of(pkg))
        `when`(packageRepository.save(any<PackageOffered>())).thenAnswer { it.arguments[0] }

        val response = packagesService.updatePackage(
            packageId = 7L,
            userEmail = "owner@test.com",
            request = UpdatePackageOfferedRequest(title = "New title"),
        )

        verify(businessService).requireRole(1L, "owner@test.com", BusinessRole.MANAGER)
        assertEquals("New title", response.title)
        assertEquals(BigDecimal("100.00"), response.priceAmount)
    }

    @Test
    fun `updatePackage toggles status to INACTIVE`() {
        val pkg = packageFixture(status = PackageOfferedStatus.ACTIVE)
        `when`(packageRepository.findById(7L)).thenReturn(Optional.of(pkg))
        `when`(packageRepository.save(any<PackageOffered>())).thenAnswer { it.arguments[0] }

        val response = packagesService.updatePackage(
            7L, "owner@test.com",
            UpdatePackageOfferedRequest(status = PackageOfferedStatus.INACTIVE),
        )

        assertEquals(PackageOfferedStatus.INACTIVE, response.status)
        assertFalse(response.isCurrentlyActive)
    }

    @Test
    fun `updatePackage sets a tag from null`() {
        val pkg = packageFixture(tag = null)
        `when`(packageRepository.findById(7L)).thenReturn(Optional.of(pkg))
        `when`(packageRepository.save(any<PackageOffered>())).thenAnswer { it.arguments[0] }

        val response = packagesService.updatePackage(
            7L, "owner@test.com",
            UpdatePackageOfferedRequest(tag = PackageOfferedTag.HOLIDAY_OFFER),
        )

        assertEquals(PackageOfferedTag.HOLIDAY_OFFER, response.tag)
    }

    @Test
    fun `updatePackage leaves tag alone when payload tag is null`() {
        // Documented v1 limitation: Kotlin/Jackson can't tell "absent" from
        // "explicit null", so null = no-change. Untagging via PATCH is
        // intentionally unsupported in v1; delete + recreate, or wait for
        // a future DELETE /packages/{id}/tag endpoint.
        val pkg = packageFixture(tag = PackageOfferedTag.WEEKEND_DEAL)
        `when`(packageRepository.findById(7L)).thenReturn(Optional.of(pkg))
        `when`(packageRepository.save(any<PackageOffered>())).thenAnswer { it.arguments[0] }

        val response = packagesService.updatePackage(
            7L, "owner@test.com",
            UpdatePackageOfferedRequest(tag = null),
        )

        assertEquals(PackageOfferedTag.WEEKEND_DEAL, response.tag)
    }

    @Test
    fun `updatePackage replaces serviceIds wholesale and renumbers positions`() {
        val business = businessFixture()
        val pkg = packageFixture(business = business)
        // Existing items: [10L position 0]
        pkg.items.add(
            com.mudhut.nudge.packagesoffered.entities.PackageOfferedItem(
                packageOffered = pkg,
                service = serviceFixture(id = 10L, business = business),
                position = 0,
            )
        )

        val newServiceA = serviceFixture(id = 20L, business = business)
        val newServiceB = serviceFixture(id = 30L, business = business)
        `when`(packageRepository.findById(7L)).thenReturn(Optional.of(pkg))
        `when`(serviceRepository.findAllById(listOf(20L, 30L)))
            .thenReturn(listOf(newServiceA, newServiceB))
        `when`(packageRepository.save(any<PackageOffered>())).thenAnswer { it.arguments[0] }

        val response = packagesService.updatePackage(
            7L, "owner@test.com",
            UpdatePackageOfferedRequest(serviceIds = listOf(20L, 30L)),
        )

        assertEquals(2, response.items.size)
        assertEquals(listOf(20L, 30L), response.items.map { it.service.id })
        assertEquals(listOf(0, 1), response.items.map { it.position })
    }

    @Test
    fun `updatePackage rejects serviceIds replacement with cross-business service`() {
        val target = businessFixture(id = 1L)
        val other = Business(id = 2L, name = "Other")
        val pkg = packageFixture(business = target)
        val foreign = serviceFixture(id = 20L, business = other)

        `when`(packageRepository.findById(7L)).thenReturn(Optional.of(pkg))
        `when`(serviceRepository.findAllById(listOf(20L))).thenReturn(listOf(foreign))

        assertThrows(IllegalArgumentException::class.java) {
            packagesService.updatePackage(
                7L, "owner@test.com",
                UpdatePackageOfferedRequest(serviceIds = listOf(20L)),
            )
        }
    }

    @Test
    fun `deletePackage hard-deletes the package`() {
        val pkg = packageFixture()
        `when`(packageRepository.findById(7L)).thenReturn(Optional.of(pkg))

        packagesService.deletePackage(7L, "owner@test.com")

        verify(businessService).requireRole(1L, "owner@test.com", BusinessRole.MANAGER)
        verify(packageRepository).delete(pkg)
    }

    @Test
    fun `deletePackage throws when not found`() {
        `when`(packageRepository.findById(999L)).thenReturn(Optional.empty())

        assertThrows(BusinessNotFoundException::class.java) {
            packagesService.deletePackage(999L, "owner@test.com")
        }
    }

    @Test
    fun `isCurrentlyActive is true when ACTIVE and within window`() {
        val pkg = packageFixture(
            status = PackageOfferedStatus.ACTIVE,
            validFrom = LocalDate.now().minusDays(1),
            validUntil = LocalDate.now().plusDays(1),
        )
        `when`(packageRepository.findById(7L)).thenReturn(Optional.of(pkg))

        val response = packagesService.getPackage(7L, "owner@test.com")

        assertTrue(response.isCurrentlyActive)
    }

    @Test
    fun `isCurrentlyActive is true when ACTIVE and no window`() {
        val pkg = packageFixture(
            status = PackageOfferedStatus.ACTIVE,
            validFrom = null,
            validUntil = null,
        )
        `when`(packageRepository.findById(7L)).thenReturn(Optional.of(pkg))

        val response = packagesService.getPackage(7L, "owner@test.com")

        assertTrue(response.isCurrentlyActive)
    }

    @Test
    fun `isCurrentlyActive is false when ACTIVE but window has not started`() {
        val pkg = packageFixture(
            status = PackageOfferedStatus.ACTIVE,
            validFrom = LocalDate.now().plusDays(7),
            validUntil = LocalDate.now().plusDays(14),
        )
        `when`(packageRepository.findById(7L)).thenReturn(Optional.of(pkg))

        val response = packagesService.getPackage(7L, "owner@test.com")

        assertFalse(response.isCurrentlyActive)
    }

    @Test
    fun `isCurrentlyActive is false when ACTIVE but window has expired`() {
        val pkg = packageFixture(
            status = PackageOfferedStatus.ACTIVE,
            validFrom = LocalDate.now().minusDays(14),
            validUntil = LocalDate.now().minusDays(1),
        )
        `when`(packageRepository.findById(7L)).thenReturn(Optional.of(pkg))

        val response = packagesService.getPackage(7L, "owner@test.com")

        assertFalse(response.isCurrentlyActive)
    }

    @Test
    fun `isCurrentlyActive is false when INACTIVE regardless of window`() {
        val pkg = packageFixture(
            status = PackageOfferedStatus.INACTIVE,
            validFrom = LocalDate.now().minusDays(1),
            validUntil = LocalDate.now().plusDays(1),
        )
        `when`(packageRepository.findById(7L)).thenReturn(Optional.of(pkg))

        val response = packagesService.getPackage(7L, "owner@test.com")

        assertFalse(response.isCurrentlyActive)
    }

    @Test
    fun `getPackage returns the package when caller is a member`() {
        val business = businessFixture()
        val pkg = PackageOffered(
            id = 7L,
            business = business,
            title = "Combo",
            priceAmount = BigDecimal("200.00"),
            priceCurrency = "UGX",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
        `when`(packageRepository.findById(7L)).thenReturn(Optional.of(pkg))

        val response = packagesService.getPackage(7L, "owner@test.com")

        verify(businessService).requireRole(1L, "owner@test.com", BusinessRole.STAFF)
        assertEquals(7L, response.id)
        assertEquals("Combo", response.title)
    }

    @Test
    fun `getPackage throws when the package does not exist`() {
        `when`(packageRepository.findById(999L)).thenReturn(Optional.empty())

        assertThrows(BusinessNotFoundException::class.java) {
            packagesService.getPackage(999L, "owner@test.com")
        }
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
