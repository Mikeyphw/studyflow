package com.mikeyphw.studyflow.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgrammingCourseModelTest {
    @Test
    fun `programming course mode enables code exercise and termux runtime capabilities`() {
        val capabilities = StudyContentMode.PROGRAMMING_COURSE.capabilities

        assertTrue(capabilities.supportsCodeBlocks)
        assertTrue(capabilities.supportsCodingExercises)
        assertTrue(capabilities.supportsTermuxRuntime)
    }

    @Test
    fun `language can be resolved from names and file extensions`() {
        assertEquals(ProgrammingLanguage.KOTLIN, ProgrammingLanguage.fromNameOrExtension("kt"))
        assertEquals(ProgrammingLanguage.PYTHON, ProgrammingLanguage.fromNameOrExtension("Python"))
        assertEquals(ProgrammingLanguage.OTHER, ProgrammingLanguage.fromNameOrExtension("unknown"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `workspace paths cannot escape the course root`() {
        ProgrammingCourseProfile(
            id = ProgrammingCourseProfileId("profile"),
            studyPlanId = StudyPlanId("plan"),
            primaryLanguage = ProgrammingLanguage.KOTLIN,
            level = ProgrammingCourseLevel.BEGINNER,
            repositoryUri = null,
            workspaceRelativePath = "../escape",
            buildSystem = null,
            packageManager = null,
            tags = emptyList(),
            createdAtEpochMillis = 0L,
            updatedAtEpochMillis = 0L,
        )
    }

    @Test
    fun `termux command templates reject shell chaining`() {
        assertTrue(isSafeTermuxCommandTemplate("python -m pytest"))
        assertTrue(isSafeTermuxCommandTemplate("gradle test"))
        assertFalse(isSafeTermuxCommandTemplate("python main.py && rm -rf ."))
        assertFalse(isSafeTermuxCommandTemplate("python main.py > output.txt"))
    }

    @Test
    fun `runtime preference defaults to disabled safe workspace`() {
        val preference = TermuxRuntimePreference.disabled(StudyPlanId("course"))

        assertFalse(preference.enabled)
        assertEquals("~/studyflow/courses", preference.workspaceRoot)
        assertTrue(preference.captureStdout)
        assertTrue(preference.captureStderr)
    }
}
