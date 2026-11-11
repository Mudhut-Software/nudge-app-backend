package com.mudhut.nudge.discovery.models

interface ReviewMonthRow {
    fun getY(): Int
    fun getM(): Int
    fun getCount(): Long
    fun getRatingSum(): Long
}
