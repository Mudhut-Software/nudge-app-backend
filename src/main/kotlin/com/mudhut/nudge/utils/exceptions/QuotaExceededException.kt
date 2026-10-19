package com.mudhut.nudge.utils.exceptions

/** The provider has used their monthly job allowance. */
class QuotaExceededException(message: String) : RuntimeException(message)
