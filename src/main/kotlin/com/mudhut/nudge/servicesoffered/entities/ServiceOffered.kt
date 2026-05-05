package com.mudhut.nudge.servicesoffered.entities

import com.mudhut.nudge.businesses.entities.Business
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.math.BigDecimal
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

    @Column(name = "cover_image_url", nullable = false)
    var coverImageUrl: String? = null,

    @Column(name = "cover_image_public_id", nullable = false)
    var coverImagePublicId: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "price_mode", nullable = false, length = 16)
    var priceMode: PriceMode? = null,

    @Column(name = "price_amount", precision = 19, scale = 2)
    var priceAmount: BigDecimal? = null,

    @Column(name = "price_currency", length = 3)
    var priceCurrency: String? = null,

    @Column(name = "price_unit", length = 32)
    var priceUnit: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: ServiceOfferedStatus = ServiceOfferedStatus.ACTIVE,

    @OneToMany(
        mappedBy = "service",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    @OrderBy("position ASC")
    var galleryImages: MutableList<ServiceOfferedImage> = mutableListOf(),

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null
)
