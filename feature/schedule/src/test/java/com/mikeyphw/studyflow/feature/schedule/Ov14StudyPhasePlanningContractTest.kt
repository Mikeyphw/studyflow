package com.mikeyphw.studyflow.feature.schedule

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class Ov14StudyPhasePlanningContractTest {
    private fun repoFile(path: String): String {
        var current = File(requireNotNull(System.getProperty("user.dir")))
        repeat(8) {
            val candidate = File(current, path)
            if (candidate.isFile) return candidate.readText()
            current = current.parentFile ?: return@repeat
        }
        error("Repository file not found: $path")
    }

    @Test
    fun `schedule editor persists phases and applies active allocation to generation`() {
        val screen = repoFile(
            "feature/schedule/src/main/java/com/mikeyphw/studyflow/feature/schedule/StudyScheduleScreen.kt",
        )
        assertTrue("StudyPhasePlanCodec.decode" in screen)
        assertTrue("savePhasePlan" in screen)
        assertTrue("effectiveStrategyForDate" in screen)
        assertTrue("StudyPhaseEditorDialog" in screen)
        assertTrue("study_phase_allocation_notice" in screen)
    }

    @Test
    fun `BB phase ratios are visibly non official suggestions`() {
        val phases = repoFile("core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyPhasePlanning.kt")
        assertTrue("allocationIsStudyFlowSuggested = true" in phases)
        assertTrue("questionGoal = 300" in phases)
        assertTrue("questionGoal = 1_000" in phases)
        assertTrue("mockExamGoal = 5" in phases)
    }
}
