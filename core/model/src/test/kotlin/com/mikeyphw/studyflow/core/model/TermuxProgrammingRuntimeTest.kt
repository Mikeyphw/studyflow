package com.mikeyphw.studyflow.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxProgrammingRuntimeTest {
    @Test
    fun `workspace slug is sanitized and confined`() {
        assertEquals("android-kotlin", sanitizeTermuxWorkspaceSlug("Android Kotlin"))
        assertThrows(IllegalArgumentException::class.java) {
            TermuxWorkspace(courseSlug = "../escape")
        }
    }

    @Test
    fun `command templates require whitelisted executables and block shell fragments`() {
        assertTrue(isAllowedTermuxCommandTemplate("python -m pytest"))
        assertTrue(isAllowedTermuxCommandTemplate("npm test"))
        assertFalse(isAllowedTermuxCommandTemplate("curl https://example.invalid"))
        assertFalse(isAllowedTermuxCommandTemplate("python main.py && rm -rf ~/studyflow"))
    }

    @Test
    fun `run command requests require an allowed command template`() {
        val request = TermuxCommandRequest(
            id = "run-1",
            studyPlanId = StudyPlanId("plan"),
            exerciseId = null,
            workspace = TermuxWorkspace(courseSlug = "android-kotlin"),
            action = TermuxBridgeAction.RUN_COMMAND,
            commandTemplate = TermuxCommandTemplate(
                id = "python-test",
                label = "Run pytest",
                language = ProgrammingLanguage.PYTHON,
                commandLine = "python -m pytest",
            ),
            createdAtEpochMillis = 1L,
        )

        assertEquals(TermuxExecutionState.QUEUED, request.state)
    }

    @Test
    fun `runtime policy requires explicit user action`() {
        val policy = TermuxRuntimePolicy.defaultForProgrammingCourse(ProgrammingLanguage.PYTHON)

        assertTrue(TermuxBridgeAction.RUN_COMMAND in policy.allowedActions)
        assertTrue(policy.requiresExplicitUserAction)
        assertTrue(policy.allowedTemplates.any { template -> template.commandLine == "python -m pytest" })
    }
}
