package com.mudhut.nudge.invoices.services

import com.mudhut.nudge.analytics.spi.MonthlyMoney
import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.entities.BusinessCategory
import com.mudhut.nudge.businesses.repositories.BusinessCategoryRepository
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.invoices.entities.Invoice
import com.mudhut.nudge.invoices.entities.InvoiceLine
import com.mudhut.nudge.invoices.entities.InvoiceStatus
import com.mudhut.nudge.invoices.repositories.InvoiceRepository
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
@Import(PaymentStatsQueryImpl::class)
class PaymentStatsQueryImplTest {

    companion object {
        private val seq = AtomicLong()
        private fun unique(prefix: String) = "$prefix-${seq.incrementAndGet()}"
        val FROM: LocalDateTime = LocalDateTime.of(2026, 9, 1, 0, 0)
        val TO: LocalDateTime = LocalDateTime.of(2026, 11, 1, 0, 0)
    }

    @Autowired private lateinit var stats: PaymentStatsQueryImpl
    @Autowired private lateinit var invoiceRepo: InvoiceRepository
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var businessRepository: BusinessRepository
    @Autowired private lateinit var categoryRepository: BusinessCategoryRepository

    private lateinit var owner: User
    private lateinit var customer: User
    private lateinit var biz: Business

    @BeforeEach
    fun setUp() {
        val category: BusinessCategory = categoryRepository.save(BusinessCategory(name = unique("category")))
        owner = user("owner")
        customer = user("customer")
        biz = businessRepository.save(Business(name = unique("business"), owner = owner, category = category))
    }

    private fun user(prefix: String): User = userRepository.save(
        User(email = "${unique(prefix)}@example.com", username = unique(prefix).take(50), password = "password123"),
    )

    private fun invoice(
        status: InvoiceStatus,
        paidAt: LocalDateTime?,
        currency: String = "UGX",
        lines: List<Pair<String, Int>> = listOf("100.00" to 1),
        business: Business = biz,
    ): Invoice {
        val inv = Invoice(business = business, customer = customer, createdBy = owner, status = status, currency = currency, paidAt = paidAt)
        lines.forEachIndexed { i, (amount, qty) ->
            inv.lines.add(InvoiceLine(invoice = inv, description = "line $i", unitAmount = BigDecimal(amount), quantity = qty, position = i))
        }
        return invoiceRepo.saveAndFlush(inv)
    }

    @Test
    fun `sums paid invoices by paidAt month and currency, multiplying quantity`() {
        invoice(InvoiceStatus.PAID, LocalDateTime.of(2026, 10, 31, 23, 59), lines = listOf("100.00" to 2, "50.00" to 1))   // 250
        invoice(InvoiceStatus.PAID, LocalDateTime.of(2026, 10, 2, 9, 0), currency = "USD", lines = listOf("5.50" to 1))
        invoice(InvoiceStatus.PAID, LocalDateTime.of(2026, 9, 15, 9, 0))                                                 // 100 in Sep

        val rows = stats.paid(biz.id!!, FROM, TO).associateBy { it.month to it.currency }

        assertEquals(0, BigDecimal("250.00").compareTo(rows.getValue(YearMonth.of(2026, 10) to "UGX").amount))
        assertEquals(0, BigDecimal("5.50").compareTo(rows.getValue(YearMonth.of(2026, 10) to "USD").amount))
        assertEquals(0, BigDecimal("100.00").compareTo(rows.getValue(YearMonth.of(2026, 9) to "UGX").amount))
    }

    @Test
    fun `void, sent, draft, out-of-range and other businesses are excluded`() {
        val at = LocalDateTime.of(2026, 10, 2, 9, 0)
        invoice(InvoiceStatus.VOID, at)
        invoice(InvoiceStatus.SENT, at)
        invoice(InvoiceStatus.DRAFT, null)
        invoice(InvoiceStatus.PAID, LocalDateTime.of(2026, 11, 1, 0, 0))
        val other = businessRepository.save(Business(name = unique("other"), owner = owner, category = categoryRepository.save(BusinessCategory(name = unique("c")))))
        invoice(InvoiceStatus.PAID, at, business = other)

        assertEquals(emptyList<MonthlyMoney>(), stats.paid(biz.id!!, FROM, TO))
    }
}
