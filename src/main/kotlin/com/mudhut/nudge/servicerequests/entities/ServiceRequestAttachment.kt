package com.mudhut.nudge.servicerequests.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "service_request_attachments")
class ServiceRequestAttachment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    var request: ServiceRequest? = null,

    @Column(nullable = false)
    var url: String? = null,

    @Column(name = "public_id", nullable = false)
    var publicId: String? = null,

    @Column(nullable = false, length = 16)
    var kind: String? = null,

    @Column(nullable = false)
    var position: Int = 0,
)
