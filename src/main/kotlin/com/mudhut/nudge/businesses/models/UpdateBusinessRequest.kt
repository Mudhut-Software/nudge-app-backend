package com.mudhut.nudge.businesses.models

data class UpdateBusinessRequest(
    var name: String? = null,
    var description: String? = null,
    var categoryId: Long? = null,
    var phone: String? = null,
    var email: String? = null,
    var logoUrl: String? = null,
    var address: String? = null,
    var serviceArea: String? = null
)
