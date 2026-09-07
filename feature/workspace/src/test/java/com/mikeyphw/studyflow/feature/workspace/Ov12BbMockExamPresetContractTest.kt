package com.mikeyphw.studyflow.feature.workspace

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ov12BbMockExamPresetContractTest {
    @Test
    fun `BB mock preset reuses public exam blueprint and remains truthful about derived counts`() {
        val preset = repoFile(
            "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/BancoDoBrasilMockExamPreset.kt",
        )
        val blueprint = repoFile(
            "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/PublicExamMockBlueprint.kt",
        )
        val screen = repoFile(
            "feature/workspace/src/main/java/com/mikeyphw/studyflow/feature/workspace/PracticeWorkspaceScreen.kt",
        )
        val en = repoFile("feature/workspace/src/main/res/values/strings.xml")
        val pt = repoFile("feature/workspace/src/main/res/values-pt-rBR/strings.xml")

        assertTrue("DERIVED_FROM_REFERENCE_POINT_WEIGHTS" in preset)
        assertTrue("ObjectiveQuestionCount" in preset)
        assertTrue("ExamBoardFilter = \"Cesgranrio\"" in preset)
        assertTrue("ReferenceFullMockMinutes = 300" in preset)
        assertTrue("minimumOverallScorePercent = null" in preset)
        assertTrue("ExamBlueprintPlanner.preview" in preset)
        assertTrue("missingQuestionCount" in preset)
        assertTrue("type == QuestionType.ESSAY" in blueprint)
        assertTrue("examBoardMatches" in blueprint)
        assertTrue("createMockExamFromBlueprint" in screen)
        assertTrue("matchesReferencePlanName(plan.name)" in screen)
        assertTrue("bb_mock_exam_missing" in screen)
        assertTrue("not presented as official per-subject question counts" in en)
        assertTrue("não são apresentadas como quantidade oficial" in pt)
        assertFalse("70 questions per official distribution" in en)
    }

    private fun repoFile(path: String): String {
        var current = File(requireNotNull(System.getProperty("user.dir")))
        repeat(8) {
            val candidate = File(current, path)
            if (candidate.isFile) return candidate.readText()
            current = current.parentFile ?: return@repeat
        }
        error("Could not resolve repository file: $path")
    }
}
