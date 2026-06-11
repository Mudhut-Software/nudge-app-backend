package com.mudhut.nudge.servicerequests.services

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.servicerequests.entities.ServiceRequest
import com.mudhut.nudge.servicerequests.entities.ServiceRequestItem
import com.mudhut.nudge.servicerequests.entities.ServiceRequestItemAddon
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.models.CreateRequestPayload
import com.mudhut.nudge.servicerequests.models.RequestItemInput
import com.mudhut.nudge.servicerequests.models.ServiceRequestItemAddonInput
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestRepository
import com.mudhut.nudge.servicesoffered.entities.PriceMode
import com.mudhut.nudge.servicesoffered.entities.ServiceAddon
import com.mudhut.nudge.servicesoffered.entities.ServiceOffered
import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedStatus
import com.mudhut.nudge.servicesoffered.repositories.ServiceAddonRepository
import com.mudhut.nudge.servicesoffered.repositories.ServiceOfferedRepository
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.utils.exceptions.ServiceAddonNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Optional

class ServiceRequestServiceAddonsTest {

    private val repo: ServiceRequestRepository = mock()
    private val userRepo: UserRepository = mock()
    private val businessRepo: BusinessRepository = mock()
    private val serviceRepo: ServiceOfferedRepository = mock()
    private val addonRepo: ServiceAddonRepository = mock()

    private lateinit var sut: ServiceRequestService

    private val customer = User(id = 1L, email = "c@e", username = "Cust")
    private val biz = Business(id = 2L, name = "Biz")
    private val service = ServiceOffered(
        id = 10L,
        business = biz,
        title = "Catering",
        coverImageUrl = "u",
        coverImagePublicId = "p",
        priceMode = PriceMode.FIXED,
        priceAmount = BigDecimal("100"),
        priceCurrency = "UGX",
        status = ServiceOfferedStatus.ACTIVE,
    )
    private val quoteService = ServiceOffered(
        id = 11L,
        business = biz,
        title = "Errand",
        coverImageUrl = "u",
        coverImagePublicId = "p",
        priceMode = PriceMode.QUOTE,
        status = ServiceOfferedStatus.ACTIVE,
    )
    private val addon = ServiceAddon(
        id = 99L,
        service = service,
        title = "Extra tray",
        priceDelta = BigDecimal("5000"),
        priceUnit = "per tray",
        quantifiable = true,
        defaultQuantity = 1,
        maxQuantity = 10,
        position = 0,
    )

    @BeforeEach
    fun setUp() {
        sut = ServiceRequestService(repo, userRepo, businessRepo, serviceRepo, addonRepo)
        whenever(userRepo.findByEmail("c@e")).thenReturn(Optional.of(customer))
        whenever(businessRepo.findById(2L)).thenReturn(Optional.of(biz))
        whenever(serviceRepo.findAllById(listOf(10L))).thenReturn(listOf(service))
        whenever(serviceRepo.findAllById(listOf(11L))).thenReturn(listOf(quoteService))
        whenever(addonRepo.findAllById(listOf(99L))).thenReturn(listOf(addon))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }
    }

    @Test
    fun `create attaches addon row with quantity but null snapshots`() {
        val payload = CreateRequestPayload(
            businessId = 2L,
            items = listOf(RequestItemInput(
                serviceId = 10L,
                addonInputs = listOf(ServiceRequestItemAddonInput(addonId = 99L, quantity = 3)),
            )),
        )

        val res = sut.create("c@e", payload)

        assertThat(res.items[0].addons).hasSize(1)
        val a = res.items[0].addons[0]
        assertThat(a.addonId).isEqualTo(99L)
        assertThat(a.quantity).isEqualTo(3)
        assertThat(a.title).isEqualTo("Extra tray") // live read in DRAFT
        assertThat(a.priceDelta).isEqualByComparingTo("5000")
    }

    @Test
    fun `create clamps quantity to maxQuantity`() {
        val payload = CreateRequestPayload(
            businessId = 2L,
            items = listOf(RequestItemInput(
                serviceId = 10L,
                addonInputs = listOf(ServiceRequestItemAddonInput(addonId = 99L, quantity = 999)),
            )),
        )

        val res = sut.create("c@e", payload)
        assertThat(res.items[0].addons[0].quantity).isEqualTo(10)
    }

    @Test
    fun `create floors quantity at 1 when zero or negative`() {
        val payload = CreateRequestPayload(
            businessId = 2L,
            items = listOf(RequestItemInput(
                serviceId = 10L,
                addonInputs = listOf(ServiceRequestItemAddonInput(addonId = 99L, quantity = 0)),
            )),
        )

        val res = sut.create("c@e", payload)
        assertThat(res.items[0].addons[0].quantity).isEqualTo(1)
    }

    @Test
    fun `create rejects addon that does not belong to the item's service`() {
        val foreignAddon = ServiceAddon(id = 77L, service = quoteService, title = "X", position = 0)
        whenever(addonRepo.findAllById(listOf(77L))).thenReturn(listOf(foreignAddon))

        val payload = CreateRequestPayload(
            businessId = 2L,
            items = listOf(RequestItemInput(
                serviceId = 10L,
                addonInputs = listOf(ServiceRequestItemAddonInput(addonId = 77L, quantity = 1)),
            )),
        )

        assertThatThrownBy { sut.create("c@e", payload) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("does not belong")
    }

    @Test
    fun `create with addonInputs on a QUOTE service throws`() {
        val payload = CreateRequestPayload(
            businessId = 2L,
            items = listOf(RequestItemInput(
                serviceId = 11L, // QUOTE
                addonInputs = listOf(ServiceRequestItemAddonInput(addonId = 99L, quantity = 1)),
            )),
        )

        assertThatThrownBy { sut.create("c@e", payload) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Addons are not allowed")
    }

    @Test
    fun `create with missing addon id throws ServiceAddonNotFoundException`() {
        whenever(addonRepo.findAllById(listOf(999L))).thenReturn(emptyList())

        val payload = CreateRequestPayload(
            businessId = 2L,
            items = listOf(RequestItemInput(
                serviceId = 10L,
                addonInputs = listOf(ServiceRequestItemAddonInput(addonId = 999L, quantity = 1)),
            )),
        )

        assertThatThrownBy { sut.create("c@e", payload) }
            .isInstanceOf(ServiceAddonNotFoundException::class.java)
    }

    @Test
    fun `submit snapshots addon title, priceDelta, priceUnit`() {
        val draft = ServiceRequest(
            id = 50L,
            customer = customer,
            business = biz,
            status = ServiceRequestStatus.DRAFT,
            requestedDate = LocalDateTime.now().plusDays(1),
            serviceLocation = "Place",
        )
        val item = ServiceRequestItem(request = draft, service = service, position = 0)
        item.addons.add(ServiceRequestItemAddon(
            item = item, addon = addon, quantity = 2, position = 0,
        ))
        draft.items.add(item)
        whenever(repo.findById(50L)).thenReturn(Optional.of(draft))

        sut.submit("c@e", 50L)

        val snap = item.addons[0]
        assertThat(snap.snapshotTitle).isEqualTo("Extra tray")
        assertThat(snap.snapshotPriceDelta).isEqualByComparingTo("5000")
        assertThat(snap.snapshotPriceUnit).isEqualTo("per tray")
        assertThat(snap.quantity).isEqualTo(2)
    }

    @Test
    fun `toResponse on submitted request reads addon fields from snapshot (survives addon deletion)`() {
        val draft = ServiceRequest(
            id = 50L,
            customer = customer,
            business = biz,
            status = ServiceRequestStatus.PENDING,
            requestedDate = LocalDateTime.now().plusDays(1),
            serviceLocation = "Place",
            submittedAt = LocalDateTime.now(),
        )
        val item = ServiceRequestItem(request = draft, service = service, position = 0)
        item.addons.add(ServiceRequestItemAddon(
            item = item, addon = null,
            snapshotTitle = "Extra tray (renamed away)",
            snapshotPriceDelta = BigDecimal("5000"),
            snapshotPriceUnit = "per tray",
            quantity = 2, position = 0,
        ))
        draft.items.add(item)
        whenever(repo.findById(50L)).thenReturn(Optional.of(draft))

        val res = sut.get("c@e", 50L)

        val a = res.items[0].addons[0]
        assertThat(a.title).isEqualTo("Extra tray (renamed away)")
        assertThat(a.addonId).isNull()
        assertThat(a.priceDelta).isEqualByComparingTo("5000")
    }
}
