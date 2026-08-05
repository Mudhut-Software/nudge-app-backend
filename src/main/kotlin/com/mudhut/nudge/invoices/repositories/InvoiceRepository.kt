package com.mudhut.nudge.invoices.repositories

import com.mudhut.nudge.invoices.entities.Invoice
import com.mudhut.nudge.invoices.entities.InvoiceStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface InvoiceRepository : JpaRepository<Invoice, Long> {
    fun findByBusinessIdOrderByCreatedAtDescIdDesc(businessId: Long): List<Invoice>
    fun findByBusinessIdAndStatusOrderByCreatedAtDescIdDesc(businessId: Long, status: InvoiceStatus): List<Invoice>
    fun findByIdAndBusinessId(id: Long, businessId: Long): Optional<Invoice>

    @Query("SELECT MAX(i.sequenceNo) FROM Invoice i WHERE i.business.id = :businessId")
    fun findMaxSequenceForBusiness(@Param("businessId") businessId: Long): Int?
}
