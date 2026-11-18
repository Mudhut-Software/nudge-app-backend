package com.mudhut.nudge.servicesoffered.entities

import com.mudhut.nudge.businesses.entities.Business
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "services_offered")
class ServiceOffered(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    var business: Business? = null,

    @Column(nullable = false, length = 120)
    var title: String? = null,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(name = "cover_image_url")
    var coverImageUrl: String? = null,

    @Column(name = "cover_image_public_id")
    var coverImagePublicId: String? = null,

    @Column(name = "price_amount", precision = 19, scale = 2)
    var priceAmount: BigDecimal? = null,

    @Column(name = "price_currency", length = 3)
    var priceCurrency: String? = null,

    @Column(name = "price_is_estimate", nullable = false)
    var priceIsEstimate: Boolean = false,

    @Column(name = "estimate_reason", length = 200)
    var estimateReason: String? = null,

    @Column(name = "quantity_enabled", nullable = false)
    var quantityEnabled: Boolean = false,

    @Column(name = "quantity_unit", length = 32)
    var quantityUnit: String? = null,

    @Column(name = "quantity_min", nullable = false)
    var quantityMin: Int = 1,

    @Column(name = "quantity_max")
    var quantityMax: Int? = null,

    @Column(name = "sold_on_its_own", nullable = false)
    var soldOnItsOwn: Boolean = true,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: ServiceOfferedStatus = ServiceOfferedStatus.ACTIVE,

    // Promo metadata (collapsed in from PackageOffered): an optional offer tag
    // and an optional validity window. `isCurrentlyActive` is computed in the
    // service layer from status + window.
    @Enumerated(EnumType.STRING)
    @Column(name = "tag")
    var tag: ServiceOfferedTag? = null,

    @Column(name = "valid_from")
    var validFrom: LocalDate? = null,

    @Column(name = "valid_until")
    var validUntil: LocalDate? = null,

    @OneToMany(
        mappedBy = "service",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    @OrderBy("position ASC")
    var galleryImages: MutableList<ServiceOfferedImage> = mutableListOf(),

    @OneToMany(
        mappedBy = "item",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    @OrderBy("position ASC")
    var groups: MutableList<OptionGroup> = mutableListOf(),

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null
)
