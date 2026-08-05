package com.mudhut.nudge.invoices.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "invoice_lines")
class InvoiceLine(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    var invoice: Invoice? = null,

    @Column(nullable = false, length = 200)
    var description: String? = null,

    @Column(name = "unit_amount", nullable = false, precision = 19, scale = 2)
    var unitAmount: BigDecimal? = null,

    @Column(nullable = false)
    var quantity: Int = 1,

    @Column(nullable = false)
    var position: Int = 0,
)
