package com.mudhut.nudge.servicesoffered.entities

import jakarta.persistence.*

/**
 * A named set of linked choices on an item ("Choose 3 flavours"). Replaces the old
 * per-service add-ons: each choice points at another item in the catalogue.
 */
@Entity
@Table(name = "option_groups")
class OptionGroup(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    var item: ServiceOffered? = null,

    @Column(nullable = false, length = 80)
    var name: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var selection: GroupSelection = GroupSelection.SINGLE,

    @Column(nullable = false)
    var required: Boolean = false,

    @Column(name = "min_picks")
    var minPicks: Int? = null,

    @Column(name = "max_picks")
    var maxPicks: Int? = null,

    @Column(nullable = false)
    var position: Int = 0,

    @OneToMany(
        mappedBy = "group",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    @OrderBy("position ASC")
    var choices: MutableList<GroupChoice> = mutableListOf(),
)
