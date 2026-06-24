package com.mudhut.nudge.servicesoffered.services

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.services.BusinessService
import com.mudhut.nudge.servicesoffered.entities.PendingMediaDeletion
import com.mudhut.nudge.servicesoffered.entities.PriceMode
import com.mudhut.nudge.servicesoffered.entities.ServiceAddon
import com.mudhut.nudge.servicesoffered.entities.ServiceOffered
import com.mudhut.nudge.servicesoffered.events.ServiceAddonDeletedEvent
import com.mudhut.nudge.servicesoffered.models.CreateServiceAddonRequest
import com.mudhut.nudge.servicesoffered.models.ReorderAddonsRequest
import com.mudhut.nudge.servicesoffered.models.UpdateServiceAddonRequest
import com.mudhut.nudge.servicesoffered.repositories.PendingMediaDeletionRepository
import com.mudhut.nudge.servicesoffered.repositories.ServiceAddonRepository
import com.mudhut.nudge.servicesoffered.repositories.ServiceOfferedRepository
import com.mudhut.nudge.utils.exceptions.ServiceAddonNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import java.math.BigDecimal
import java.util.Optional

class ServiceAddonServiceTest {

    private val addonRepo: ServiceAddonRepository = mock()
    private val serviceRepo: ServiceOfferedRepository = mock()
    private val pendingMediaDeletionRepo: PendingMediaDeletionRepository = mock()
    private val businessService: BusinessService = mock()
    private val events: ApplicationEventPublisher = mock()

    private lateinit var sut: ServiceAddonService

    private val email = "owner@example.com"
    private val business = Business(id = 1L, name = "Biz")
    private val service = ServiceOffered(
        id = 10L,
        business = business,
        title = "Catering",
        coverImageUrl = "u",
        coverImagePublicId = "p",
        priceMode = PriceMode.FIXED,
        priceAmount = BigDecimal("100.00"),
        priceCurrency = "UGX",
    )

    @BeforeEach
    fun setUp() {
        sut = ServiceAddonService(addonRepo, serviceRepo, pendingMediaDeletionRepo, businessService, events)
        whenever(serviceRepo.findById(10L)).thenReturn(Optional.of(service))
        whenever(addonRepo.save(any())).thenAnswer {
            (it.arguments[0] as ServiceAddon).apply { if (id == null) id = 99L }
        }
    }

    @Test
    fun `create assigns next position and returns response`() {
        whenever(addonRepo.findMaxPositionByServiceId(10L)).thenReturn(2)

        val req = CreateServiceAddonRequest(title = "Extra tray", priceDelta = BigDecimal("5000"))
        val res = sut.create(serviceId = 10L, userEmail = email, request = req)

        assertThat(res.position).isEqualTo(3)
        assertThat(res.title).isEqualTo("Extra tray")
        verify(businessService).requireRole(1L, email, BusinessRole.MANAGER)
    }

    @Test
    fun `create on empty list starts at position 0`() {
        whenever(addonRepo.findMaxPositionByServiceId(10L)).thenReturn(-1)

        val res = sut.create(10L, email, CreateServiceAddonRequest(title = "First"))
        assertThat(res.position).isEqualTo(0)
    }

    @Test
    fun `update skips null fields and persists non-null ones`() {
        val existing = ServiceAddon(id = 5L, service = service, title = "Old", position = 0)
        whenever(addonRepo.findById(5L)).thenReturn(Optional.of(existing))

        sut.update(serviceId = 10L, addonId = 5L, userEmail = email,
            request = UpdateServiceAddonRequest(title = "New"))

        assertThat(existing.title).isEqualTo("New")
    }

    @Test
    fun `update enqueues PendingMediaDeletion when cover is replaced`() {
        val existing = ServiceAddon(
            id = 5L, service = service, title = "X", position = 0,
            coverImageUrl = "old-url", coverImagePublicId = "nudge/images/old"
        )
        whenever(addonRepo.findById(5L)).thenReturn(Optional.of(existing))

        sut.update(10L, 5L, email, UpdateServiceAddonRequest(
            coverImageUrl = "new-url",
            coverImagePublicId = "nudge/images/new",
        ))

        val captor = argumentCaptor<PendingMediaDeletion>()
        verify(pendingMediaDeletionRepo).save(captor.capture())
        assertThat(captor.firstValue.publicId).isEqualTo("nudge/images/old")
        assertThat(existing.coverImagePublicId).isEqualTo("nudge/images/new")
    }

    @Test
    fun `update does not enqueue cleanup when cover unchanged`() {
        val existing = ServiceAddon(
            id = 5L, service = service, title = "X", position = 0,
            coverImageUrl = "url", coverImagePublicId = "nudge/images/p"
        )
        whenever(addonRepo.findById(5L)).thenReturn(Optional.of(existing))

        sut.update(10L, 5L, email, UpdateServiceAddonRequest(title = "Renamed"))

        verify(pendingMediaDeletionRepo, never()).save(any())
    }

    @Test
    fun `non-quantifiable forces quantity=1`() {
        val existing = ServiceAddon(
            id = 5L, service = service, title = "X", position = 0,
            quantifiable = true, defaultQuantity = 5, maxQuantity = 10
        )
        whenever(addonRepo.findById(5L)).thenReturn(Optional.of(existing))

        sut.update(10L, 5L, email, UpdateServiceAddonRequest(quantifiable = false))

        assertThat(existing.quantifiable).isFalse()
        assertThat(existing.defaultQuantity).isEqualTo(1)
        assertThat(existing.maxQuantity).isEqualTo(1)
    }

    @Test
    fun `delete enqueues cover cleanup and removes the row`() {
        val existing = ServiceAddon(
            id = 5L, service = service, title = "X", position = 0,
            coverImagePublicId = "nudge/images/p"
        )
        whenever(addonRepo.findById(5L)).thenReturn(Optional.of(existing))

        sut.delete(serviceId = 10L, addonId = 5L, userEmail = email)

        verify(pendingMediaDeletionRepo).save(any())
        verify(addonRepo).delete(existing)
    }

    @Test
    fun `delete publishes ServiceAddonDeletedEvent before removing the addon`() {
        val existing = ServiceAddon(id = 5L, service = service, title = "X", position = 0)
        whenever(addonRepo.findById(5L)).thenReturn(Optional.of(existing))

        sut.delete(10L, 5L, email)

        val captor = argumentCaptor<ServiceAddonDeletedEvent>()
        val order = inOrder(events, addonRepo)
        order.verify(events).publishEvent(captor.capture())
        order.verify(addonRepo).delete(existing)
        assertThat(captor.firstValue.addonId).isEqualTo(5L)
    }

    @Test
    fun `delete does not enqueue when no cover`() {
        val existing = ServiceAddon(id = 5L, service = service, title = "X", position = 0)
        whenever(addonRepo.findById(5L)).thenReturn(Optional.of(existing))

        sut.delete(10L, 5L, email)
        verify(pendingMediaDeletionRepo, never()).save(any())
    }

    @Test
    fun `update on unknown addon throws ServiceAddonNotFoundException`() {
        whenever(addonRepo.findById(999L)).thenReturn(Optional.empty())
        assertThatThrownBy { sut.update(10L, 999L, email, UpdateServiceAddonRequest(title = "x")) }
            .isInstanceOf(ServiceAddonNotFoundException::class.java)
    }

    @Test
    fun `reorder applies new positions in one pass`() {
        val a = ServiceAddon(id = 1L, service = service, title = "A", position = 0)
        val b = ServiceAddon(id = 2L, service = service, title = "B", position = 1)
        val c = ServiceAddon(id = 3L, service = service, title = "C", position = 2)
        whenever(addonRepo.findAllByServiceIdOrderByPositionAsc(10L)).thenReturn(listOf(a, b, c))

        sut.reorder(10L, ReorderAddonsRequest(orderedIds = listOf(3, 1, 2)), email)

        assertThat(c.position).isEqualTo(0)
        assertThat(a.position).isEqualTo(1)
        assertThat(b.position).isEqualTo(2)
    }

    @Test
    fun `reorder rejects when ids do not match the service's existing set`() {
        val a = ServiceAddon(id = 1L, service = service, title = "A", position = 0)
        whenever(addonRepo.findAllByServiceIdOrderByPositionAsc(10L)).thenReturn(listOf(a))

        assertThatThrownBy {
            sut.reorder(10L, ReorderAddonsRequest(orderedIds = listOf(1, 99)), email)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `list returns ordered responses`() {
        val a = ServiceAddon(id = 1L, service = service, title = "A", position = 0)
        val b = ServiceAddon(id = 2L, service = service, title = "B", position = 1)
        whenever(addonRepo.findAllByServiceIdOrderByPositionAsc(10L)).thenReturn(listOf(a, b))

        val res = sut.list(10L, email)
        assertThat(res.map { it.id }).containsExactly(1L, 2L)
    }
}
