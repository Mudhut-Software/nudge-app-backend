package com.mudhut.nudge.servicesoffered.events

/**
 * Published (synchronously, in-transaction) just before GroupChoice rows are deleted,
 * so other modules can clean up their references to them first.
 */
data class GroupChoicesDeletedEvent(val choiceIds: List<Long>)
