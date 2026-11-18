package com.mudhut.nudge.servicerequests.services

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.entities.BusinessCategory
import com.mudhut.nudge.businesses.entities.BusinessStatus
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.servicerequests.entities.ProposalOutcome
import com.mudhut.nudge.servicerequests.entities.ServiceRequest
import com.mudhut.nudge.servicerequests.entities.PricingState
import com.mudhut.nudge.servicerequests.entities.ServiceRequestItem
import com.mudhut.nudge.servicerequests.entities.ServiceRequestItemPick
import com.mudhut.nudge.servicerequests.entities.ServiceRequestPriceProposal
import com.mudhut.nudge.servicerequests.entities.ServiceRequestProposal
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.events.PriceEventKind
import com.mudhut.nudge.servicerequests.events.RequestActor
import com.mudhut.nudge.servicerequests.models.AttachmentInput
import com.mudhut.nudge.servicerequests.models.CreateRequestPayload
import com.mudhut.nudge.servicerequests.models.PickInput
import com.mudhut.nudge.servicerequests.models.RequestItemInput
import com.mudhut.nudge.servicerequests.models.UpdateRequestPayload
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestPriceProposalRepository
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestProposalRepository
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestRepository
import com.mudhut.nudge.servicesoffered.entities.GroupChoice
import com.mudhut.nudge.servicesoffered.entities.GroupSelection
import com.mudhut.nudge.servicesoffered.entities.OptionGroup
import com.mudhut.nudge.servicesoffered.entities.ServiceOffered
import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedStatus
import com.mudhut.nudge.servicesoffered.repositories.ServiceOfferedRepository
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.entities.UserRole
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.utils.exceptions.BusinessNotFoundException
import com.mudhut.nudge.utils.exceptions.InvalidStateTransitionException
import com.mudhut.nudge.utils.exceptions.PriceNotSettledException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Optional

class ServiceRequestServiceTest {

    private val repo: ServiceRequestRepository = mock()
    private val userRepo: UserRepository = mock()
    private val businessRepo: BusinessRepository = mock()
    private val serviceRepo: ServiceOfferedRepository = mock()
    private val publisher: ServiceRequestEventPublisher = mock()
    private val popularityPublisher: RequestPopularityPublisher = mock()
    private val proposalRepo: ServiceRequestProposalRepository = mock()
    private val priceProposalRepo: ServiceRequestPriceProposalRepository = mock()

    private val sut = ServiceRequestService(
        repo, userRepo, businessRepo, serviceRepo, publisher, popularityPublisher, proposalRepo, priceProposalRepo,
    )

    init {
        // Price-touching paths load the request with a row lock. The locked finder answers with
        // whatever each test stubbed on findById, so fixtures stay written once.
        whenever(repo.findWithLockById(any())).thenAnswer { repo.findById(it.arguments[0] as Long).orElse(null) }
    }

    // --- fixtures ---

    private fun user(id: Long = 1L, email: String = "alice@example.com") = User(
        id = id,
        username = "Alice",
        email = email,
        phoneNumber = "+256700000000",
        password = "hashed",
        role = UserRole.BASIC_USER,
        isActive = true,
    )

    private fun business(id: Long = 10L) = Business(
        id = id,
        name = "SparkleClean",
        description = "Cleaning",
        category = BusinessCategory(id = 1L, name = "Cleaning"),
        owner = user(id = 99L, email = "owner@sparkle.com"),
        phoneNumbers = mutableListOf(),
        email = "biz@example.com",
        logoUrl = null,
        address = "Kampala",
        serviceAreas = mutableListOf("Kampala"),
        status = BusinessStatus.ACTIVE,
    )

    private fun service(id: Long, biz: Business, status: ServiceOfferedStatus = ServiceOfferedStatus.ACTIVE) =
        ServiceOffered(
            id = id,
            business = biz,
            title = "Deep Clean",
            description = "Full home",
            coverImageUrl = "https://cdn/svc.jpg",
            coverImagePublicId = "nudge/images/svc",
            priceAmount = BigDecimal("250000.00"),
            priceCurrency = "UGX",
            status = status,
        )


    // --- create ---

    @Test
    fun `create writes a DRAFT with items`() {
        val alice = user()
        val biz = business()
        val svc = service(id = 100L, biz = biz)

        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(businessRepo.findById(biz.id!!)).thenReturn(Optional.of(biz))
        whenever(serviceRepo.findAllById(listOf(100L))).thenReturn(listOf(svc))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }

        val response = sut.create(
            email = alice.email!!,
            payload = CreateRequestPayload(businessId = biz.id, items = listOf(RequestItemInput(serviceId = 100L))),
        )

        assertEquals(ServiceRequestStatus.DRAFT, response.status)
        assertEquals(1, response.items.size)
        assertEquals(100L, response.items[0].serviceId)
    }

    @Test
    fun `create rejects when business not found`() {
        whenever(userRepo.findByEmail(any())).thenReturn(Optional.of(user()))
        whenever(businessRepo.findById(eq(999L))).thenReturn(Optional.empty())

        assertThrows(BusinessNotFoundException::class.java) {
            sut.create(
                email = "alice@example.com",
                payload = CreateRequestPayload(businessId = 999L, items = listOf(RequestItemInput(serviceId = 1L))),
            )
        }
    }

    @Test
    fun `create rejects when item references a service from a different business`() {
        val alice = user()
        val biz = business()
        val otherBiz = business(id = 11L).apply { name = "Other" }
        val foreign = service(id = 200L, biz = otherBiz)

        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(businessRepo.findById(biz.id!!)).thenReturn(Optional.of(biz))
        whenever(serviceRepo.findAllById(listOf(200L))).thenReturn(listOf(foreign))

        assertThrows(IllegalArgumentException::class.java) {
            sut.create(
                email = alice.email!!,
                payload = CreateRequestPayload(businessId = biz.id, items = listOf(RequestItemInput(serviceId = 200L))),
            )
        }
    }

    // --- patch ---

    @Test
    fun `patch updates fields while DRAFT`() {
        val alice = user()
        val biz = business()
        val req = ServiceRequest(
            id = 1L,
            customer = alice,
            business = biz,
            status = ServiceRequestStatus.DRAFT,
        )

        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(req))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }

        val response = sut.patch(
            email = alice.email!!,
            id = 1L,
            payload = UpdateRequestPayload(
                serviceLocation = "Plot 24, Acacia Hill",
                requestedDate = LocalDateTime.now().plusDays(3),
                note = "Bring extra gear",
            ),
        )

        assertEquals("Plot 24, Acacia Hill", response.serviceLocation)
        assertNotNull(response.requestedDate)
        assertEquals("Bring extra gear", response.note)
    }

    @Test
    fun `cancel persists the reason and normalises blank to null`() {
        // The endpoint accepted `reason` and discarded it via an `ignoredReason`
        // local, so a cancellation was never explained to the provider.
        val alice = user()
        val req = ServiceRequest(
            id = 1L,
            customer = alice,
            business = business(),
            status = ServiceRequestStatus.CONFIRMED,
        )
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(req))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }

        val withReason = sut.cancel(alice.email!!, 1L, "  Something came up  ")
        assertEquals("Something came up", withReason.cancellationReason)

        // Blank input must normalise to null so "no reason" has one representation.
        req.status = ServiceRequestStatus.CONFIRMED
        req.cancellationReason = null
        val blank = sut.cancel(alice.email!!, 1L, "   ")
        assertNull(blank.cancellationReason)
    }

    @Test
    fun `patch keeps accessDirections and note independent across successive patches`() {
        // Regression: the wizard collects access directions on step 3 and the note
        // to the provider on step 4. Both used to read and write `note`, so
        // answering step 4 destroyed the step-3 directions and the provider
        // arrived at a locked gate.
        val alice = user()
        val req = ServiceRequest(
            id = 1L,
            customer = alice,
            business = business(),
            status = ServiceRequestStatus.DRAFT,
        )

        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(req))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }

        // Step 3 writes the directions.
        sut.patch(
            email = alice.email!!,
            id = 1L,
            payload = UpdateRequestPayload(
                accessDirections = "The keys will be with the gateman at the side gate",
            ),
        )

        // Step 4 writes the note and must not disturb the directions.
        val afterNote = sut.patch(
            email = alice.email!!,
            id = 1L,
            payload = UpdateRequestPayload(note = "Please call when you arrive"),
        )

        assertEquals("Please call when you arrive", afterNote.note)
        assertEquals(
            "The keys will be with the gateman at the side gate",
            afterNote.accessDirections,
        )

        // And editing the directions afterwards must not disturb the note.
        val afterDirections = sut.patch(
            email = alice.email!!,
            id = 1L,
            payload = UpdateRequestPayload(accessDirections = "Gate code is 4417"),
        )

        assertEquals("Gate code is 4417", afterDirections.accessDirections)
        assertEquals("Please call when you arrive", afterDirections.note)
    }

    @Test
    fun `patch rejects on non-DRAFT request`() {
        val alice = user()
        val req = ServiceRequest(
            id = 1L,
            customer = alice,
            business = business(),
            status = ServiceRequestStatus.PENDING,
        )

        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(req))

        assertThrows(IllegalStateException::class.java) {
            sut.patch(
                email = alice.email!!,
                id = 1L,
                payload = UpdateRequestPayload(note = "too late"),
            )
        }
    }

    @Test
    fun `patch accepts up to 8 attachments`() {
        val alice = user()
        val req = ServiceRequest(id = 1L, customer = alice, business = business(), status = ServiceRequestStatus.DRAFT)
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(req))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }

        val eight = (1..8).map {
            AttachmentInput(url = "https://cdn/$it.jpg", publicId = "nudge/images/$it", kind = "image")
        }
        val response = sut.patch(alice.email!!, 1L, UpdateRequestPayload(attachments = eight))
        assertEquals(8, response.attachments.size)
    }

    @Test
    fun `patch rejects more than 8 attachments`() {
        val alice = user()
        val req = ServiceRequest(id = 1L, customer = alice, business = business(), status = ServiceRequestStatus.DRAFT)
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(req))

        val nine = (1..9).map {
            AttachmentInput(url = "https://cdn/$it.jpg", publicId = "nudge/images/$it", kind = "image")
        }
        assertThrows(IllegalArgumentException::class.java) {
            sut.patch(alice.email!!, 1L, UpdateRequestPayload(attachments = nine))
        }
    }

    // --- submit ---

    @Test
    fun `submit transitions DRAFT to PENDING, writes snapshots, sets submittedAt`() {
        val alice = user()
        val biz = business()
        val svc = service(id = 100L, biz = biz)
        val req = ServiceRequest(
            id = 1L,
            customer = alice,
            business = biz,
            status = ServiceRequestStatus.DRAFT,
            requestedDate = LocalDateTime.now().plusDays(3),
            serviceLocation = "Kampala",
        )
        req.items.add(
            com.mudhut.nudge.servicerequests.entities.ServiceRequestItem(
                request = req,
                service = svc,
                position = 0,
            )
        )

        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(req))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }

        val response = sut.submit(alice.email!!, 1L)

        assertEquals(ServiceRequestStatus.PENDING, response.status)
        assertNotNull(response.submittedAt)
        val item = req.items[0]
        assertEquals("Deep Clean", item.snapshotTitle)
        assertEquals(BigDecimal("250000.00"), item.snapshotPriceAmount)
        assertEquals("UGX", item.snapshotPriceCurrency)
        assertEquals("https://cdn/svc.jpg", item.snapshotCoverUrl)
    }

    @Test
    fun `submit publishes ServiceRequestSubmittedEvent`() {
        val alice = user()
        val biz = business()
        val svc = service(id = 100L, biz = biz)
        val req = ServiceRequest(
            id = 1L,
            customer = alice,
            business = biz,
            status = ServiceRequestStatus.DRAFT,
            requestedDate = LocalDateTime.now().plusDays(3),
            serviceLocation = "Kampala",
        )
        req.items.add(
            com.mudhut.nudge.servicerequests.entities.ServiceRequestItem(
                request = req,
                service = svc,
                position = 0,
            )
        )
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(req))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }

        sut.submit(alice.email!!, 1L)

        // The event's own construction is covered by ServiceRequestEventPublisherTest.
        // What matters here is that submit delegates with the pre-mutation `from`.
        val captor = argumentCaptor<ServiceRequest>()
        val fromCaptor = argumentCaptor<ServiceRequestStatus>()
        verify(publisher).statusChanged(
            captor.capture(),
            fromCaptor.capture(),
            eq(RequestActor.CUSTOMER),
            eq(null),
            eq(null),
        )
        assertEquals(1L, captor.firstValue.id)
        assertEquals(ServiceRequestStatus.DRAFT, fromCaptor.firstValue)
        assertEquals(ServiceRequestStatus.PENDING, captor.firstValue.status)
    }

    @Test
    fun `withdraw publishes nothing`() {
        // PENDING -> DRAFT is the customer un-submitting. The provider does not
        // need an email saying "never mind".
        val alice = user()
        val req = ServiceRequest(
            id = 1L,
            customer = alice,
            business = business(),
            status = ServiceRequestStatus.PENDING,
        )
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(req))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }

        sut.withdraw(alice.email!!, 1L)

        verify(publisher, never()).statusChanged(any(), any(), any(), any(), any())
    }

    @Test
    fun `submit rejects DRAFT with zero items`() {
        val alice = user()
        val req = ServiceRequest(
            id = 1L,
            customer = alice,
            business = business(),
            status = ServiceRequestStatus.DRAFT,
            requestedDate = LocalDateTime.now().plusDays(3),
            serviceLocation = "Kampala",
        )

        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(req))

        assertThrows(IllegalArgumentException::class.java) { sut.submit(alice.email!!, 1L) }
    }

    @Test
    fun `submit rejects DRAFT with null requestedDate`() {
        val alice = user()
        val biz = business()
        val svc = service(id = 100L, biz = biz)
        val req = ServiceRequest(
            id = 1L,
            customer = alice,
            business = biz,
            status = ServiceRequestStatus.DRAFT,
            requestedDate = null,
            serviceLocation = "Kampala",
        )
        req.items.add(
            com.mudhut.nudge.servicerequests.entities.ServiceRequestItem(
                request = req, service = svc, position = 0,
            )
        )
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(req))

        assertThrows(IllegalArgumentException::class.java) { sut.submit(alice.email!!, 1L) }
    }

    @Test
    fun `submit rejects requestedDate in the past`() {
        val alice = user()
        val biz = business()
        val svc = service(id = 100L, biz = biz)
        val req = ServiceRequest(
            id = 1L,
            customer = alice,
            business = biz,
            status = ServiceRequestStatus.DRAFT,
            requestedDate = LocalDateTime.now().minusDays(1),
            serviceLocation = "Kampala",
        )
        req.items.add(
            com.mudhut.nudge.servicerequests.entities.ServiceRequestItem(
                request = req, service = svc, position = 0,
            )
        )
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(req))

        assertThrows(IllegalArgumentException::class.java) { sut.submit(alice.email!!, 1L) }
    }

    @Test
    fun `submit from PENDING throws InvalidStateTransition`() {
        val alice = user()
        val req = ServiceRequest(id = 1L, customer = alice, business = business(), status = ServiceRequestStatus.PENDING)
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(req))

        assertThrows(InvalidStateTransitionException::class.java) { sut.submit(alice.email!!, 1L) }
    }

    // --- withdraw ---

    @Test
    fun `withdraw transitions PENDING back to DRAFT and clears submittedAt`() {
        val alice = user()
        val req = ServiceRequest(
            id = 1L, customer = alice, business = business(),
            status = ServiceRequestStatus.PENDING,
            submittedAt = LocalDateTime.now(),
        )
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(req))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }

        val response = sut.withdraw(alice.email!!, 1L)

        assertEquals(ServiceRequestStatus.DRAFT, response.status)
        assertNull(response.submittedAt)
    }

    // --- cancel ---

    @Test
    fun `cancel transitions PENDING to CANCELLED with cancelledAt set`() {
        val alice = user()
        val req = ServiceRequest(id = 1L, customer = alice, business = business(), status = ServiceRequestStatus.PENDING)
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(req))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }

        val response = sut.cancel(alice.email!!, 1L, reason = "Changed mind")

        assertEquals(ServiceRequestStatus.CANCELLED, response.status)
        assertNotNull(response.cancelledAt)
    }

    @Test
    fun `cancel from CONFIRMED is allowed`() {
        val alice = user()
        val req = ServiceRequest(id = 1L, customer = alice, business = business(), status = ServiceRequestStatus.CONFIRMED)
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(req))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }

        val response = sut.cancel(alice.email!!, 1L, null)

        assertEquals(ServiceRequestStatus.CANCELLED, response.status)
        verify(popularityPublisher).recomputeAndPublish(10L)
    }

    @Test
    fun `cancel from DRAFT is rejected (drafts hard-delete)`() {
        val alice = user()
        val req = ServiceRequest(id = 1L, customer = alice, business = business(), status = ServiceRequestStatus.DRAFT)
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(req))

        assertThrows(InvalidStateTransitionException::class.java) {
            sut.cancel(alice.email!!, 1L, null)
        }
    }

    // --- delete ---

    @Test
    fun `delete only allowed on DRAFT`() {
        val alice = user()
        val req = ServiceRequest(id = 1L, customer = alice, business = business(), status = ServiceRequestStatus.DRAFT)
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(req))

        sut.delete(alice.email!!, 1L)
    }

    @Test
    fun `delete rejects on PENDING`() {
        val alice = user()
        val req = ServiceRequest(id = 1L, customer = alice, business = business(), status = ServiceRequestStatus.PENDING)
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(req))

        assertThrows(IllegalStateException::class.java) { sut.delete(alice.email!!, 1L) }
    }

    // --- ownership ---

    @Test
    fun `actions on other-customer requests throw 404`() {
        val alice = user(id = 1L, email = "alice@example.com")
        val bob = user(id = 2L, email = "bob@example.com")
        val req = ServiceRequest(id = 1L, customer = bob, business = business(), status = ServiceRequestStatus.DRAFT)
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(req))

        assertThrows(BusinessNotFoundException::class.java) {
            sut.get(alice.email!!, 1L)
        }
    }

    // --- duplicate ---

    @Test
    fun `duplicate clones items that are still ACTIVE`() {
        val alice = user()
        val biz = business()
        val svcActive = service(id = 100L, biz = biz)
        val original = ServiceRequest(
            id = 1L,
            customer = alice,
            business = biz,
            status = ServiceRequestStatus.COMPLETED,
        )
        original.items.add(
            com.mudhut.nudge.servicerequests.entities.ServiceRequestItem(
                request = original,
                service = svcActive,
                position = 0,
                snapshotTitle = "Deep Clean",
            )
        )

        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(original))
        whenever(serviceRepo.findAllById(listOf(100L))).thenReturn(listOf(svcActive))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }

        val response = sut.duplicate(alice.email!!, 1L)

        assertEquals(ServiceRequestStatus.DRAFT, response.request.status)
        assertEquals(1, response.request.items.size)
        assertEquals(100L, response.request.items[0].serviceId)
        assertTrue(response.unavailableItems.isEmpty())
        assertNull(response.request.requestedDate)
        assertNull(response.request.serviceLocation)
        assertNull(response.request.note)
        assertTrue(response.request.attachments.isEmpty())
    }

    @Test
    fun `duplicate skips inactive services and names them in unavailableItems`() {
        val alice = user()
        val biz = business()
        val svcActive = service(id = 100L, biz = biz)
        val svcInactive = service(id = 101L, biz = biz, status = ServiceOfferedStatus.INACTIVE)
        val original = ServiceRequest(
            id = 1L,
            customer = alice,
            business = biz,
            status = ServiceRequestStatus.COMPLETED,
        )
        original.items.addAll(
            listOf(
                com.mudhut.nudge.servicerequests.entities.ServiceRequestItem(
                    request = original, service = svcActive, position = 0,
                    snapshotTitle = "Deep Clean",
                ),
                com.mudhut.nudge.servicerequests.entities.ServiceRequestItem(
                    request = original, service = svcInactive, position = 1,
                    snapshotTitle = "Carpet Cleaning",
                ),
            )
        )

        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(original))
        whenever(serviceRepo.findAllById(listOf(100L, 101L))).thenReturn(listOf(svcActive, svcInactive))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }

        val response = sut.duplicate(alice.email!!, 1L)

        assertEquals(1, response.request.items.size)
        assertEquals(100L, response.request.items[0].serviceId)
        assertEquals(listOf("Carpet Cleaning"), response.unavailableItems)
    }

    @Test
    fun `duplicate returns a 0-item DRAFT when every original item is gone`() {
        val alice = user()
        val biz = business()
        val svcInactive = service(id = 200L, biz = biz, status = ServiceOfferedStatus.INACTIVE)
        val original = ServiceRequest(
            id = 1L,
            customer = alice,
            business = biz,
            status = ServiceRequestStatus.COMPLETED,
        )
        original.items.add(
            com.mudhut.nudge.servicerequests.entities.ServiceRequestItem(
                request = original, service = svcInactive, position = 0,
                snapshotTitle = "Carpet Cleaning",
            )
        )

        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(original))
        whenever(serviceRepo.findAllById(listOf(200L))).thenReturn(listOf(svcInactive))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }

        val response = sut.duplicate(alice.email!!, 1L)

        assertEquals(0, response.request.items.size)
        assertEquals(listOf("Carpet Cleaning"), response.unavailableItems)
    }

    @Test
    fun `duplicate of another customer's request throws 404`() {
        val alice = user(id = 1L, email = "alice@example.com")
        val bob = user(id = 2L, email = "bob@example.com")
        val req = ServiceRequest(id = 1L, customer = bob, business = business(), status = ServiceRequestStatus.COMPLETED)
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(req))

        assertThrows(BusinessNotFoundException::class.java) {
            sut.duplicate(alice.email!!, 1L)
        }
    }

    // --- responding to a proposal ---

    private fun proposedRequest(alice: User) = ServiceRequest(
        id = 1L,
        customer = alice,
        business = business(),
        status = ServiceRequestStatus.REVISION_REQUESTED,
        requestedDate = LocalDateTime.now().plusDays(1),
    )

    /**
     * Scratch verification per code review: don't assume the surefire
     * `-Duser.timezone=Africa/Kampala` argLine (see pom.xml) actually applied —
     * confirm the JVM's default zone really is Kampala before trusting any
     * assertion below that depends on local and UTC clocks disagreeing. On a
     * UTC test JVM this whole file's UTC-vs-local assertions would be vacuous
     * (both clocks agree), so this must hold or the regression tests below
     * prove nothing.
     */
    private fun assertTestJvmPinnedToNonUtcZone() {
        assertEquals(ZoneId.of("Africa/Kampala"), ZoneId.systemDefault())
    }

    /**
     * A `respondedAt` written by `LocalDateTime.now(ZoneOffset.UTC)` must land
     * close to UTC-now and, under the Africa/Kampala (UTC+3) test JVM asserted
     * by [assertTestJvmPinnedToNonUtcZone], far from local-now — the two differ
     * by three hours. If a call site regresses to the JVM-local
     * `LocalDateTime.now()`, `written` would land close to local-now instead and
     * this assertion catches it.
     */
    private fun assertWrittenInUtcNotLocalZone(written: LocalDateTime?) {
        requireNotNull(written) { "expected a respondedAt value to assert against" }
        val nowUtc = LocalDateTime.now(ZoneOffset.UTC)
        val nowLocal = LocalDateTime.now()
        assertTrue(
            Duration.between(written, nowUtc).abs() < Duration.ofMinutes(1),
            "expected $written to be within a minute of UTC now ($nowUtc)",
        )
        assertTrue(
            Duration.between(written, nowLocal).abs() > Duration.ofHours(1),
            "expected $written to be more than an hour from local now ($nowLocal) — " +
                "if this fails, a respondedAt write reverted to LocalDateTime.now()",
        )
    }

    @Test
    fun `accepting a proposal moves the requested date and confirms`() {
        val alice = user()
        val request = proposedRequest(alice)
        val proposedFor = LocalDateTime.now().plusDays(3)
        val proposal = ServiceRequestProposal(
            id = 99L, proposedDate = proposedFor, outcome = ProposalOutcome.OUTSTANDING,
        )
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(request))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }
        whenever(proposalRepo.findFirstByRequestIdAndOutcome(1L, ProposalOutcome.OUTSTANDING))
            .thenReturn(proposal)
        whenever(proposalRepo.save(any<ServiceRequestProposal>())).thenAnswer { it.arguments[0] as ServiceRequestProposal }

        val response = sut.acceptProposal(alice.email!!, 1L)

        assertEquals(ServiceRequestStatus.CONFIRMED, response.status)
        // The whole point of accepting: the booking moves to the offered time.
        assertEquals(proposedFor, request.requestedDate)
        assertEquals(ProposalOutcome.ACCEPTED, proposal.outcome)
        assertNotNull(proposal.respondedAt)
        verify(popularityPublisher).recomputeAndPublish(10L)
    }

    @Test
    fun `acceptProposal writes respondedAt in UTC for both the proposal and the request`() {
        assertTestJvmPinnedToNonUtcZone()
        val alice = user()
        val request = proposedRequest(alice)
        val proposal = ServiceRequestProposal(
            id = 99L, proposedDate = LocalDateTime.now().plusDays(3), outcome = ProposalOutcome.OUTSTANDING,
        )
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(request))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }
        whenever(proposalRepo.findFirstByRequestIdAndOutcome(1L, ProposalOutcome.OUTSTANDING))
            .thenReturn(proposal)
        whenever(proposalRepo.save(any<ServiceRequestProposal>())).thenAnswer { it.arguments[0] as ServiceRequestProposal }

        val response = sut.acceptProposal(alice.email!!, 1L)

        assertWrittenInUtcNotLocalZone(response.respondedAt)
        assertWrittenInUtcNotLocalZone(proposal.respondedAt)
    }

    @Test
    fun `accepting notifies the provider, not the customer who clicked`() {
        val alice = user()
        val request = proposedRequest(alice)
        val proposal = ServiceRequestProposal(
            id = 99L, proposedDate = LocalDateTime.now().plusDays(3), outcome = ProposalOutcome.OUTSTANDING,
        )
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(request))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }
        whenever(proposalRepo.findFirstByRequestIdAndOutcome(1L, ProposalOutcome.OUTSTANDING))
            .thenReturn(proposal)
        whenever(proposalRepo.save(any<ServiceRequestProposal>())).thenAnswer { it.arguments[0] as ServiceRequestProposal }

        sut.acceptProposal(alice.email!!, 1L)

        // CUSTOMER is what routes the email to the owner; PROVIDER here would mail
        // the person who just clicked the button.
        verify(publisher).statusChanged(
            any(),
            eq(ServiceRequestStatus.REVISION_REQUESTED),
            eq(RequestActor.CUSTOMER),
            eq(null),
            eq(null),
        )
    }

    @Test
    fun `accepting a proposal whose time has passed is refused`() {
        val alice = user()
        val request = proposedRequest(alice)
        val stale = ServiceRequestProposal(
            id = 99L,
            proposedDate = LocalDateTime.now().minusHours(2),
            outcome = ProposalOutcome.OUTSTANDING,
        )
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(request))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }
        whenever(proposalRepo.findFirstByRequestIdAndOutcome(1L, ProposalOutcome.OUTSTANDING))
            .thenReturn(stale)

        assertThrows(IllegalArgumentException::class.java) {
            sut.acceptProposal(alice.email!!, 1L)
        }
        assertEquals(ServiceRequestStatus.REVISION_REQUESTED, request.status)
        assertEquals(ProposalOutcome.OUTSTANDING, stale.outcome)
        verify(proposalRepo, never()).save(any<ServiceRequestProposal>())
    }

    @Test
    fun `rejecting a proposal declines the request and records the reason`() {
        val alice = user()
        val request = proposedRequest(alice)
        val proposal = ServiceRequestProposal(
            id = 99L,
            proposedDate = LocalDateTime.now().plusDays(3),
            outcome = ProposalOutcome.OUTSTANDING,
        )
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(request))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }
        whenever(proposalRepo.findFirstByRequestIdAndOutcome(1L, ProposalOutcome.OUTSTANDING))
            .thenReturn(proposal)
        whenever(proposalRepo.save(any<ServiceRequestProposal>())).thenAnswer { it.arguments[0] as ServiceRequestProposal }

        val response = sut.rejectProposal(alice.email!!, 1L, "  Too late in the week  ")

        assertEquals(ServiceRequestStatus.DECLINED, response.status)
        assertEquals(ProposalOutcome.REJECTED, proposal.outcome)
        assertEquals("Too late in the week", proposal.responseNote)
        // The provider's field must stay clear — it means "their reason" in the UI.
        assertNull(request.declineReason)
    }

    @Test
    fun `rejectProposal writes respondedAt in UTC for both the proposal and the request`() {
        assertTestJvmPinnedToNonUtcZone()
        val alice = user()
        val request = proposedRequest(alice)
        val proposal = ServiceRequestProposal(
            id = 99L,
            proposedDate = LocalDateTime.now().plusDays(3),
            outcome = ProposalOutcome.OUTSTANDING,
        )
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(request))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }
        whenever(proposalRepo.findFirstByRequestIdAndOutcome(1L, ProposalOutcome.OUTSTANDING))
            .thenReturn(proposal)
        whenever(proposalRepo.save(any<ServiceRequestProposal>())).thenAnswer { it.arguments[0] as ServiceRequestProposal }

        val response = sut.rejectProposal(alice.email!!, 1L, "Too late in the week")

        assertWrittenInUtcNotLocalZone(response.respondedAt)
        assertWrittenInUtcNotLocalZone(proposal.respondedAt)
    }

    @Test
    fun `rejecting a proposal whose time has passed is allowed`() {
        val alice = user()
        val request = proposedRequest(alice)
        val stale = ServiceRequestProposal(
            id = 99L,
            proposedDate = LocalDateTime.now().minusHours(2),
            outcome = ProposalOutcome.OUTSTANDING,
        )
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(request))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }
        whenever(proposalRepo.findFirstByRequestIdAndOutcome(1L, ProposalOutcome.OUTSTANDING))
            .thenReturn(stale)
        whenever(proposalRepo.save(any<ServiceRequestProposal>())).thenAnswer { it.arguments[0] as ServiceRequestProposal }

        // A stale offer must stay refusable — otherwise the only way out of
        // REVISION_REQUESTED once the date passes is cancelling.
        val response = sut.rejectProposal(alice.email!!, 1L, null)

        assertEquals(ServiceRequestStatus.DECLINED, response.status)
        assertNull(stale.responseNote)
    }

    @Test
    fun `responding when nothing is outstanding is refused`() {
        val alice = user()
        val request = proposedRequest(alice)
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(request))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }
        whenever(proposalRepo.findFirstByRequestIdAndOutcome(1L, ProposalOutcome.OUTSTANDING))
            .thenReturn(null)

        assertThrows(InvalidStateTransitionException::class.java) {
            sut.acceptProposal(alice.email!!, 1L)
        }
        assertEquals(ServiceRequestStatus.REVISION_REQUESTED, request.status)
    }

    @Test
    fun `responding to another customer's request throws 404`() {
        val alice = user(id = 1L, email = "alice@example.com")
        val bob = user(id = 2L, email = "bob@example.com")
        val request = proposedRequest(bob)
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(request))

        assertThrows(BusinessNotFoundException::class.java) {
            sut.acceptProposal(alice.email!!, 1L)
        }
        verify(proposalRepo, never()).save(any<ServiceRequestProposal>())
    }

    @Test
    fun `cancelling a proposed request settles the outstanding proposal`() {
        val alice = user()
        val request = proposedRequest(alice)
        val outstanding = ServiceRequestProposal(id = 99L, outcome = ProposalOutcome.OUTSTANDING)
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(request))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }
        whenever(proposalRepo.findFirstByRequestIdAndOutcome(1L, ProposalOutcome.OUTSTANDING))
            .thenReturn(outstanding)
        whenever(proposalRepo.save(any<ServiceRequestProposal>())).thenAnswer { it.arguments[0] as ServiceRequestProposal }

        val response = sut.cancel(alice.email!!, 1L, "Changed my mind")

        assertEquals(ServiceRequestStatus.CANCELLED, response.status)
        assertEquals(ProposalOutcome.REJECTED, outstanding.outcome)
        assertNotNull(outstanding.respondedAt)
    }

    @Test
    fun `cancel settles an outstanding proposal with a UTC, not local, respondedAt`() {
        assertTestJvmPinnedToNonUtcZone()
        val alice = user()
        val request = proposedRequest(alice)
        val outstanding = ServiceRequestProposal(id = 99L, outcome = ProposalOutcome.OUTSTANDING)
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(request))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }
        whenever(proposalRepo.findFirstByRequestIdAndOutcome(1L, ProposalOutcome.OUTSTANDING))
            .thenReturn(outstanding)
        whenever(proposalRepo.save(any<ServiceRequestProposal>())).thenAnswer { it.arguments[0] as ServiceRequestProposal }

        sut.cancel(alice.email!!, 1L, "Changed my mind")

        assertWrittenInUtcNotLocalZone(outstanding.respondedAt)
    }

    @Test
    fun `the response carries the latest proposal whatever its outcome`() {
        val alice = user()
        val request = proposedRequest(alice)
        val proposedFor = LocalDateTime.now().plusDays(3)
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(request))
        whenever(proposalRepo.findFirstByRequestIdOrderByProposedAtDesc(1L)).thenReturn(
            ServiceRequestProposal(
                id = 99L,
                proposedDate = proposedFor,
                note = "Fully booked Monday",
                proposedAt = LocalDateTime.now().minusHours(1),
                outcome = ProposalOutcome.OUTSTANDING,
            )
        )

        val response = sut.get(alice.email!!, 1L)

        assertEquals(proposedFor, response.proposal?.proposedDate)
        assertEquals("Fully booked Monday", response.proposal?.note)
        assertEquals(ProposalOutcome.OUTSTANDING, response.proposal?.outcome)
    }

    @Test
    fun `the response omits a proposal when none was ever made`() {
        val alice = user()
        val request = ServiceRequest(
            id = 1L, customer = alice, business = business(), status = ServiceRequestStatus.PENDING,
        )
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(request))
        whenever(proposalRepo.findFirstByRequestIdOrderByProposedAtDesc(1L)).thenReturn(null)

        assertNull(sut.get(alice.email!!, 1L).proposal)
    }

    // --- the completion prompt ---

    private fun confirmedRequest(alice: User, requestedDate: LocalDateTime) = ServiceRequest(
        id = 1L,
        customer = alice,
        business = business(),
        status = ServiceRequestStatus.CONFIRMED,
        requestedDate = requestedDate,
    )

    @Test
    fun `confirming completion marks the request COMPLETED`() {
        val alice = user()
        val request = confirmedRequest(alice, LocalDateTime.now().minusHours(30))
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(request))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }

        val response = sut.confirmCompletion(alice.email!!, 1L)

        assertEquals(ServiceRequestStatus.COMPLETED, response.status)
        assertNotNull(response.completedAt)
        verify(popularityPublisher).recomputeAndPublish(10L)
    }

    @Test
    fun `confirming completion notifies the provider, not the customer who clicked`() {
        val alice = user()
        val request = confirmedRequest(alice, LocalDateTime.now().minusHours(30))
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(request))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }

        sut.confirmCompletion(alice.email!!, 1L)

        // CUSTOMER is what routes the email to the owner; PROVIDER here would
        // send a review invitation to the person who just pressed the button.
        verify(publisher).statusChanged(
            any(),
            eq(ServiceRequestStatus.CONFIRMED),
            eq(RequestActor.CUSTOMER),
            eq(null),
            eq(null),
        )
    }

    @Test
    fun `reporting a no-show marks the request NO_SHOW and drops it from popularity`() {
        val alice = user()
        val request = confirmedRequest(alice, LocalDateTime.now().minusHours(30))
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(request))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }

        val response = sut.reportNoShow(alice.email!!, 1L)

        assertEquals(ServiceRequestStatus.NO_SHOW, response.status)
        // POPULAR_STATUSES is [CONFIRMED, COMPLETED]; leaving CONFIRMED is what
        // finally stops a booking that never happened inflating the ranking.
        verify(popularityPublisher).recomputeAndPublish(10L)
    }

    @Test
    fun `neither answer is accepted before the service time has passed`() {
        val alice = user()
        val request = confirmedRequest(alice, LocalDateTime.now().plusDays(1))
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(request))

        assertThrows(IllegalArgumentException::class.java) {
            sut.confirmCompletion(alice.email!!, 1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            sut.reportNoShow(alice.email!!, 1L)
        }
        assertEquals(ServiceRequestStatus.CONFIRMED, request.status)
    }

    @Test
    fun `neither answer is accepted inside the 24-hour grace window`() {
        // The provider is the party who profits from marking a job complete, so
        // the window exists to give their silence time to mean something.
        val alice = user()
        val request = confirmedRequest(alice, LocalDateTime.now().minusHours(23))
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(request))

        assertThrows(IllegalArgumentException::class.java) {
            sut.reportNoShow(alice.email!!, 1L)
        }
        assertEquals(ServiceRequestStatus.CONFIRMED, request.status)
    }

    @Test
    fun `a request that is not CONFIRMED is a state error, not a timing error`() {
        // Order matters: requireTransition runs first so a PENDING request answers
        // 409, not the 400 the window guard would give.
        val alice = user()
        val request = confirmedRequest(alice, LocalDateTime.now().minusHours(30))
            .apply { status = ServiceRequestStatus.PENDING }
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(request))

        assertThrows(InvalidStateTransitionException::class.java) {
            sut.reportNoShow(alice.email!!, 1L)
        }
    }

    @Test
    fun `answering another customer's request throws 404`() {
        val alice = user(id = 1L, email = "alice@example.com")
        val bob = user(id = 2L, email = "bob@example.com")
        val request = confirmedRequest(bob, LocalDateTime.now().minusHours(30))
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(request))

        assertThrows(BusinessNotFoundException::class.java) {
            sut.confirmCompletion(alice.email!!, 1L)
        }
    }

    // --- quantity and picks ---

    /** A catering package: quantity per guest, a required palette and a "choose exactly 3" juice group. */
    private inner class Package(biz: Business) {
        val catering = ServiceOffered(
            id = 300L, business = biz, title = "Catering", coverImageUrl = "https://cdn/cat.jpg",
            priceAmount = BigDecimal("20000.00"), priceCurrency = "UGX",
            priceIsEstimate = true, estimateReason = "Final count may change",
            quantityEnabled = true, quantityUnit = "guest", quantityMin = 10, quantityMax = 200,
        )
        val palette = OptionGroup(id = 31L, item = catering, name = "Palette", selection = GroupSelection.SINGLE, required = true, position = 0)
        val juices = OptionGroup(
            id = 32L, item = catering, name = "Juices", selection = GroupSelection.MULTIPLE,
            required = true, minPicks = 3, maxPicks = 3, position = 1,
        )
        val warmItem = ServiceOffered(id = 301L, business = biz, title = "Warm", priceAmount = null)
        val coolItem = ServiceOffered(id = 302L, business = biz, title = "Cool", priceAmount = null)
        val mangoItem = ServiceOffered(id = 303L, business = biz, title = "Mango", priceAmount = BigDecimal("3000.00"), priceCurrency = "UGX")
        val passionItem = ServiceOffered(id = 304L, business = biz, title = "Passion", priceAmount = BigDecimal("2500.00"), priceCurrency = "UGX")
        val warm = GroupChoice(id = 401L, group = palette, item = warmItem, position = 0)
        val cool = GroupChoice(id = 402L, group = palette, item = coolItem, position = 1)
        val mango = GroupChoice(id = 403L, group = juices, item = mangoItem, quantityEnabled = true, maxQuantity = 3, position = 0)
        // Group price overrides the linked item's own price.
        val passion = GroupChoice(
            id = 404L, group = juices, item = passionItem, priceOverride = BigDecimal("2000.00"),
            quantityEnabled = true, maxQuantity = 3, position = 1,
        )

        init {
            palette.choices.addAll(listOf(warm, cool))
            juices.choices.addAll(listOf(mango, passion))
            catering.groups.addAll(listOf(palette, juices))
        }

        val validPicks = listOf(PickInput(warm.id!!), PickInput(mango.id!!, 2), PickInput(passion.id!!, 1))
    }

    private fun draftWith(alice: User, biz: Business, pkg: Package, picks: Boolean = true): ServiceRequest {
        val req = ServiceRequest(
            id = 1L, customer = alice, business = biz, status = ServiceRequestStatus.DRAFT,
            requestedDate = LocalDateTime.now().plusDays(3), serviceLocation = "Kampala",
        )
        val item = ServiceRequestItem(request = req, service = pkg.catering, position = 0, quantity = 40)
        if (picks) {
            listOf(Triple(pkg.warm, 1, 0), Triple(pkg.mango, 2, 1), Triple(pkg.passion, 1, 2)).forEach { (c, q, pos) ->
                item.picks.add(ServiceRequestItemPick(item = item, choice = c, groupId = c.group!!.id, quantity = q, position = pos))
            }
        }
        req.items.add(item)
        return req
    }

    @Test
    fun `draft stores item quantity and picks`() {
        val alice = user()
        val biz = business()
        val pkg = Package(biz)
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(businessRepo.findById(biz.id!!)).thenReturn(Optional.of(biz))
        whenever(serviceRepo.findAllById(listOf(300L))).thenReturn(listOf(pkg.catering))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }

        val response = sut.create(
            alice.email!!,
            CreateRequestPayload(
                businessId = biz.id,
                items = listOf(RequestItemInput(serviceId = 300L, quantity = 40, picks = pkg.validPicks)),
            ),
        )

        val saved = argumentCaptor<ServiceRequest>().also { verify(repo).save(it.capture()) }.firstValue
        val item = saved.items.single()
        assertEquals(40, item.quantity)
        assertEquals(listOf(401L, 403L, 404L), item.picks.map { it.choice?.id })
        assertEquals(listOf(1, 2, 1), item.picks.map { it.quantity })
        assertEquals(listOf(31L, 32L, 32L), item.picks.map { it.groupId })

        val dto = response.items.single()
        assertEquals(40, dto.quantity)
        assertEquals("guest", dto.quantityUnit)
        assertTrue(dto.priceIsEstimate)
        assertEquals("Final count may change", dto.estimateReason)
        assertEquals(listOf("Warm", "Mango", "Passion"), dto.picks.map { it.title })
        assertEquals(listOf("Palette", "Juices", "Juices"), dto.picks.map { it.groupName })
        assertEquals(listOf(null, BigDecimal("3000.00"), BigDecimal("2000.00")), dto.picks.map { it.price })
        assertEquals(PricingState.ESTIMATED, response.pricing)
        // 20000 x 40 + 3000 x 2 + 2000 x 1
        assertEquals(0, BigDecimal("808000.00").compareTo(response.total))
    }

    @Test
    fun `draft refuses an incomplete selection, naming the group`() {
        val alice = user()
        val biz = business()
        val pkg = Package(biz)
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(businessRepo.findById(biz.id!!)).thenReturn(Optional.of(biz))
        whenever(serviceRepo.findAllById(listOf(300L))).thenReturn(listOf(pkg.catering))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            sut.create(
                alice.email!!,
                CreateRequestPayload(
                    businessId = biz.id,
                    items = listOf(RequestItemInput(serviceId = 300L, quantity = 40, picks = listOf(PickInput(pkg.warm.id!!)))),
                ),
            )
        }
        assertEquals("Juices: choose exactly 3", ex.message)
    }

    @Test
    fun `patching items keeps surviving picks in place instead of re-creating them`() {
        val alice = user()
        val biz = business()
        val pkg = Package(biz)
        val req = draftWith(alice, biz, pkg)
        val item = req.items.single()
        val warmPick = item.picks[0]
        val mangoPick = item.picks[1]
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(req))
        whenever(serviceRepo.findAllById(listOf(300L))).thenReturn(listOf(pkg.catering))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }

        sut.patch(
            alice.email!!, 1L,
            UpdateRequestPayload(
                items = listOf(
                    RequestItemInput(
                        serviceId = 300L, quantity = 50,
                        picks = listOf(PickInput(pkg.cool.id!!), PickInput(pkg.mango.id!!, 3)),
                    ),
                ),
            ),
        )

        assertTrue(req.items.single() === item, "the item row is kept")
        assertEquals(50, item.quantity)
        assertEquals(listOf(402L, 403L), item.picks.map { it.choice?.id })
        assertTrue(item.picks.none { it === warmPick }, "dropped pick is removed")
        assertTrue(item.picks[1] === mangoPick, "kept pick is updated in place")
        assertEquals(3, mangoPick.quantity)
        assertEquals(1, mangoPick.position)
    }

    @Test
    fun `submit snapshots item estimate and each pick's group, title and effective price`() {
        val alice = user()
        val biz = business()
        val pkg = Package(biz)
        val req = draftWith(alice, biz, pkg)
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(req))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }

        val response = sut.submit(alice.email!!, 1L)

        val item = req.items.single()
        assertTrue(item.snapshotPriceIsEstimate)
        assertEquals("Final count may change", item.snapshotEstimateReason)
        assertEquals("guest", item.snapshotQuantityUnit)
        assertEquals(40, item.quantity)
        assertEquals(listOf("Palette", "Juices", "Juices"), item.picks.map { it.snapshotGroupName })
        assertEquals(listOf("Warm", "Mango", "Passion"), item.picks.map { it.snapshotTitle })
        assertEquals(listOf(null, BigDecimal("3000.00"), BigDecimal("2000.00")), item.picks.map { it.snapshotPrice })
        assertEquals(ServiceRequestStatus.PENDING, response.status)
        assertEquals(PricingState.ESTIMATED, response.pricing)
    }

    @Test
    fun `submit refuses when a required group's pick was paused, naming the group`() {
        val alice = user()
        val biz = business()
        val pkg = Package(biz)
        val req = draftWith(alice, biz, pkg)
        pkg.warmItem.status = ServiceOfferedStatus.INACTIVE
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(req))

        val ex = assertThrows(IllegalArgumentException::class.java) { sut.submit(alice.email!!, 1L) }

        assertEquals("Palette: this choice is no longer available", ex.message)
        assertEquals(ServiceRequestStatus.DRAFT, req.status)
        verify(repo, never()).save(any<ServiceRequest>())
    }

    @Test
    fun `submit refuses when a picked choice was deleted, naming its group`() {
        val alice = user()
        val biz = business()
        val pkg = Package(biz)
        val req = draftWith(alice, biz, pkg)
        // GroupChoicesDeletionListener nulled the pointer; the choice is gone from the group.
        req.items.single().picks[0].choice = null
        pkg.palette.choices.remove(pkg.warm)
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(req))

        val ex = assertThrows(IllegalArgumentException::class.java) { sut.submit(alice.email!!, 1L) }

        assertEquals("Palette: this choice is no longer available", ex.message)
    }

    @Test
    fun `renaming a choice after submit leaves the snapshot`() {
        val alice = user()
        val biz = business()
        val pkg = Package(biz)
        val req = draftWith(alice, biz, pkg)
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(req))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }
        sut.submit(alice.email!!, 1L)

        pkg.mangoItem.title = "Mango Deluxe"
        pkg.passion.priceOverride = BigDecimal("9999.00")
        pkg.juices.name = "Fresh juices"

        val pick = sut.get(alice.email!!, 1L).items.single().picks
        assertEquals(listOf("Warm", "Mango", "Passion"), pick.map { it.title })
        assertEquals("Juices", pick[2].groupName)
        assertEquals(BigDecimal("2000.00"), pick[2].price)
    }

    @Test
    fun `duplicate copies picks still available and drops the rest`() {
        val alice = user()
        val biz = business()
        val pkg = Package(biz)
        val original = draftWith(alice, biz, pkg).apply { status = ServiceRequestStatus.COMPLETED }
        pkg.passionItem.status = ServiceOfferedStatus.INACTIVE          // paused
        original.items.single().picks[0].choice = null                  // deleted
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(original))
        whenever(serviceRepo.findAllById(listOf(300L))).thenReturn(listOf(pkg.catering))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }

        val response = sut.duplicate(alice.email!!, 1L)

        val copy = response.request.items.single()
        assertEquals(40, copy.quantity)
        assertEquals(listOf("Mango"), copy.picks.map { it.title })
        assertEquals(listOf(2), copy.picks.map { it.quantity })
        assertEquals(listOf(403L), copy.picks.map { it.choiceId })
    }

    @Test
    fun `pricing an unpriced item after submit leaves the sent request to be confirmed`() {
        val alice = user()
        val biz = business()
        val svc = service(id = 100L, biz = biz).apply { priceAmount = null; priceCurrency = null; coverImageUrl = null }
        val req = ServiceRequest(
            id = 1L, customer = alice, business = biz, status = ServiceRequestStatus.DRAFT,
            requestedDate = LocalDateTime.now().plusDays(3), serviceLocation = "Kampala",
        )
        req.items.add(ServiceRequestItem(request = req, service = svc, position = 0))
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(req))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }
        val sent = sut.submit(alice.email!!, 1L)
        assertEquals(PricingState.TO_BE_CONFIRMED, sent.pricing)

        // The provider prices the item (and adds a cover) in the catalogue afterwards.
        svc.priceAmount = BigDecimal("300000.00")
        svc.priceCurrency = "UGX"
        svc.coverImageUrl = "https://cdn/new.jpg"

        val after = sut.get(alice.email!!, 1L)
        assertEquals(PricingState.TO_BE_CONFIRMED, after.pricing)
        assertNull(after.total)
        assertNull(after.items.single().priceAmount)
        assertNull(after.items.single().priceCurrency)
        assertNull(after.items.single().coverImageUrl)
    }

    // --- price proposals (Task 7) ---

    /** Alice's CONFIRMED booking, job time 30h ago (prompt window open), with one snapshotted item. */
    private fun bookedWith(alice: User, price: String?, estimate: Boolean): ServiceRequest {
        val r = confirmedRequest(alice, LocalDateTime.now().minusHours(30))
        r.items.add(
            ServiceRequestItem(
                request = r, position = 0, quantity = 1, snapshotTitle = "Venue dressing",
                snapshotPriceAmount = price?.let { BigDecimal(it) },
                snapshotPriceCurrency = price?.let { "UGX" },
                snapshotPriceIsEstimate = estimate,
            ),
        )
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        whenever(repo.findById(1L)).thenReturn(Optional.of(r))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }
        whenever(priceProposalRepo.save(any<ServiceRequestPriceProposal>()))
            .thenAnswer { it.arguments[0] as ServiceRequestPriceProposal }
        return r
    }

    private fun outstandingPrice(amount: String = "4200000.00", note: String? = "after visiting the venue") =
        ServiceRequestPriceProposal(
            id = 7L, amount = BigDecimal(amount), currency = "UGX", note = note,
            proposedAt = LocalDateTime.now(ZoneOffset.UTC).minusHours(1), outcome = ProposalOutcome.OUTSTANDING,
        ).also {
            whenever(priceProposalRepo.findAllByRequestIdAndOutcome(1L, ProposalOutcome.OUTSTANDING)).thenReturn(listOf(it))
            whenever(priceProposalRepo.findFirstByRequestIdOrderByProposedAtDescIdDesc(1L)).thenReturn(it)
        }

    @Test
    fun `accepting sets the agreed price`() {
        assertTestJvmPinnedToNonUtcZone()
        val alice = user()
        val r = bookedWith(alice, null, estimate = false)
        val proposal = outstandingPrice()

        val response = sut.acceptPrice(alice.email!!, 1L, 7L)

        assertEquals(ProposalOutcome.ACCEPTED, proposal.outcome)
        assertWrittenInUtcNotLocalZone(proposal.respondedAt)
        assertEquals(0, BigDecimal("4200000.00").compareTo(r.agreedPriceAmount))
        assertEquals("UGX", r.agreedPriceCurrency)
        assertEquals("after visiting the venue", r.agreedPriceNote)
        assertEquals(ServiceRequestStatus.CONFIRMED, response.status)
        assertEquals(0, BigDecimal("4200000.00").compareTo(response.agreedPrice!!.amount))
        assertEquals("UGX", response.agreedPrice!!.currency)
        assertEquals("after visiting the venue", response.agreedPrice!!.note)
        assertNull(response.total)
        assertTrue(response.priceSettled)
        assertEquals(ProposalOutcome.ACCEPTED, response.priceProposal?.outcome)
        verify(publisher).priceChanged(any(), eq(PriceEventKind.ACCEPTED), eq(proposal))
    }

    @Test
    fun `accept with nothing outstanding is 409`() {
        val alice = user()
        val r = bookedWith(alice, null, estimate = false)

        assertThrows(InvalidStateTransitionException::class.java) { sut.acceptPrice(alice.email!!, 1L, 7L) }
        assertNull(r.agreedPriceAmount)
    }

    @Test
    fun `reject with nothing outstanding is 409`() {
        val alice = user()
        val r = bookedWith(alice, null, estimate = false)

        assertThrows(InvalidStateTransitionException::class.java) { sut.rejectPrice(alice.email!!, 1L, 7L, "No") }
        assertEquals(ServiceRequestStatus.CONFIRMED, r.status)
    }

    @Test
    fun `price responses on another customer's request throw 404`() {
        val alice = user(id = 1L, email = "alice@example.com")
        val bob = user(id = 2L, email = "bob@example.com")
        bookedWith(bob, null, estimate = false)
        whenever(userRepo.findByEmail(alice.email!!)).thenReturn(Optional.of(alice))
        outstandingPrice()

        assertThrows(BusinessNotFoundException::class.java) { sut.acceptPrice(alice.email!!, 1L, 7L) }
        assertThrows(BusinessNotFoundException::class.java) { sut.rejectPrice(alice.email!!, 1L, 7L, null) }
    }

    @Test
    fun `rejecting cancels with the reason and notifies via status change`() {
        assertTestJvmPinnedToNonUtcZone()
        val alice = user()
        val r = bookedWith(alice, "3500000.00", estimate = true)
        val proposal = outstandingPrice()

        val response = sut.rejectPrice(alice.email!!, 1L, 7L, "  Too expensive  ")

        assertEquals(ServiceRequestStatus.CANCELLED, response.status)
        assertEquals("Too expensive", r.cancellationReason)
        assertNotNull(r.cancelledAt)
        assertNull(r.agreedPriceAmount)
        assertEquals(ProposalOutcome.REJECTED, proposal.outcome)
        assertEquals("Too expensive", proposal.responseNote)
        assertWrittenInUtcNotLocalZone(proposal.respondedAt)
        verify(publisher).statusChanged(
            any(), eq(ServiceRequestStatus.CONFIRMED), eq(RequestActor.CUSTOMER), eq("Too expensive"), eq(null),
        )
        verify(popularityPublisher).recomputeAndPublish(10L)
        verify(publisher, never()).priceChanged(any(), any(), any())
    }

    @Test
    fun `customer cancel settles an outstanding price proposal`() {
        assertTestJvmPinnedToNonUtcZone()
        val alice = user()
        bookedWith(alice, null, estimate = false)
        val proposal = outstandingPrice()

        sut.cancel(alice.email!!, 1L, "Plans changed")

        assertEquals(ProposalOutcome.REJECTED, proposal.outcome)
        assertWrittenInUtcNotLocalZone(proposal.respondedAt)
        // The customer's cancellation reason is not an answer to the price.
        assertNull(proposal.responseNote)
    }

    @Test
    fun `confirm-completion and no-show are refused while unsettled`() {
        val alice = user()
        val r = bookedWith(alice, "3500000.00", estimate = true)
        outstandingPrice()

        assertThrows(PriceNotSettledException::class.java) { sut.confirmCompletion(alice.email!!, 1L) }
        assertThrows(PriceNotSettledException::class.java) { sut.reportNoShow(alice.email!!, 1L) }
        assertEquals(ServiceRequestStatus.CONFIRMED, r.status)
    }

    @Test
    fun `confirm-completion and no-show are refused for a to-be-confirmed booking with no agreed price`() {
        val alice = user()
        bookedWith(alice, null, estimate = false)

        assertThrows(PriceNotSettledException::class.java) { sut.confirmCompletion(alice.email!!, 1L) }
        assertThrows(PriceNotSettledException::class.java) { sut.reportNoShow(alice.email!!, 1L) }
    }

    @Test
    fun `the response reports an unanswered price as unsettled`() {
        val alice = user()
        bookedWith(alice, "3500000.00", estimate = true)
        outstandingPrice()

        val response = sut.get(alice.email!!, 1L)

        assertEquals(false, response.priceSettled)
        assertEquals(ProposalOutcome.OUTSTANDING, response.priceProposal?.outcome)
        assertEquals(0, BigDecimal("4200000.00").compareTo(response.priceProposal!!.amount))
        // The estimate stays the visible total until the new price is accepted.
        assertEquals(0, BigDecimal("3500000.00").compareTo(response.total))
        assertNull(response.agreedPrice)
    }

    @Test
    fun `accept naming a superseded proposal is 409 and changes nothing`() {
        val alice = user()
        val r = bookedWith(alice, null, estimate = false)
        val current = outstandingPrice()

        val ex = assertThrows(InvalidStateTransitionException::class.java) { sut.acceptPrice(alice.email!!, 1L, 6L) }

        assertTrue(ex.message!!.contains("replaced"))
        assertEquals(ProposalOutcome.OUTSTANDING, current.outcome)
        assertNull(r.agreedPriceAmount)
        verify(priceProposalRepo, never()).save(any<ServiceRequestPriceProposal>())
        verify(repo, never()).save(any<ServiceRequest>())
        verify(publisher, never()).priceChanged(any(), any(), any())
    }

    @Test
    fun `reject naming a superseded proposal is 409 and changes nothing`() {
        val alice = user()
        val r = bookedWith(alice, null, estimate = false)
        val current = outstandingPrice()

        assertThrows(InvalidStateTransitionException::class.java) { sut.rejectPrice(alice.email!!, 1L, 6L, "No") }

        assertEquals(ServiceRequestStatus.CONFIRMED, r.status)
        assertEquals(ProposalOutcome.OUTSTANDING, current.outcome)
        verify(priceProposalRepo, never()).save(any<ServiceRequestPriceProposal>())
    }

    @Test
    fun `cancel settles every outstanding price row`() {
        val alice = user()
        bookedWith(alice, null, estimate = false)
        val rows = listOf(5L, 6L).map {
            ServiceRequestPriceProposal(id = it, amount = BigDecimal("1"), currency = "UGX", outcome = ProposalOutcome.OUTSTANDING)
        }
        whenever(priceProposalRepo.findAllByRequestIdAndOutcome(1L, ProposalOutcome.OUTSTANDING)).thenReturn(rows)

        sut.cancel(alice.email!!, 1L, null)

        rows.forEach { assertEquals(ProposalOutcome.REJECTED, it.outcome) }
    }

    @Test
    fun `price answers, completion answers and cancel load the request with a row lock`() {
        val alice = user()
        bookedWith(alice, "3500000.00", estimate = true)

        sut.confirmCompletion(alice.email!!, 1L)
        bookedWith(alice, "3500000.00", estimate = true)
        sut.reportNoShow(alice.email!!, 1L)
        bookedWith(alice, "3500000.00", estimate = true)
        outstandingPrice()
        sut.acceptPrice(alice.email!!, 1L, 7L)
        bookedWith(alice, "3500000.00", estimate = true)
        outstandingPrice()
        sut.rejectPrice(alice.email!!, 1L, 7L, null)
        bookedWith(alice, "3500000.00", estimate = true)
        sut.cancel(alice.email!!, 1L, null)

        verify(repo, org.mockito.kotlin.times(5)).findWithLockById(1L)
    }
}
