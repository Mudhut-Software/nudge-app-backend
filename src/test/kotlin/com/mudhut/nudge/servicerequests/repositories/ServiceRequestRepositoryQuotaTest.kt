package com.mudhut.nudge.servicerequests.repositories

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.entities.BusinessCategory
import com.mudhut.nudge.businesses.repositories.BusinessCategoryRepository
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.servicerequests.entities.ServiceRequest
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.repositories.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicLong

/**
 * `countQuotaConsuming` is the single query the entire job-quota cap depends
 * on. Before this test it had only ever been parse-validated at boot — every
 * `JobQuotaService`/`ProviderRequestService`/`ServiceRequestService` test
 * mocks `ServiceRequestRepository`, so a wrong join (e.g. `r.business.owner.id`
 * silently resolving to the wrong hop) would pass all of them.
 *
 * Runs against the real Postgres the suite already requires (`TEST_DB_URL`,
 * `ddl-auto=create-drop`), not H2 and not a container: the point is to exercise
 * the actual dialect every environment runs, not a JPQL parser's idea of it,
 * and the `test` profile already provides exactly that with no extra
 * infrastructure. `replace = NONE` stops Boot swapping in an embedded database.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ServiceRequestRepositoryQuotaTest {

    companion object {
        // Each test persists its own owner/customer/business rows (unique email
        // and username are DB constraints), so every fixture needs a value that
        // is unique across the whole class, not just within one test method.
        private val seq = AtomicLong()
        private fun unique(prefix: String) = "$prefix-${seq.incrementAndGet()}"
    }

    @Autowired
    private lateinit var repo: ServiceRequestRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var businessRepository: BusinessRepository

    @Autowired
    private lateinit var categoryRepository: BusinessCategoryRepository

    /** Every status that holds a unit of the allowance — mirrors JobQuotaService's QUOTA_CONSUMING. */
    private val quotaConsumingStatuses = listOf(
        ServiceRequestStatus.REVISION_REQUESTED,
        ServiceRequestStatus.CONFIRMED,
        ServiceRequestStatus.COMPLETED,
        ServiceRequestStatus.NO_SHOW,
    )

    private lateinit var category: BusinessCategory

    @BeforeEach
    fun setUp() {
        category = categoryRepository.save(BusinessCategory(name = unique("category")))
    }

    private fun user(prefix: String = "user"): User = userRepository.save(
        User(
            email = "${unique(prefix)}@example.com",
            username = unique(prefix).take(50),
            password = "password123",
        )
    )

    private fun business(owner: User, name: String = "business"): Business = businessRepository.save(
        Business(name = unique(name), owner = owner, category = category)
    )

    private fun requestWith(
        customer: User,
        business: Business,
        status: ServiceRequestStatus,
        respondedAt: LocalDateTime?,
    ): ServiceRequest = repo.saveAndFlush(
        ServiceRequest(customer = customer, business = business, status = status, respondedAt = respondedAt)
    )

    private val periodStart: LocalDateTime = LocalDateTime.of(2026, 3, 1, 0, 0)
    private val inPeriod: LocalDateTime = periodStart.plusDays(1)

    @Test
    fun `counts exactly the statuses that hold a unit of the allowance`() {
        val owner = user("owner")
        val biz = business(owner)
        val customer = user("customer")

        // The four that consume a unit.
        requestWith(customer, biz, ServiceRequestStatus.REVISION_REQUESTED, inPeriod)
        requestWith(customer, biz, ServiceRequestStatus.CONFIRMED, inPeriod)
        requestWith(customer, biz, ServiceRequestStatus.COMPLETED, inPeriod)
        requestWith(customer, biz, ServiceRequestStatus.NO_SHOW, inPeriod)

        // The four that never held one or released it — must not be counted.
        requestWith(customer, biz, ServiceRequestStatus.DRAFT, inPeriod)
        requestWith(customer, biz, ServiceRequestStatus.PENDING, inPeriod)
        requestWith(customer, biz, ServiceRequestStatus.DECLINED, inPeriod)
        requestWith(customer, biz, ServiceRequestStatus.CANCELLED, inPeriod)

        val count = repo.countQuotaConsuming(owner.id!!, quotaConsumingStatuses, periodStart)

        assertEquals(4, count)
    }

    @Test
    fun `pools usage across every business the owner owns`() {
        val owner = user("owner")
        val bizA = business(owner, "biz-a")
        val bizB = business(owner, "biz-b")
        val customer = user("customer")

        // Two different businesses, same owner: the two-hop r.business.owner.id
        // join must sum both into the single pooled allowance.
        requestWith(customer, bizA, ServiceRequestStatus.CONFIRMED, inPeriod)
        requestWith(customer, bizA, ServiceRequestStatus.COMPLETED, inPeriod)
        requestWith(customer, bizB, ServiceRequestStatus.NO_SHOW, inPeriod)

        val count = repo.countQuotaConsuming(owner.id!!, quotaConsumingStatuses, periodStart)

        assertEquals(3, count)
    }

    @Test
    fun `does not count a request owned by a different account`() {
        val owner = user("owner")
        val otherOwner = user("other-owner")
        val mine = business(owner, "mine")
        val theirs = business(otherOwner, "theirs")
        val customer = user("customer")

        requestWith(customer, mine, ServiceRequestStatus.CONFIRMED, inPeriod)
        requestWith(customer, theirs, ServiceRequestStatus.CONFIRMED, inPeriod)

        val count = repo.countQuotaConsuming(owner.id!!, quotaConsumingStatuses, periodStart)

        assertEquals(1, count)
    }

    @Test
    fun `respondedAt strictly before periodStart is excluded, exactly at the boundary is included`() {
        val owner = user("owner")
        val biz = business(owner)
        val customer = user("customer")

        requestWith(customer, biz, ServiceRequestStatus.CONFIRMED, periodStart.minusSeconds(1))
        requestWith(customer, biz, ServiceRequestStatus.CONFIRMED, periodStart)

        val count = repo.countQuotaConsuming(owner.id!!, quotaConsumingStatuses, periodStart)

        assertEquals(1, count)
    }

    @Test
    fun `a request with no respondedAt is not counted`() {
        val owner = user("owner")
        val biz = business(owner)
        val customer = user("customer")

        requestWith(customer, biz, ServiceRequestStatus.CONFIRMED, null)

        val count = repo.countQuotaConsuming(owner.id!!, quotaConsumingStatuses, periodStart)

        assertEquals(0, count)
    }

    /**
     * Guards the timezone fix (see ProviderRequestService/ServiceRequestService
     * `respondedAt` writes and JobQuotaService's boundary derivation): both now
     * share the UTC frame, so the `>=` comparison here is evaluated the same
     * way it will be read by `JobQuotaService.snapshotFor`, which hands this
     * query `periodStart.atOffset(ZoneOffset.UTC).toLocalDateTime()`.
     *
     * Before the fix, `respondedAt` was written with `LocalDateTime.now()`
     * (JVM-default zone) while the boundary was derived in UTC — on a server
     * ahead of UTC, a row written just after the real period rollover could
     * still read as "before" a UTC-derived `periodStart`, and vice versa. This
     * test fixes both sides to UTC, as production now does, and would catch a
     * regression that reintroduced a zone mismatch on either side.
     */
    @Test
    fun `respondedAt and periodStart compare correctly in the same UTC frame`() {
        val owner = user("owner")
        val biz = business(owner)
        val customer = user("customer")

        val boundaryInstant = Instant.parse("2026-03-08T00:00:00Z")
        val boundary = boundaryInstant.atOffset(ZoneOffset.UTC).toLocalDateTime()
        val justBefore = boundaryInstant.minusSeconds(1).atOffset(ZoneOffset.UTC).toLocalDateTime()
        val justAfter = boundaryInstant.plusSeconds(1).atOffset(ZoneOffset.UTC).toLocalDateTime()

        requestWith(customer, biz, ServiceRequestStatus.CONFIRMED, justBefore)
        requestWith(customer, biz, ServiceRequestStatus.CONFIRMED, boundary)
        requestWith(customer, biz, ServiceRequestStatus.CONFIRMED, justAfter)

        val count = repo.countQuotaConsuming(owner.id!!, quotaConsumingStatuses, boundary)

        assertEquals(2, count)
    }
}
