package com.mudhut.nudge.servicerequests.entities

import com.mudhut.nudge.packagesoffered.entities.PackageOffered
import com.mudhut.nudge.servicesoffered.entities.ServiceOffered
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
@Table(name = "service_request_items")
class ServiceRequestItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    var request: ServiceRequest? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    var service: ServiceOffered? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "package_id")
    var packageOffered: PackageOffered? = null,

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
)
