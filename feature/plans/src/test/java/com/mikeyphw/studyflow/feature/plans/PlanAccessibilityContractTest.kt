package com.mikeyphw.studyflow.feature.plans

import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanAccessibilityContractTest {
    @Test
    fun `plan switches expose one row toggle target`() {
        val create = read("CreatePlanScreen.kt")
        val settings = read("PlanSettingsScreen.kt")
        val portability = read("PlanPortabilityScreen.kt")
        assertTrue(create.contains(".semantics(mergeDescendants = true) {}"))
        assertTrue(create.contains("role = Role.Switch"))
        assertTrue(create.contains("Switch(checked = checked, onCheckedChange = null)"))
        assertTrue(settings.contains(".semantics(mergeDescendants = true) {}"))
        assertTrue(settings.contains("role = Role.Switch"))
        assertTrue(settings.contains("onCheckedChange = null"))
        assertTrue(portability.contains(".semantics(mergeDescendants = true) {}"))
        assertTrue(portability.contains("role = Role.Checkbox"))
        assertTrue(portability.contains("Checkbox(checked = checked, onCheckedChange = null)"))
    }

    private fun read(name: String): String {
        val module = Paths.get("src/main/java/com/mikeyphw/studyflow/feature/plans/$name")
        val root = Paths.get("feature/plans/src/main/java/com/mikeyphw/studyflow/feature/plans/$name")
        return listOf(module, root).first { it.toFile().isFile }.toFile().readText()
    }
}
