package com.mudhut.nudge.servicerequests.services

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.entities.BusinessCategory
import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.entities.BusinessStatus
import com.mudhut.nudge.businesses.services.BusinessService
import com.mudhut.nudge.servicerequests.entities.PricingState
import com.mudhut.nudge.servicerequests.entities.ProposalOutcome
import com.mudhut.nudge.servicerequests.entities.ServiceRequest
import com.mudhut.nudge.servicerequests.entities.ServiceRequestItem
import com.mudhut.nudge.servicerequests.entities.ServiceRequestItemPick
import com.mudhut.nudge.servicerequests.entities.ServiceRequestPriceProposal
import com.mudhut.nudge.servicerequests.entities.ServiceRequestProposal
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.events.PriceEventKind
import com.mudhut.nudge.servicerequests.models.ItemPickResponse
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestPriceProposalRepository
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestProposalRepository
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestRepository
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.entities.UserRole
import com.mudhut.nudge.utils.exceptions.InvalidStateTransitionException
import com.mudhut.nudge.utils.exceptions.PriceNotSettledException
import com.mudhut.nudge.utils.exceptions.QuotaExceededException
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
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Optional

class ProviderRequestServiceTest {

    private val repo: ServiceRequestRepository = mock()
    private val businessService: BusinessService = mock()
    private val popularityPublisher: RequestPopularityPublisher = mock()
    private val eventPublisher: ServiceRequestEventPublisher = mock()
    private val proposalRepo: ServiceRequestProposalRepository = mock()
    private val quotaService: JobQuotaService = mock()
    private val priceProposalRepo: ServiceRequestPriceProposalRepository = mock()
    private val sut = ProviderRequestService(
        repo, businessService, popularityPublisher, eventPublisher, proposalRepo, quotaService, priceProposalRepo,
    )

    private fun biz(id: Long = 10L) = Business(
        id = id,
        name = "SparkleClean",
        category = BusinessCategory(id = 1L, name = "Cleaning"),
        phoneNumbers = mutableListOf(),
        email = "biz@example.com",
        serviceAreas = mutableListOf("Kampala"),
        status = BusinessStatus.ACTIVE,
        owner = owner(),
    )

    // The business owner, whose allowance quotaService checks. Distinct from
    // the customer below, which this fixture previously didn't bother
    // distinguishing since nothing depended on it before quota enforcement.
    private fun owner() = User(
        id = 2L, username = "Owner", email = "owner@example.com",
        phoneNumber = null, password = "x", role = UserRole.BASIC_USER, isActive = true,
    )

    private fun user() = User(
        id = 1L, username = "Customer", email = "customer@example.com",
        phoneNumber = null, password = "x", role = UserRole.BASIC_USER, isActive = true,
    )

    private fun req(status: ServiceRequestStatus, viewedAt: LocalDateTime? = null) = ServiceRequest(
        id = 100L,
        customer = user(),
        business = biz(),
        status = status,
        viewedAt = viewedAt,
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
    fun `list filters drafts at the DB layer`() {
        whenever(repo.findAllByBusinessExcludingDrafts(eq(10L), any())).thenReturn(PageImpl(listOf(req(ServiceRequestStatus.PENDING))))
        val page = sut.list("owner@example.com", 10L, status = null, pageable = Pageable.ofSize(20))

        verify(businessService).requireRole(10L, "owner@example.com", BusinessRole.MANAGER)
        assertEquals(1, page.totalElements)
    }

    @Test
    fun `unread-count delegates to repo`() {
        whenever(repo.countUnreadByBusiness(10L)).thenReturn(3L)
        val count = sut.unreadCount("owner@example.com", 10L)
        verify(businessService).requireRole(10L, "owner@example.com", BusinessRole.MANAGER)
        assertEquals(3L, count)
    }

    @Test
    fun `get sets viewedAt when PENDING and viewedAt null`() {
        val r = req(ServiceRequestStatus.PENDING, viewedAt = null)
        whenever(repo.findById(100L)).thenReturn(Optional.of(r))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }

        val response = sut.get("owner@example.com", 10L, 100L)
        assertNotNull(response.viewedAt)
    }

    @Test
    fun `get does not change viewedAt when already set`() {
        val now = LocalDateTime.now()
        val r = req(ServiceRequestStatus.PENDING, viewedAt = now.minusHours(1))
        whenever(repo.findById(100L)).thenReturn(Optional.of(r))

        val response = sut.get("owner@example.com", 10L, 100L)
        assertEquals(r.viewedAt, response.viewedAt)
    }

    @Test
    fun `get does not set viewedAt for non-PENDING`() {
        val r = req(ServiceRequestStatus.CONFIRMED, viewedAt = null)
        whenever(repo.findById(100L)).thenReturn(Optional.of(r))

        val response = sut.get("owner@example.com", 10L, 100L)
        assertEquals(null, response.viewedAt)
    }

    @Test
    fun `accept transitions PENDING to CONFIRMED`() {
        val r = req(ServiceRequestStatus.PENDING)
        whenever(repo.findById(100L)).thenReturn(Optional.of(r))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }

        val response = sut.accept("owner@example.com", 10L, 100L)
        assertEquals(ServiceRequestStatus.CONFIRMED, response.status)
        assertNotNull(response.respondedAt)
        verify(popularityPublisher).recomputeAndPublish(10L)
    }

    @Test
    fun `accept writes respondedAt in UTC, not the JVM-local zone`() {
        assertTestJvmPinnedToNonUtcZone()
        val r = req(ServiceRequestStatus.PENDING)
        whenever(repo.findById(100L)).thenReturn(Optional.of(r))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }

        val response = sut.accept("owner@example.com", 10L, 100L)

        assertWrittenInUtcNotLocalZone(response.respondedAt)
    }

    @Test
    fun `decline transitions PENDING to DECLINED`() {
        val r = req(ServiceRequestStatus.PENDING)
        whenever(repo.findById(100L)).thenReturn(Optional.of(r))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }

        val response = sut.decline("owner@example.com", 10L, 100L, reason = "Too busy")
        assertEquals(ServiceRequestStatus.DECLINED, response.status)
        assertNotNull(response.respondedAt)
        // This test always passed a reason; nothing asserted it survived, and it
        // did not — `decline` discarded it via an `ignoredReason` local.
        assertEquals("Too busy", response.declineReason)
    }

    @Test
    fun `decline normalises a blank reason to null`() {
        val r = req(ServiceRequestStatus.PENDING)
        whenever(repo.findById(100L)).thenReturn(Optional.of(r))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }

        val response = sut.decline("owner@example.com", 10L, 100L, reason = "   ")
        assertNull(response.declineReason)
    }

    @Test
    fun `complete transitions CONFIRMED to COMPLETED when requestedDate has passed`() {
        val r = req(ServiceRequestStatus.CONFIRMED)
        r.requestedDate = LocalDateTime.now().minusDays(1)
        whenever(repo.findById(100L)).thenReturn(Optional.of(r))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }

        val response = sut.complete("owner@example.com", 10L, 100L)
        assertEquals(ServiceRequestStatus.COMPLETED, response.status)
        assertNotNull(response.completedAt)
        verify(popularityPublisher).recomputeAndPublish(10L)
    }

    @Test
    fun `complete is rejected when requestedDate has not passed`() {
        val r = req(ServiceRequestStatus.CONFIRMED)
        r.requestedDate = LocalDateTime.now().plusDays(1)
        whenever(repo.findById(100L)).thenReturn(Optional.of(r))

        assertThrows(IllegalStateException::class.java) {
            sut.complete("owner@example.com", 10L, 100L)
        }
    }

    @Test
    fun `complete from PENDING throws InvalidStateTransition`() {
        val r = req(ServiceRequestStatus.PENDING)
        r.requestedDate = LocalDateTime.now().minusDays(1)
        whenever(repo.findById(100L)).thenReturn(Optional.of(r))

        assertThrows(InvalidStateTransitionException::class.java) {
            sut.complete("owner@example.com", 10L, 100L)
        }
    }

    @Test
    fun `calendar queries CONFIRMED and COMPLETED within the half-open window`() {
        whenever(repo.findCalendarJobs(any(), any(), any(), any())).thenReturn(emptyList())

        sut.calendar("owner@example.com", 10L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1))

        verify(businessService).requireRole(10L, "owner@example.com", BusinessRole.MANAGER)
        verify(repo).findCalendarJobs(
            eq(10L),
            eq(listOf(ServiceRequestStatus.CONFIRMED, ServiceRequestStatus.COMPLETED)),
            eq(LocalDateTime.of(2026, 3, 1, 0, 0)),
            eq(LocalDateTime.of(2026, 4, 1, 0, 0)),
        )
    }

    @Test
    fun `calendar rejects an inverted or empty window`() {
        assertThrows(IllegalArgumentException::class.java) {
            sut.calendar("owner@example.com", 10L, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 1))
        }
    }

    @Test
    fun `calendar rejects a window longer than 92 days`() {
        assertThrows(IllegalArgumentException::class.java) {
            sut.calendar("owner@example.com", 10L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1))
        }
    }

    @Test
    fun `propose stores the offered time and moves the request to REVISION_REQUESTED`() {
        val r = req(ServiceRequestStatus.PENDING)
        whenever(repo.findById(100L)).thenReturn(Optional.of(r))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }
        whenever(proposalRepo.findFirstByRequestIdAndOutcome(100L, ProposalOutcome.OUTSTANDING))
            .thenReturn(null)
        whenever(proposalRepo.save(any<ServiceRequestProposal>())).thenAnswer { it.arguments[0] as ServiceRequestProposal }

        val offered = LocalDateTime.now().plusDays(3)
        val response = sut.propose("owner@example.com", 10L, 100L, offered, "  Fully booked Monday  ")

        assertEquals(ServiceRequestStatus.REVISION_REQUESTED, response.status)
        assertNotNull(response.respondedAt)
        val saved = argumentCaptor<ServiceRequestProposal>()
        verify(proposalRepo).save(saved.capture())
        assertEquals(offered, saved.firstValue.proposedDate)
        assertEquals("Fully booked Monday", saved.firstValue.note)
        assertEquals(ProposalOutcome.OUTSTANDING, saved.firstValue.outcome)
        assertNotNull(saved.firstValue.proposedAt)
    }

    @Test
    fun `propose writes respondedAt in UTC, not the JVM-local zone`() {
        assertTestJvmPinnedToNonUtcZone()
        val r = req(ServiceRequestStatus.PENDING)
        whenever(repo.findById(100L)).thenReturn(Optional.of(r))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }
        whenever(proposalRepo.findFirstByRequestIdAndOutcome(100L, ProposalOutcome.OUTSTANDING))
            .thenReturn(null)
        whenever(proposalRepo.save(any<ServiceRequestProposal>())).thenAnswer { it.arguments[0] as ServiceRequestProposal }

        val response = sut.propose("owner@example.com", 10L, 100L, LocalDateTime.now().plusDays(3), null)

        assertWrittenInUtcNotLocalZone(response.respondedAt)
    }

    @Test
    fun `propose normalises a blank note to null`() {
        val r = req(ServiceRequestStatus.PENDING)
        whenever(repo.findById(100L)).thenReturn(Optional.of(r))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }
        whenever(proposalRepo.findFirstByRequestIdAndOutcome(100L, ProposalOutcome.OUTSTANDING))
            .thenReturn(null)
        whenever(proposalRepo.save(any<ServiceRequestProposal>())).thenAnswer { it.arguments[0] as ServiceRequestProposal }

        sut.propose("owner@example.com", 10L, 100L, LocalDateTime.now().plusDays(3), "   ")

        val saved = argumentCaptor<ServiceRequestProposal>()
        verify(proposalRepo).save(saved.capture())
        assertNull(saved.firstValue.note)
    }

    @Test
    fun `propose refuses a time in the past`() {
        val r = req(ServiceRequestStatus.PENDING)
        whenever(repo.findById(100L)).thenReturn(Optional.of(r))

        assertThrows(IllegalArgumentException::class.java) {
            sut.propose("owner@example.com", 10L, 100L, LocalDateTime.now().minusHours(1), null)
        }
        verify(proposalRepo, never()).save(any<ServiceRequestProposal>())
        assertEquals(ServiceRequestStatus.PENDING, r.status)
    }

    @Test
    fun `propose refuses when a proposal is already outstanding`() {
        val r = req(ServiceRequestStatus.PENDING)
        whenever(repo.findById(100L)).thenReturn(Optional.of(r))
        // Stubbed so that deleting the guard lets propose run to completion and
        // this fails with "nothing was thrown" — without it the mutant dies on an
        // incidental NPE from the unstubbed save, which proves nothing.
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }
        whenever(proposalRepo.findFirstByRequestIdAndOutcome(100L, ProposalOutcome.OUTSTANDING))
            .thenReturn(ServiceRequestProposal(id = 99L))

        assertThrows(IllegalStateException::class.java) {
            sut.propose("owner@example.com", 10L, 100L, LocalDateTime.now().plusDays(3), null)
        }
        verify(proposalRepo, never()).save(any<ServiceRequestProposal>())
        assertEquals(ServiceRequestStatus.PENDING, r.status)
    }

    @Test
    fun `propose refuses a request that is not pending`() {
        val r = req(ServiceRequestStatus.CONFIRMED)
        whenever(repo.findById(100L)).thenReturn(Optional.of(r))
        whenever(proposalRepo.findFirstByRequestIdAndOutcome(100L, ProposalOutcome.OUTSTANDING))
            .thenReturn(null)

        assertThrows(InvalidStateTransitionException::class.java) {
            sut.propose("owner@example.com", 10L, 100L, LocalDateTime.now().plusDays(3), null)
        }
        verify(proposalRepo, never()).save(any<ServiceRequestProposal>())
    }

    @Test
    fun `declining a proposed request settles the outstanding proposal`() {
        val r = req(ServiceRequestStatus.REVISION_REQUESTED)
        val outstanding = ServiceRequestProposal(id = 99L, outcome = ProposalOutcome.OUTSTANDING)
        whenever(repo.findById(100L)).thenReturn(Optional.of(r))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }
        whenever(proposalRepo.findFirstByRequestIdAndOutcome(100L, ProposalOutcome.OUTSTANDING))
            .thenReturn(outstanding)
        whenever(proposalRepo.save(any<ServiceRequestProposal>())).thenAnswer { it.arguments[0] as ServiceRequestProposal }

        val response = sut.decline("owner@example.com", 10L, 100L, "Sorry, fully booked")

        assertEquals(ServiceRequestStatus.DECLINED, response.status)
        assertEquals(ProposalOutcome.REJECTED, outstanding.outcome)
        assertNotNull(outstanding.respondedAt)
    }

    @Test
    fun `declining writes respondedAt in UTC for both the request and the settled proposal`() {
        assertTestJvmPinnedToNonUtcZone()
        val r = req(ServiceRequestStatus.REVISION_REQUESTED)
        val outstanding = ServiceRequestProposal(id = 99L, outcome = ProposalOutcome.OUTSTANDING)
        whenever(repo.findById(100L)).thenReturn(Optional.of(r))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }
        whenever(proposalRepo.findFirstByRequestIdAndOutcome(100L, ProposalOutcome.OUTSTANDING))
            .thenReturn(outstanding)
        whenever(proposalRepo.save(any<ServiceRequestProposal>())).thenAnswer { it.arguments[0] as ServiceRequestProposal }

        val response = sut.decline("owner@example.com", 10L, 100L, "Sorry, fully booked")

        assertWrittenInUtcNotLocalZone(response.respondedAt)
        assertWrittenInUtcNotLocalZone(outstanding.respondedAt)
    }

    @Test
    fun `accepting checks the quota before changing anything`() {
        val r = req(ServiceRequestStatus.PENDING)
        whenever(repo.findById(100L)).thenReturn(Optional.of(r))
        whenever(quotaService.requireCapacity(any()))
            .thenThrow(QuotaExceededException("This business can't take more requests at the moment."))

        assertThrows(QuotaExceededException::class.java) {
            sut.accept("owner@example.com", 10L, 100L)
        }
        // Nothing moved: no status change, no save, no notification.
        assertEquals(ServiceRequestStatus.PENDING, r.status)
        verify(repo, never()).save(any<ServiceRequest>())
        // The check is charged to the business *owner* (id 2), not the business
        // id (10) or the customer (id 1) - those three must never be confused,
        // since the allowance is pooled per account across every business they own.
        verify(quotaService).requireCapacity(2L)
    }

    @Test
    fun `accepting a request that already holds a unit does not re-check capacity`() {
        // REVISION_REQUESTED already consumed a unit when it was proposed.
        // Accepting it is the same booking continuing, not a new one, so this
        // must succeed even if the account is sitting exactly at its limit -
        // and it must not even ask, because this transition charges nothing new.
        // If the skip in ProviderRequestService.accept is removed, this test
        // fails: requireCapacity is stubbed to throw below.
        val r = req(ServiceRequestStatus.REVISION_REQUESTED)
        whenever(repo.findById(100L)).thenReturn(Optional.of(r))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }
        whenever(quotaService.holdsUnit(ServiceRequestStatus.REVISION_REQUESTED)).thenReturn(true)
        whenever(quotaService.requireCapacity(any()))
            .thenThrow(QuotaExceededException("This business can't take more requests at the moment."))

        val response = sut.accept("owner@example.com", 10L, 100L)

        assertEquals(ServiceRequestStatus.CONFIRMED, response.status)
        verify(quotaService, never()).requireCapacity(any())
    }

    @Test
    fun `proposing a time also consumes the allowance`() {
        // Proposing reserves a unit, so it must be refused at the limit too —
        // otherwise the cap is avoidable by proposing instead of accepting.
        val r = req(ServiceRequestStatus.PENDING)
        whenever(repo.findById(100L)).thenReturn(Optional.of(r))
        whenever(proposalRepo.findFirstByRequestIdAndOutcome(100L, ProposalOutcome.OUTSTANDING))
            .thenReturn(null)
        whenever(quotaService.requireCapacity(any()))
            .thenThrow(QuotaExceededException("This business can't take more requests at the moment."))

        assertThrows(QuotaExceededException::class.java) {
            sut.propose("owner@example.com", 10L, 100L, LocalDateTime.now().plusDays(3), null)
        }
        assertEquals(ServiceRequestStatus.PENDING, r.status)
        verify(proposalRepo, never()).save(any<ServiceRequestProposal>())
        // Same identity requirement as accept: owner (2), not business (10) or
        // customer (1).
        verify(quotaService).requireCapacity(2L)
    }

    @Test
    fun `completing a job does not consult the quota`() {
        // The unit was consumed at acceptance. Charging again at completion would
        // double-count the same job.
        val r = req(ServiceRequestStatus.CONFIRMED)
        r.requestedDate = LocalDateTime.now().minusDays(1)
        whenever(repo.findById(100L)).thenReturn(Optional.of(r))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }

        sut.complete("owner@example.com", 10L, 100L)

        verify(quotaService, never()).requireCapacity(any())
    }

    @Test
    fun `the provider sees the sent quantity, estimate and snapshotted picks`() {
        val r = req(ServiceRequestStatus.PENDING, viewedAt = LocalDateTime.now())
        val item = ServiceRequestItem(
            request = r, position = 0, quantity = 40,
            snapshotTitle = "Catering", snapshotPriceAmount = BigDecimal("20000.00"), snapshotPriceCurrency = "UGX",
            snapshotPriceIsEstimate = true, snapshotEstimateReason = "Final count may change", snapshotQuantityUnit = "guest",
        )
        item.picks.add(
            ServiceRequestItemPick(
                id = 7L, item = item, choice = null, groupId = 32L, snapshotGroupName = "Juices",
                snapshotTitle = "Mango", snapshotPrice = BigDecimal("3000.00"), quantity = 2, position = 0,
            ),
        )
        r.items.add(item)
        whenever(repo.findById(100L)).thenReturn(Optional.of(r))

        val response = sut.get("owner@example.com", 10L, 100L)

        val dto = response.items.single()
        assertEquals(40, dto.quantity)
        assertEquals("guest", dto.quantityUnit)
        assertTrue(dto.priceIsEstimate)
        assertEquals("Final count may change", dto.estimateReason)
        assertEquals(
            listOf(ItemPickResponse(7L, null, 32L, "Juices", "Mango", BigDecimal("3000.00"), 2, 0)),
            dto.picks,
        )
        assertEquals(PricingState.ESTIMATED, response.pricing)
        assertEquals(0, BigDecimal("806000.00").compareTo(response.total))
    }

    // --- price proposals (Task 7) ---

    /** A CONFIRMED booking whose job date has passed, with one snapshotted item. */
    private fun bookedWith(
        price: String?,
        estimate: Boolean,
        status: ServiceRequestStatus = ServiceRequestStatus.CONFIRMED,
    ): ServiceRequest {
        val r = req(status)
        r.requestedDate = LocalDateTime.now().minusDays(1)
        r.items.add(
            ServiceRequestItem(
                request = r, position = 0, quantity = 1, snapshotTitle = "Venue dressing",
                snapshotPriceAmount = price?.let { BigDecimal(it) },
                snapshotPriceCurrency = price?.let { "UGX" },
                snapshotPriceIsEstimate = estimate,
            ),
        )
        whenever(repo.findById(100L)).thenReturn(Optional.of(r))
        whenever(repo.save(any<ServiceRequest>())).thenAnswer { it.arguments[0] as ServiceRequest }
        return r
    }

    /** Saves echo back and become the "latest" the response reads, as the real repository would. */
    private fun priceRepoRemembersSaves(): MutableList<ServiceRequestPriceProposal> {
        val saved = mutableListOf<ServiceRequestPriceProposal>()
        whenever(priceProposalRepo.save(any<ServiceRequestPriceProposal>())).thenAnswer {
            val p = it.arguments[0] as ServiceRequestPriceProposal
            if (p !in saved) saved.add(p)
            p
        }
        whenever(priceProposalRepo.findFirstByRequestIdOrderByProposedAtDescIdDesc(100L))
            .thenAnswer { saved.lastOrNull { it.outcome == ProposalOutcome.OUTSTANDING } ?: saved.lastOrNull() }
        return saved
    }

    @Test
    fun `propose price on a confirmed estimated booking stores an outstanding proposal`() {
        assertTestJvmPinnedToNonUtcZone()
        bookedWith("3500000.00", estimate = true)
        val saved = priceRepoRemembersSaves()

        val response = sut.proposePrice(
            "owner@example.com", 10L, 100L, BigDecimal("4200000"), "UGX", "  after visiting the venue  ",
        )

        val p = saved.single()
        assertEquals(0, BigDecimal("4200000").compareTo(p.amount))
        assertEquals("UGX", p.currency)
        assertEquals("after visiting the venue", p.note)
        assertEquals(ProposalOutcome.OUTSTANDING, p.outcome)
        assertWrittenInUtcNotLocalZone(p.proposedAt)
        assertEquals(ProposalOutcome.OUTSTANDING, response.priceProposal?.outcome)
        assertEquals(false, response.priceSettled)
        assertNull(response.agreedPrice)
        // Still CONFIRMED: proposing a price is not a status change.
        assertEquals(ServiceRequestStatus.CONFIRMED, response.status)
        verify(businessService).requireRole(10L, "owner@example.com", BusinessRole.MANAGER)
        verify(eventPublisher).priceChanged(any(), eq(PriceEventKind.PROPOSED), any())
    }

    @Test
    fun `propose price normalises a blank note to null`() {
        bookedWith(null, estimate = false)
        val saved = priceRepoRemembersSaves()

        sut.proposePrice("owner@example.com", 10L, 100L, BigDecimal("100"), "UGX", "   ")

        assertNull(saved.single().note)
    }

    @Test
    fun `second proposal supersedes the unanswered one`() {
        bookedWith(null, estimate = false)
        val saved = priceRepoRemembersSaves()
        val first = ServiceRequestPriceProposal(
            id = 1L, amount = BigDecimal("100"), currency = "UGX",
            proposedAt = LocalDateTime.now(ZoneOffset.UTC).minusHours(1), outcome = ProposalOutcome.OUTSTANDING,
        )
        whenever(priceProposalRepo.findFirstByRequestIdAndOutcome(100L, ProposalOutcome.OUTSTANDING))
            .thenReturn(first)

        val response = sut.proposePrice("owner@example.com", 10L, 100L, BigDecimal("150"), "UGX", null)

        assertEquals(ProposalOutcome.SUPERSEDED, first.outcome)
        assertWrittenInUtcNotLocalZone(first.respondedAt)
        val current = saved.single { it !== first }
        assertEquals(ProposalOutcome.OUTSTANDING, current.outcome)
        assertEquals(0, BigDecimal("150").compareTo(response.priceProposal!!.amount))
    }

    @Test
    fun `propose price is refused on a firm booking`() {
        bookedWith("250000.00", estimate = false)

        assertThrows(IllegalArgumentException::class.java) {
            sut.proposePrice("owner@example.com", 10L, 100L, BigDecimal("300000"), "UGX", null)
        }
        verify(priceProposalRepo, never()).save(any<ServiceRequestPriceProposal>())
    }

    @Test
    fun `propose price is refused once a price was accepted`() {
        val r = bookedWith(null, estimate = false)
        r.agreedPriceAmount = BigDecimal("100")
        r.agreedPriceCurrency = "UGX"

        assertThrows(IllegalArgumentException::class.java) {
            sut.proposePrice("owner@example.com", 10L, 100L, BigDecimal("300000"), "UGX", null)
        }
        verify(priceProposalRepo, never()).save(any<ServiceRequestPriceProposal>())
    }

    @Test
    fun `propose price is refused when not confirmed`() {
        bookedWith(null, estimate = false, status = ServiceRequestStatus.PENDING)

        assertThrows(IllegalArgumentException::class.java) {
            sut.proposePrice("owner@example.com", 10L, 100L, BigDecimal("300000"), "UGX", null)
        }
        verify(priceProposalRepo, never()).save(any<ServiceRequestPriceProposal>())
    }

    @Test
    fun `propose price refuses a zero amount`() {
        bookedWith(null, estimate = false)

        assertThrows(IllegalArgumentException::class.java) {
            sut.proposePrice("owner@example.com", 10L, 100L, BigDecimal.ZERO, "UGX", null)
        }
    }

    @Test
    fun `complete is refused while a price is outstanding`() {
        val r = bookedWith("3500000.00", estimate = true)
        whenever(priceProposalRepo.findFirstByRequestIdAndOutcome(100L, ProposalOutcome.OUTSTANDING))
            .thenReturn(ServiceRequestPriceProposal(id = 1L, amount = BigDecimal("1"), currency = "UGX"))

        assertThrows(PriceNotSettledException::class.java) {
            sut.complete("owner@example.com", 10L, 100L)
        }
        assertEquals(ServiceRequestStatus.CONFIRMED, r.status)
        verify(repo, never()).save(any<ServiceRequest>())
    }

    @Test
    fun `complete is refused for an unpriced booking with no agreed price`() {
        bookedWith(null, estimate = false)

        assertThrows(PriceNotSettledException::class.java) {
            sut.complete("owner@example.com", 10L, 100L)
        }
    }

    @Test
    fun `complete goes through for an unpriced booking once a price was agreed`() {
        val r = bookedWith(null, estimate = false)
        r.agreedPriceAmount = BigDecimal("100")
        r.agreedPriceCurrency = "UGX"

        val response = sut.complete("owner@example.com", 10L, 100L)

        assertEquals(ServiceRequestStatus.COMPLETED, response.status)
        assertEquals(true, response.priceSettled)
        // The agreed figure replaces the itemised total on the wire.
        assertNull(response.total)
        assertEquals(0, BigDecimal("100").compareTo(response.agreedPrice!!.amount))
    }

    @Test
    fun `an estimate that was never changed does not block completion`() {
        bookedWith("3500000.00", estimate = true)

        val response = sut.complete("owner@example.com", 10L, 100L)

        assertEquals(ServiceRequestStatus.COMPLETED, response.status)
    }
}
