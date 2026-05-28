package com.mudhut.nudge.servicesoffered.entities

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "service_addons")
class ServiceAddon(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    var service: ServiceOffered? = null,

    @Column(nullable = false, length = 120)
    var title: String? = null,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(name = "cover_image_url", length = 500)
    var coverImageUrl: String? = null,

    @Column(name = "cover_image_public_id", length = 200)
    var coverImagePublicId: String? = null,

    /** null or 0 → "included". > 0 → adds to service total. */
    @Column(name = "price_delta", precision = 19, scale = 2)
    var priceDelta: BigDecimal? = null,

    /** Free-text unit hint, e.g. "per tray". Optional. */
    @Column(name = "price_unit", length = 32)
    var priceUnit: String? = null,

    @Column(name = "default_selected", nullable = false)
    var defaultSelected: Boolean = false,

    @Column(nullable = false)
    var quantifiable: Boolean = false,

    @Column(name = "default_quantity", nullable = false)
    var defaultQuantity: Int = 1,

    /** null = unlimited. */
    @Column(name = "max_quantity")
    var maxQuantity: Int? = null,

    @Column(nullable = false)
    var position: Int = 0,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null,
)
