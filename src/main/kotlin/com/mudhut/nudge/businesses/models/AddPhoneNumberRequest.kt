package com.mudhut.nudge.businesses.models

import jakarta.validation.constraints.NotBlank

data class AddPhoneNumberRequest(
    @field:NotBlank(message = "Phone number is required")
    val phoneNumber: String? = null
)
