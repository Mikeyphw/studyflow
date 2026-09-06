package com.mikeyphw.studyflow.feature.workspace

import java.nio.file.Path
import kotlin.io.path.exists
import org.junit.Assert.assertTrue
import org.junit.Test

class Ov09PreEditalCockpitContractTest {
    private fun repoRoot(): Path {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        repeat(8) {
            if (current.resolve("settings.gradle.kts").exists()) return current
            current = current.parent ?: return@repeat
        }
        error("Could not locate StudyFlow repository root")
    }

    private fun source(relative: String): String = repoRoot().resolve(relative).toFile().readText()

    @Test
    fun `Plan overview derives pre-edital state from target and revision history`() {
        val source = source(
            "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PlanWorkspaceScreen.kt",
        )
        assertTrue("observeExamNoticeRevisions(plan.id)" in source)
        assertTrue("PublicExamPreEditalBuilder.build(" in source)
        assertTrue("snapshot.preEdital" in source)
        assertTrue("PublicExamPreEditalCockpit(" in source)
        assertTrue("onReviewPreEditalAssumptions = onManageContent" in source)
    }

    @Test
    fun `pre-edital copy is bilingual and forbids a fabricated official countdown`() {
        val en = source("feature/workspace/src/main/res/values/strings.xml")
        val pt = source("feature/workspace/src/main/res/values-pt-rBR/strings.xml")
        assertTrue("Pre-edital cockpit" in en)
        assertTrue("No official exam countdown is shown" in en)
        assertTrue("Painel pré-edital" in pt)
        assertTrue("não inventa uma contagem regressiva" in pt)
    }
}
