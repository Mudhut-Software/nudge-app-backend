package com.mudhut.nudge

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class NudgeApplication

fun main(args: Array<String>) {
    runApplication<NudgeApplication>(*args)
}
