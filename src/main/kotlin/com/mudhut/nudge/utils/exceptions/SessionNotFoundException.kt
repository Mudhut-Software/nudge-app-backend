package com.mudhut.nudge.utils.exceptions

/** A session that does not exist, or does not belong to the caller. Deliberately the same for both. */
class SessionNotFoundException(message: String) : RuntimeException(message)
