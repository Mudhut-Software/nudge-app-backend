package com.mudhut.nudge.servicerequests.entities

import com.mudhut.nudge.servicesoffered.entities.ServiceOffered
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "service_request_items")
class ServiceRequestItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    var request: ServiceRequest? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    var service: ServiceOffered? = null,

    @Column(nullable = false)
    var position: Int = 0,

    @Column(name = "snapshot_title", length = 120)
    var snapshotTitle: String? = null,

    @Column(name = "snapshot_price_amount", precision = 19, scale = 2)
    var snapshotPriceAmount: BigDecimal? = null,

    @Column(name = "snapshot_price_currency", length = 3)
    var snapshotPriceCurrency: String? = null,

    @Column(name = "snapshot_cover_url", length = 500)
    var snapshotCoverUrl: String? = null,

    /** Units booked; 1 unless the item has quantity on. */
    @Column(nullable = false)
    var quantity: Int = 1,

    @Column(name = "snapshot_price_is_estimate", nullable = false)
    var snapshotPriceIsEstimate: Boolean = false,

    @Column(name = "snapshot_estimate_reason", length = 200)
    var snapshotEstimateReason: String? = null,

    @Column(name = "snapshot_quantity_unit", length = 32)
    var snapshotQuantityUnit: String? = null,

    @OneToMany(
        mappedBy = "item",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY,
    )
    @OrderBy("position ASC")
    var picks: MutableList<ServiceRequestItemPick> = mutableListOf(),
)
