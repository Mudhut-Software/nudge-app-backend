package com.mudhut.nudge.messaging.entities

import com.mudhut.nudge.users.entities.User
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime

enum class SenderSide { CUSTOMER, BUSINESS }

@Entity
@Table(name = "messages")
class Message(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    var conversation: Conversation? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    var sender: User? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_side", nullable = false, length = 16)
    var senderSide: SenderSide = SenderSide.CUSTOMER,

    @Column(name = "body", columnDefinition = "TEXT", nullable = false)
    var body: String = "",

    @CreationTimestamp
    @Column(name = "sent_at", updatable = false)
    var sentAt: LocalDateTime? = null,

    @OneToMany(mappedBy = "message", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("position ASC")
    var attachments: MutableList<MessageAttachment> = mutableListOf(),
)
