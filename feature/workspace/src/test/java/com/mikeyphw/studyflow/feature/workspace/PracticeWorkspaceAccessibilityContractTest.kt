package com.mikeyphw.studyflow.feature.workspace

import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeWorkspaceAccessibilityContractTest {
    @Test
    fun `mock exam disputed filter is one labeled checkbox row`() {
        val module = Paths.get("src/main/java/com/mikeyphw/studyflow/feature/workspace/PracticeWorkspaceScreen.kt")
        val root = Paths.get("feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PracticeWorkspaceScreen.kt")
        val source = listOf(module, root).first { it.toFile().isFile }.toFile().readText()
        val block = source.substringAfter("item(key = \"mock-disputed\")").substringBefore("item(key = \"mock-preview\")")
        assertTrue(block.contains(".semantics(mergeDescendants = true) {}"))
        assertTrue(block.contains("role = Role.Checkbox"))
        assertTrue(block.contains("Checkbox(checked = includeDisputed, onCheckedChange = null)"))
    }
}
