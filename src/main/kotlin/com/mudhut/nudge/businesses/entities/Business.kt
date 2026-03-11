package com.mudhut.nudge.businesses.entities

import com.mudhut.nudge.users.entities.User
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

@Entity
@Table(name = "businesses")
class Business(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @field:NotBlank
    var name: String? = null,

    var description: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    var owner: User? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    var category: BusinessCategory? = null,

    @OneToMany(mappedBy = "business", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var phoneNumbers: MutableList<BusinessPhoneNumber> = mutableListOf(),

    var email: String? = null,

    var logoUrl: String? = null,

    var address: String? = null,

    @field:NotBlank
    var serviceArea: String? = null,

    @Enumerated(EnumType.STRING)
    var status: BusinessStatus = BusinessStatus.ACTIVE,

    @CreationTimestamp
    var createdAt: LocalDateTime? = null,

    @UpdateTimestamp
    var updatedAt: LocalDateTime? = null
)
