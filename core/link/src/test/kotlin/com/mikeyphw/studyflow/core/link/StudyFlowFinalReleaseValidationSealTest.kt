package com.mikeyphw.studyflow.core.link

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StudyFlowFinalReleaseValidationSealTest {
    @Test
    fun finalSealContainsAllSflpTasksAndPlatforms() {
        val seal = StudyFlowSflp24FinalReleaseGate.defaultSeal(createdAtEpochMillis = 1_000L)
        val names = seal.requiredTaskNames()

        assertEquals(StudyFlowSflp24FinalReleaseGate.FinalTaskName, names.first())
        assertEquals(names.distinct(), names)
        assertTrue(StudyFlowSflp23ReleaseGate.RequiredSflpTasks.all { it in names })
        assertTrue(seal.containsFinalGate())
        assertTrue(seal.coversAllPlatforms())
    }

    @Test
    fun finalSealRejectsIncompletePredecessorChain() {
        assertFailsWith<IllegalArgumentException> {
            StudyFlowFinalReleaseValidationSeal(
                sealId = StudyFlowFinalReleaseSealId("bad-seal"),
                workspaceId = StudyFlowWorkspaceId("release-sflp-v1"),
                requiredTasks = listOf(
                    StudyFlowFinalValidationEvidence(
                        taskName = StudyFlowSflp24FinalReleaseGate.FinalTaskName,
                        surface = StudyFlowFinalReleaseSurface.RELEASE_ARTIFACT,
                    ),
                ),
                requiredArtifacts = listOf(".devtool-artifact.json"),
                createdAtEpochMillis = 1_000L,
            )
        }
    }
}
