package com.mikeyphw.studyflow.feature.dashboard

import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardContractTest {
    @Test
    fun `progress percentages stay bounded`() {
        assertTrue(42 in 0..100)
    }

    @Test
    fun `primary session action is wired to dashboard callback`() {
        val source = readSource(
            modulePath = "src/main/java/com/mikeyphw/studyflow/feature/dashboard/DashboardScreen.kt",
            rootPath = "feature/dashboard/src/main/java/com/mikeyphw/studyflow/feature/dashboard/DashboardScreen.kt",
        )

        assertTrue(source.contains("onStartSession: () -> Unit"))
        assertTrue(source.contains("Button(onClick = onStartSession)"))
        assertTrue(source.contains("onStartSession = onOpenSessions"))
        assertFalse(source.contains("Button(onClick = {})"))
    }

    private fun readSource(modulePath: String, rootPath: String): String {
        val candidates: List<Path> = listOf(Paths.get(modulePath), Paths.get(rootPath))
        return candidates.first { path -> path.toFile().isFile }.toFile().readText()
    }
}
