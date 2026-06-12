package com.mudhut.nudge

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules
import org.springframework.modulith.docs.Documenter

class ModularityTests {
    private val modules = ApplicationModules.of(NudgeApplication::class.java)

    @Test
    fun verifiesModuleStructure() {
        modules.verify()
    }

    @Test
    fun writesDocumentation() {
        Documenter(modules).writeDocumentation()
    }
}
