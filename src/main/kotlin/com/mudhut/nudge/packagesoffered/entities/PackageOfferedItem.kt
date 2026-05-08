package com.mudhut.nudge.packagesoffered.entities

import com.mudhut.nudge.servicesoffered.entities.ServiceOffered
import jakarta.persistence.*

@Entity
@Table(
    name = "package_offered_items",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_package_item_package_service",
            columnNames = ["package_id", "service_id"],
        ),
    ],
)
class PackageOfferedItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "package_id", nullable = false)
    var packageOffered: PackageOffered? = null,

    // FK to ServiceOffered. Cascade-on-service-delete is enforced by the
    // application layer (ServicesOfferedService.deleteService) — no migration
    // tooling yet, so the DB-level ON DELETE CASCADE is deferred.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    var service: ServiceOffered? = null,

    @Column(nullable = false)
    var position: Int = 0,
)
