package com.mudhut.nudge.servicerequests.entities

import com.mudhut.nudge.servicesoffered.entities.GroupChoice
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

/**
 * One choice the customer picked inside one of an item's option groups.
 *
 * [choice] is a soft pointer: GroupChoicesDeletionListener nulls it when the choice is deleted, and
 * the snapshot columns keep what was sent. The snapshot is written when the pick is stored (as a
 * fallback label for a draft whose choice later disappears) and rewritten at submit; drafts are
 * displayed from the live choice, sent requests from the snapshot.
 */
@Entity
@Table(name = "service_request_item_picks")
class ServiceRequestItemPick(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    var item: ServiceRequestItem? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "choice_id")
    var choice: GroupChoice? = null,

    /** The choice's group, kept so a deleted choice can still be reported against its group. */
    @Column(name = "group_id")
    var groupId: Long? = null,

    @Column(name = "snapshot_group_name", length = 80)
    var snapshotGroupName: String? = null,

    @Column(name = "snapshot_title", length = 120)
    var snapshotTitle: String? = null,

    /** Effective price in the group (override, else the linked item's price); null = included. */
    @Column(name = "snapshot_price", precision = 19, scale = 2)
    var snapshotPrice: BigDecimal? = null,

    @Column(nullable = false)
    var quantity: Int = 1,

    @Column(nullable = false)
    var position: Int = 0,
)
