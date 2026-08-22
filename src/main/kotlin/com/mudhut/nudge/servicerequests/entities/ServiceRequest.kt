package com.mudhut.nudge.servicerequests.entities

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
import java.time.LocalDateTime

@Entity
@Table(name = "service_requests")
class ServiceRequest(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    var customer: User? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    var business: Business? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: ServiceRequestStatus = ServiceRequestStatus.DRAFT,

    @Column(name = "requested_date")
    var requestedDate: LocalDateTime? = null,

    @Column(name = "service_location", length = 500)
    var serviceLocation: String? = null,

    @Column(name = "service_latitude")
    var serviceLatitude: Double? = null,

    @Column(name = "service_longitude")
    var serviceLongitude: Double? = null,

    @Column(name = "note", columnDefinition = "TEXT")
    var note: String? = null,

    /**
     * How to reach the customer once on site ("keys are with the gateman at the
     * side gate"). Distinct from [note], which is the customer's message to the
     * provider: the wizard collects them on different steps and they previously
     * shared this column, so answering the second question destroyed the first.
     */
    @Column(name = "access_directions", columnDefinition = "TEXT")
    var accessDirections: String? = null,

    /**
     * Why the provider declined. Set only on the PENDING -> DECLINED transition.
     * Optional: a provider may decline without explaining.
     */
    @Column(name = "decline_reason", columnDefinition = "TEXT")
    var declineReason: String? = null,

    /** Why the customer cancelled. Set only on a transition to CANCELLED. Optional. */
    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    var cancellationReason: String? = null,

    @Column(name = "submitted_at")
    var submittedAt: LocalDateTime? = null,

    @Column(name = "responded_at")
    var respondedAt: LocalDateTime? = null,

    @Column(name = "completed_at")
    var completedAt: LocalDateTime? = null,

    @Column(name = "cancelled_at")
    var cancelledAt: LocalDateTime? = null,

    @Column(name = "viewed_at")
    var viewedAt: LocalDateTime? = null,

    @OneToMany(
        mappedBy = "request",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY,
    )
    @OrderBy("position ASC")
    var items: MutableList<ServiceRequestItem> = mutableListOf(),

    @OneToMany(
        mappedBy = "request",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY,
    )
    @OrderBy("position ASC")
    var attachments: MutableList<ServiceRequestAttachment> = mutableListOf(),

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = LocalDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = LocalDateTime.now(),
)
