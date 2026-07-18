package com.mudhut.nudge.messaging.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

/** An image attached to a chat [Message]; stored on Cloudinary, snapshot at send time. */
@Entity
@Table(name = "message_attachments")
class MessageAttachment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    var message: Message? = null,

    @Column(nullable = false, length = 1024)
    var url: String = "",

    @Column(name = "public_id", nullable = false, length = 512)
    var publicId: String = "",

    var width: Int? = null,

    var height: Int? = null,

    @Column(nullable = false)
    var position: Int = 0,
)
