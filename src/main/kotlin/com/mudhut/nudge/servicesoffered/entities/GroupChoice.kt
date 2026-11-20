package com.mudhut.nudge.servicesoffered.entities

import jakarta.persistence.*
import java.math.BigDecimal

/** One option inside an [OptionGroup]: a link to another catalogue item. */
@Entity
@Table(name = "group_choices")
class GroupChoice(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    var group: OptionGroup? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    var item: ServiceOffered? = null,

    /** Price within this group; null falls back to the linked item's own price. */
    @Column(name = "price_override", precision = 19, scale = 2)
    var priceOverride: BigDecimal? = null,

    @Column(name = "quantity_enabled", nullable = false)
    var quantityEnabled: Boolean = false,

    @Column(name = "max_quantity")
    var maxQuantity: Int? = null,

    @Column(nullable = false)
    var position: Int = 0,
)
