package com.mudhut.nudge.invoices.entities

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.users.entities.User
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "invoices")
class Invoice(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    var business: Business? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    var customer: User? = null,

    // Loose reference to the source ServiceRequest — no FK by design.
    @Column(name = "source_request_id")
    var sourceRequestId: Long? = null,

    // Per-business sequential display number, assigned at issue (null while DRAFT).
    @Column(length = 32)
    var number: String? = null,

    @Column(name = "sequence_no")
    var sequenceNo: Int? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: InvoiceStatus = InvoiceStatus.DRAFT,

    @Column(nullable = false, length = 3)
    var currency: String = "USD",

    @Column(name = "issue_date")
    var issueDate: LocalDate? = null,

    @Column(name = "due_date")
    var dueDate: LocalDate? = null,

    @Column(columnDefinition = "TEXT")
    var notes: String? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    var createdBy: User? = null,

    @OneToMany(mappedBy = "invoice", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("position ASC")
    var lines: MutableList<InvoiceLine> = mutableListOf(),

    @Column(name = "sent_at")
    var sentAt: LocalDateTime? = null,

    @Column(name = "paid_at")
    var paidAt: LocalDateTime? = null,

    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null,

    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null,
)
