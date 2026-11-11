package com.mudhut.nudge.servicerequests.spi

import com.mudhut.nudge.analytics.spi.MonthlyCount
import com.mudhut.nudge.analytics.spi.MonthlyMoney
import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.entities.BusinessCategory
import com.mudhut.nudge.businesses.repositories.BusinessCategoryRepository
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.servicerequests.entities.ServiceRequest
import com.mudhut.nudge.servicerequests.entities.ServiceRequestItem
import com.mudhut.nudge.servicerequests.entities.ServiceRequestItemAddon
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.COMPLETED
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestRepository
import com.mudhut.nudge.servicesoffered.entities.PriceMode
import com.mudhut.nudge.servicesoffered.entities.ServiceOffered
import com.mudhut.nudge.servicesoffered.repositories.ServiceOfferedRepository
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.repositories.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.concurrent.atomic.AtomicLong

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(RequestStatsQueryImpl::class)
class RequestStatsQueryImplTest {

    companion object {
        private val seq = AtomicLong()
        private fun unique(prefix: String) = "$prefix-${seq.incrementAndGet()}"
        val OCT: YearMonth = YearMonth.of(2026, 10)
        val FROM: LocalDateTime = LocalDateTime.of(2026, 9, 1, 0, 0)
        val TO: LocalDateTime = LocalDateTime.of(2026, 11, 1, 0, 0)
    }

    @Autowired private lateinit var stats: RequestStatsQueryImpl
    @Autowired private lateinit var requestRepo: ServiceRequestRepository
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var businessRepository: BusinessRepository
    @Autowired private lateinit var categoryRepository: BusinessCategoryRepository
    @Autowired private lateinit var serviceRepo: ServiceOfferedRepository

    private lateinit var category: BusinessCategory
    private lateinit var biz: Business
    private lateinit var haircut: ServiceOffered
    private lateinit var shave: ServiceOffered

    @BeforeEach
    fun setUp() {
        category = categoryRepository.save(BusinessCategory(name = unique("category")))
        biz = business(user("owner"))
        haircut = service(biz, "Haircut")
        shave = service(biz, "Shave")
    }

    private fun user(prefix: String = "user"): User = userRepository.save(
        User(email = "${unique(prefix)}@example.com", username = unique(prefix).take(50), password = "password123"),
    )

    private fun business(owner: User): Business =
        businessRepository.save(Business(name = unique("business"), owner = owner, category = category))

    private fun service(b: Business, title: String): ServiceOffered = serviceRepo.save(
        ServiceOffered(
            business = b, title = title,
            coverImageUrl = "https://example.com/c.jpg", coverImagePublicId = unique("cover"),
            priceMode = PriceMode.FIXED, priceAmount = BigDecimal("100.00"), priceCurrency = "UGX",
        ),
    )

    /** One line on a request: a service, its snapshot price (null = QUOTE), and addons as (delta, quantity). */
    data class Line(
        val service: ServiceOffered,
        val price: BigDecimal?,
        val addons: List<Pair<BigDecimal, Int>> = emptyList(),
        val currency: String? = "UGX",
    )

    private fun request(
        customer: User,
        status: ServiceRequestStatus,
        lines: List<Line> = emptyList(),
        completedAt: LocalDateTime? = null,
        submittedAt: LocalDateTime? = null,
        respondedAt: LocalDateTime? = null,
        business: Business = biz,
    ): ServiceRequest {
        val r = ServiceRequest(
            customer = customer, business = business, status = status,
            completedAt = completedAt, submittedAt = submittedAt, respondedAt = respondedAt,
        )
        lines.forEachIndexed { idx, line ->
            val item = ServiceRequestItem(
                request = r, service = line.service, position = idx,
                snapshotTitle = line.service.title, snapshotPriceAmount = line.price,
                snapshotPriceCurrency = line.currency,
            )
            line.addons.forEachIndexed { j, (delta, qty) ->
                item.addons.add(
                    ServiceRequestItemAddon(item = item, snapshotTitle = "addon", snapshotPriceDelta = delta, quantity = qty, position = j),
                )
            }
            r.items.add(item)
        }
        return requestRepo.saveAndFlush(r)
    }

    private fun completed(customer: User, at: LocalDateTime, vararg lines: Line) =
        request(customer, COMPLETED, lines.toList(), completedAt = at, submittedAt = at.minusDays(3))

    @Test
    fun `completions bucket by completedAt month with a half-open boundary`() {
        val c = user("customer")
        completed(c, LocalDateTime.of(2026, 10, 31, 23, 59), Line(haircut, BigDecimal("100.00")))
        completed(c, LocalDateTime.of(2026, 11, 1, 0, 0), Line(haircut, BigDecimal("100.00")))   // outside TO
        completed(c, LocalDateTime.of(2026, 9, 30, 12, 0), Line(haircut, BigDecimal("100.00")))

        val s = stats.completions(biz.id!!, FROM, TO)

        assertEquals(setOf(MonthlyCount(OCT, 1), MonthlyCount(OCT.minusMonths(1), 1)), s.jobs.toSet())
    }

    @Test
    fun `work sums item prices and addon delta times quantity per currency`() {
        val c = user("customer")
        completed(
            c, LocalDateTime.of(2026, 10, 5, 10, 0),
            Line(haircut, BigDecimal("100.00"), addons = listOf(BigDecimal("10.00") to 3)),   // 130
            Line(shave, BigDecimal("20.00")),                                                 // +20 = 150 UGX
        )
        completed(c, LocalDateTime.of(2026, 10, 6, 10, 0), Line(haircut, BigDecimal("5.50"), currency = "USD"))

        val work = stats.completions(biz.id!!, FROM, TO).work
            .groupBy { it.currency }
            .mapValues { (_, rows) -> rows.fold(BigDecimal.ZERO) { a, r -> a + r.amount } }

        assertEquals(0, BigDecimal("150.00").compareTo(work["UGX"]))
        assertEquals(0, BigDecimal("5.50").compareTo(work["USD"]))
    }

    @Test
    fun `a quote-priced item marks the job unpriced and is left out of work`() {
        val c = user("customer")
        completed(
            c, LocalDateTime.of(2026, 10, 5, 10, 0),
            Line(haircut, null),                       // QUOTE
            Line(shave, BigDecimal("20.00")),
        )

        val s = stats.completions(biz.id!!, FROM, TO)

        assertEquals(listOf(MonthlyCount(OCT, 1)), s.unpricedJobs)
        assertEquals(1, s.work.size)
        assertEquals(0, BigDecimal("20.00").compareTo(s.work.single().amount))
    }

    @Test
    fun `a priced item with a null currency is excluded without crashing`() {
        val c = user("customer")
        completed(c, LocalDateTime.of(2026, 10, 5, 10, 0), Line(haircut, BigDecimal("100.00"), currency = null))

        val s = stats.completions(biz.id!!, FROM, TO)

        assertEquals(listOf(MonthlyCount(OCT, 1)), s.jobs)
        assertEquals(emptyList<MonthlyMoney>(), s.work)
    }

    @Test
    fun `other statuses and other businesses are excluded`() {
        val c = user("customer")
        val other = business(user("other-owner"))
        val at = LocalDateTime.of(2026, 10, 5, 10, 0)
        request(c, ServiceRequestStatus.CONFIRMED, listOf(Line(haircut, BigDecimal("1.00"))), completedAt = at)
        request(c, COMPLETED, listOf(Line(service(other, "Other"), BigDecimal("1.00"))), completedAt = at, business = other)

        assertEquals(emptyList<MonthlyCount>(), stats.completions(biz.id!!, FROM, TO).jobs)
    }
}
