package com.mudhut.nudge.servicesoffered.events

/**
 * Published (synchronously, in-transaction) just before a ServiceAddon row is deleted,
 * so other modules can clean up their references to it first.
 */
data class ServiceAddonDeletedEvent(val addonId: Long)
