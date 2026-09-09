package com.mudhut.nudge.servicerequests.services

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.entities.BusinessCategory
import com.mudhut.nudge.businesses.entities.BusinessStatus
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.servicerequests.entities.ProposalOutcome
import com.mudhut.nudge.servicerequests.entities.ServiceRequest
import com.mudhut.nudge.servicerequests.entities.ServiceRequestProposal
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.events.RequestActor
import com.mudhut.nudge.servicerequests.models.AttachmentInput
import com.mudhut.nudge.servicerequests.models.CreateRequestPayload
import com.mudhut.nudge.servicerequests.models.RequestItemInput
import com.mudhut.nudge.servicerequests.models.UpdateRequestPayload
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestProposalRepository
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestRepository
import com.mudhut.nudge.servicesoffered.entities.PriceMode
import com.mudhut.nudge.servicesoffered.entities.ServiceOffered
import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedStatus
import com.mudhut.nudge.servicesoffered.repositories.ServiceOfferedRepository
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.entities.UserRole
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.utils.exceptions.BusinessNotFoundException
import com.mudhut.nudge.utils.exceptions.InvalidStateTransitionException
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
import java.time.LocalDateTime
import java.util.Optional

class ServiceRequestServiceTest {

    private val repo: ServiceRequestRepository = mock()
    private val userRepo: UserRepository = mock()
    private val businessRepo: BusinessRepository = mock()
    private val serviceRepo: ServiceOfferedRepository = mock()
    private val addonRepo: com.mudhut.nudge.servicesoffered.repositories.ServiceAddonRepository = mock()
    private val publisher: ServiceRequestEventPublisher = mock()
    private val popularityPublisher: RequestPopularityPublisher = mock()
    private val proposalRepo: ServiceRequestProposalRepository = mock()

    private val sut = ServiceRequestService(
        repo, userRepo, businessRepo, serviceRepo, addonRepo, publisher, popularityPublisher, proposalRepo,
    )

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
            priceMode = PriceMode.FIXED,
            priceAmount = BigDecimal("250000.00"),
            priceCurrency = "UGX",
            priceUnit = null,
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
}
