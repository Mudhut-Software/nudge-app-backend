package com.mudhut.nudge.businesses.entities

import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime

@Entity
@Table(
    name = "business_phone_numbers",
    uniqueConstraints = [UniqueConstraint(columnNames = ["business_id", "phone_number"])]
)
class BusinessPhoneNumber(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @field:NotBlank
    @Column(name = "phone_number")
    var phoneNumber: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", nullable = false)
    var business: Business? = null,

    @CreationTimestamp
    var createdAt: LocalDateTime? = null
)
