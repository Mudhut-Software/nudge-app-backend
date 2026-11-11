package com.mudhut.nudge.invoices.repositories

import com.mudhut.nudge.invoices.entities.Invoice
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param
import java.math.BigDecimal
import java.time.LocalDateTime

interface PaidMonthRow { val y: Int; val m: Int; val currency: String; val amount: BigDecimal }

/** Read-only aggregates for the analytics SPI. */
interface InvoiceAnalyticsRepository : Repository<Invoice, Long> {

    @Query("""
        SELECT year(i.paidAt) AS y, month(i.paidAt) AS m, i.currency AS currency,
               SUM(l.unitAmount * l.quantity) AS amount
        FROM InvoiceLine l JOIN l.invoice i
        WHERE i.business.id = :businessId
          AND i.status = com.mudhut.nudge.invoices.entities.InvoiceStatus.PAID
          AND i.paidAt >= :from AND i.paidAt < :to
        GROUP BY year(i.paidAt), month(i.paidAt), i.currency
    """)
    fun paidByMonth(
        @Param("businessId") businessId: Long,
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
    ): List<PaidMonthRow>
}
