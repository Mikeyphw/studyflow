package com.mikeyphw.studyflow.tv

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class PersonalSigningContractTest {
    private val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    @Test
    fun `tv debug and release share permanent personal signing identity`() {
        val build = File(root, "app-tv/build.gradle.kts").readText()
        assertTrue(build.contains(".config/studyflow/android-signing.properties"))
        assertTrue(build.contains("create(\"studyFlowTvPersonal\")"))
        assertTrue(build.contains("getByName(\"debug\")"))
        assertTrue(build.contains("signingConfigs.getByName(\"studyFlowTvPersonal\")"))
        assertTrue(build.contains("studyFlowSigningStatus"))
        assertTrue(build.contains("verifyStudyFlowTvPersonalSigningConfigured"))
        assertTrue(build.contains("assemblePersonalDebug"))
        assertTrue(build.contains("assemblePersonalRelease"))
    }
}
