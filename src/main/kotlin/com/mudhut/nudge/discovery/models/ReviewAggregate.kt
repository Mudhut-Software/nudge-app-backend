package com.mudhut.nudge.discovery.models

/** Projection for the per-business rating aggregate (batch GROUP BY over the current page). */
interface ReviewAggregate {
    fun getBusinessId(): Long
    fun getAverage(): Double
    fun getCount(): Long
}
