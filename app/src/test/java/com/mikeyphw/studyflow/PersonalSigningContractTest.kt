package com.mikeyphw.studyflow

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class PersonalSigningContractTest {
    private val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    @Test
    fun `phone uses permanent personal identity for debug and release`() {
        val build = File(root, "app/build.gradle.kts").readText()
        assertTrue(build.contains(".config/studyflow/android-signing.properties"))
        assertTrue(build.contains("STUDYFLOW_ANDROID_SIGNING_PROPERTIES"))
        assertTrue(build.contains("create(\"studyFlowPersonal\")"))
        assertTrue(build.contains("getByName(\"debug\")"))
        assertTrue(build.contains("signingConfigs.getByName(\"studyFlowPersonal\")"))
        assertTrue(build.contains("studyFlowSigningStatus"))
        assertTrue(build.contains("assemblePersonalDebug"))
        assertTrue(build.contains("assemblePersonalRelease"))
    }

    @Test
    fun `signing material is bootstrapped outside repository`() {
        val setup = File(root, "tools/setup-personal-signing.sh").readText()
        assertTrue(setup.contains("${'$'}HOME/.config/studyflow/android-signing.properties"))
        assertTrue(setup.contains("${'$'}HOME/.config/studyflow/studyflow-personal.p12"))
        assertTrue(setup.contains("-keysize 4096"))
        assertTrue(setup.contains("chmod 600"))
    }
}
