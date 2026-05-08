package com.mudhut.nudge.packagesoffered.entities

import com.mudhut.nudge.businesses.entities.Business
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "packages_offered")
class PackageOffered(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    var business: Business? = null,

    @Column(nullable = false, length = 120)
    var title: String? = null,

    @Column(name = "price_amount", precision = 19, scale = 2, nullable = false)
    var priceAmount: BigDecimal? = null,

    @Column(name = "price_currency", length = 3, nullable = false)
    var priceCurrency: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    var tag: PackageOfferedTag? = null,

    @Column(name = "valid_from")
    var validFrom: LocalDate? = null,

    @Column(name = "valid_until")
    var validUntil: LocalDate? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: PackageOfferedStatus = PackageOfferedStatus.ACTIVE,

    @OneToMany(
        mappedBy = "packageOffered",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY,
    )
    @OrderBy("position ASC")
    var items: MutableList<PackageOfferedItem> = mutableListOf(),

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null,
)
