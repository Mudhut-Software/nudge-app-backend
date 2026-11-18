package com.mudhut.nudge.servicerequests.entities

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
@Table(name = "service_request_item_addons")
class ServiceRequestItemAddon(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    var item: ServiceRequestItem? = null,

    /** Filled at submit. Until then, null. */
    @Column(name = "snapshot_title", length = 120)
    var snapshotTitle: String? = null,

    @Column(name = "snapshot_price_delta", precision = 19, scale = 2)
    var snapshotPriceDelta: BigDecimal? = null,

    @Column(name = "snapshot_price_unit", length = 32)
    var snapshotPriceUnit: String? = null,

    @Column(nullable = false)
    var quantity: Int = 1,

    @Column(nullable = false)
    var position: Int = 0,
)
