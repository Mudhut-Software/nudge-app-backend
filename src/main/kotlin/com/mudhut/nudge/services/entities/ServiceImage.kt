package com.mudhut.nudge.services.entities

import jakarta.persistence.*

@Entity
@Table(name = "service_images")
class ServiceImage(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    var service: Service? = null,

    @Column(nullable = false)
    var url: String? = null,

    @Column(name = "public_id", nullable = false)
    var publicId: String? = null,

    @Column(nullable = false)
    var position: Int = 0
)
