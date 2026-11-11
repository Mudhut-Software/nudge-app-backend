package com.mudhut.nudge.config

import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.servicerequests.entities.ServiceRequest
import com.mudhut.nudge.servicerequests.entities.ServiceRequestItem
import com.mudhut.nudge.servicerequests.entities.ServiceRequestItemAddon
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestRepository
import com.mudhut.nudge.servicesoffered.repositories.ServiceOfferedRepository
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.entities.UserRole
import com.mudhut.nudge.users.repositories.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * Twelve months of activity for SparkleClean, so `/analytics` has real data in
 * dev. Runs on ApplicationReadyEvent, i.e. after `DevDataSeeder`'s runner, and
 * only once: it skips if the demo customer client1@nudge.local already exists.
 *
 * Deterministic by design: the same seed every time, so the figures on the
 * page can be checked by hand against this file.
 */
@Component
@Profile("dev")
class AnalyticsDemoSeeder(
    private val userRepo: UserRepository,
    private val businessRepo: BusinessRepository,
    private val serviceRepo: ServiceOfferedRepository,
    private val requestRepo: ServiceRequestRepository,
    private val jdbc: JdbcTemplate,
    private val passwordEncoder: PasswordEncoder,
) {

    private val log = LoggerFactory.getLogger(AnalyticsDemoSeeder::class.java)

    companion object {
        const val OWNER_EMAIL = "sarah@sparkleclean.local"
        const val PASSWORD = "user#password#123"
    }

    @Transactional
    @EventListener(ApplicationReadyEvent::class)
    fun seed() {
        val owner = userRepo.findByEmail(OWNER_EMAIL).orElse(null) ?: return
        val business = businessRepo.findAll().firstOrNull { it.owner?.id == owner.id } ?: return
        if (userRepo.findByEmail("client1@nudge.local").isPresent) return

        val services = serviceRepo.findAll().filter { it.business?.id == business.id }.sortedBy { it.id }
        if (services.size < 2) return
        val customers = (1..6).map { customer(it) }

        val now = YearMonth.now()
        var invoiceSeq = 9000
        for (ago in 11 downTo 0) {
            val month = now.minusMonths(ago.toLong())
            val day = { d: Int -> month.atDay(minOf(d, month.lengthOfMonth())).atTime(10, 0) }
            val completedCount = 2 + (11 - ago) / 2                       // grows from 2 to 7

            repeat(completedCount) { i ->
                val customer = customers[(i + ago) % customers.size]
                val done = day(3 + i * 3)
                val usd = ago % 4 == 0 && i == 0                          // one USD job every 4th month
                val quote = ago == 1 && i == 1                            // one quote-priced item last month
                val req = request(
                    customer, business, ServiceRequestStatus.COMPLETED,
                    submitted = done.minusDays(4), responded = done.minusDays(4).plusMinutes(20L + i * 15),
                    completed = done,
                    lines = listOf(
                        Triple(services[0], if (quote) null else BigDecimal(if (usd) "40.00" else "150000.00"), if (usd) "USD" else "UGX"),
                        Triple(services[1], BigDecimal(if (usd) "10.00" else "50000.00"), if (usd) "USD" else "UGX"),
                    ),
                    addonDelta = BigDecimal(if (usd) "5.00" else "10000.00"), addonQty = 2,
                )
                // Most completed jobs are invoiced and paid three days later; one per month is voided.
                if (i % 3 != 2) {
                    val status = if (i == 1 && ago % 5 == 0) "VOID" else "PAID"
                    invoice(business.id!!, customer.id!!, owner.id!!, req.id!!, ++invoiceSeq, status, if (usd) "USD" else "UGX",
                        if (usd) BigDecimal("70.00") else BigDecimal("220000.00"), done.plusDays(3))
                }
            }
            // Leaks: one declined each month, plus a cancellation and a no-show on alternating months.
            request(customers[ago % 6], business, ServiceRequestStatus.DECLINED, submitted = day(2), responded = day(2).plusHours(2))
            if (ago % 2 == 0) request(customers[(ago + 1) % 6], business, ServiceRequestStatus.CANCELLED, submitted = day(9), responded = day(9).plusMinutes(45))
            if (ago % 2 == 1) request(customers[(ago + 2) % 6], business, ServiceRequestStatus.NO_SHOW, submitted = day(12), responded = day(12).plusMinutes(10))
            if (ago == 0) request(customers[3], business, ServiceRequestStatus.PENDING, submitted = day(1))
        }

        // One review per customer (unique per customer↔business), spread across the year.
        listOf(5, 4, 5, 3, 4, 5).forEachIndexed { i, rating ->
            review(customers[i].id!!, business.id!!, rating, now.minusMonths((10 - i * 2).toLong()).atDay(15).atTime(18, 0))
        }

        log.info("AnalyticsDemoSeeder: seeded 12 months of activity for {}.", business.name)
    }

    private fun customer(n: Int): User =
        userRepo.findByEmail("client$n@nudge.local").orElseGet {
            userRepo.save(
                User(
                    username = "Demo Client $n", email = "client$n@nudge.local",
                    password = passwordEncoder.encode(PASSWORD), role = UserRole.BASIC_USER,
                    isActive = true, isEmailVerified = true, isPhoneVerified = false,
                ),
            )
        }

    private fun request(
        customer: User,
        business: com.mudhut.nudge.businesses.entities.Business,
        status: ServiceRequestStatus,
        submitted: LocalDateTime,
        responded: LocalDateTime? = null,
        completed: LocalDateTime? = null,
        lines: List<Triple<com.mudhut.nudge.servicesoffered.entities.ServiceOffered, BigDecimal?, String>> = emptyList(),
        addonDelta: BigDecimal? = null,
        addonQty: Int = 1,
    ): ServiceRequest {
        val r = ServiceRequest(
            customer = customer, business = business, status = status,
            requestedDate = completed ?: submitted.plusDays(5),
            submittedAt = submitted, respondedAt = responded, completedAt = completed,
            serviceLocation = "Kampala",
        )
        lines.forEachIndexed { idx, (svc, price, currency) ->
            val item = ServiceRequestItem(
                request = r, service = svc, position = idx, snapshotTitle = svc.title,
                snapshotPriceAmount = price, snapshotPriceCurrency = currency,
            )
            if (idx == 0 && addonDelta != null) {
                item.addons.add(ServiceRequestItemAddon(item = item, snapshotTitle = "Extra", snapshotPriceDelta = addonDelta, quantity = addonQty, position = 0))
            }
            r.items.add(item)
        }
        return requestRepo.save(r)
    }

    private fun invoice(
        businessId: Long, customerId: Long, ownerId: Long, requestId: Long, seq: Int,
        status: String, currency: String, amount: BigDecimal, paidAt: LocalDateTime,
    ) {
        val ts = Timestamp.valueOf(paidAt)
        val id = jdbc.queryForObject(
            """
            INSERT INTO invoices (business_id, customer_id, created_by, source_request_id, number, sequence_no,
                                  status, currency, issue_date, sent_at, paid_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
            """.trimIndent(),
            Long::class.java,
            businessId, customerId, ownerId, requestId, "INV-$seq", seq,
            status, currency, java.sql.Date.valueOf(paidAt.toLocalDate().minusDays(3)),
            Timestamp.valueOf(paidAt.minusDays(3)), if (status == "PAID") ts else null, ts, ts,
        )
        jdbc.update(
            "INSERT INTO invoice_lines (invoice_id, description, unit_amount, quantity, position) VALUES (?, ?, ?, ?, ?)",
            id, "Completed job", amount, 1, 0,
        )
    }

    private fun review(customerId: Long, businessId: Long, rating: Int, at: LocalDateTime) {
        val ts = Timestamp.valueOf(at)
        jdbc.update(
            "INSERT INTO reviews (customer_id, business_id, rating, comment, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
            customerId, businessId, rating, "Seeded review", ts, ts,
        )
    }
}
