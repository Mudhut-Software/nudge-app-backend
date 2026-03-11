package com.mudhut.nudge

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class NudgeApplication

fun main(args: Array<String>) {
    runApplication<NudgeApplication>(*args)
}
