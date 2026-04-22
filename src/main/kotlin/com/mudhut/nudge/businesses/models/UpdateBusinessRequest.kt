package com.mudhut.nudge.businesses.models

data class UpdateBusinessRequest(
    var name: String? = null,
    var description: String? = null,
    var categoryId: Long? = null,
    var phoneNumbers: List<String>? = null,
    var email: String? = null,
    var logoUrl: String? = null,
    var address: String? = null,
    var latitude: Double? = null,
    var longitude: Double? = null,
    var serviceAreas: List<String>? = null
)
