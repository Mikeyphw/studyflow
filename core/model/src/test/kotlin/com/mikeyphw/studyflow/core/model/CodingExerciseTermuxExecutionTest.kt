package com.mikeyphw.studyflow.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CodingExerciseTermuxExecutionTest {
    @Test
    fun `exercise creates safe Termux run request chain`() {
        val exercise = sampleExercise()
        val plan = exercise.toTermuxExecutionPlan(
            workspace = TermuxWorkspace(courseSlug = "kotlin-basics"),
            runMode = TermuxExerciseRunMode.TEST,
            requestIdPrefix = "exercise-001",
            createdAtEpochMillis = 100L,
        )

        assertEquals(
            listOf(
                TermuxBridgeAction.CREATE_WORKSPACE,
                TermuxBridgeAction.WRITE_EXERCISE_FILES,
                TermuxBridgeAction.RUN_COMMAND,
                TermuxBridgeAction.READ_RESULT,
            ),
            plan.requests.map { it.action },
        )
        assertEquals("python -m pytest", plan.commandTemplate.commandLine)
        assertTrue(plan.files.any { it.relativePath == "main.py" })
        assertEquals("exercise-001-run.request.json", termuxRequestFileName(plan.requests[2].id))
    }

    @Test
    fun `unsafe command template is rejected before a request is created`() {
        assertFailsWith<IllegalArgumentException> {
            sampleExercise(testCommandTemplate = "python -m pytest && curl evil.test")
        }
    }

    @Test
    fun `Termux results update exercise status without marking completion automatically`() {
        val exercise = sampleExercise()
        val passed = TermuxCommandResult(
            requestId = "exercise-001-run",
            state = TermuxExecutionState.SUCCEEDED,
            exitCode = 0,
            stdoutText = "3 passed",
            stderrText = "",
            resultJson = null,
            durationMillis = 842L,
            completedAtEpochMillis = 200L,
        )
        val failed = passed.copy(state = TermuxExecutionState.FAILED, exitCode = 1, stderrText = "assertion failed")

        assertEquals(CodingExerciseStatus.RAN_SUCCESSFULLY, exercise.applyTermuxResult(passed, 200L).status)
        assertEquals(CodingExerciseStatus.FAILED_TESTS, exercise.applyTermuxResult(failed, 201L).status)
        assertFalse(exercise.applyTermuxResult(passed, 200L).status == CodingExerciseStatus.COMPLETED)
    }

    private fun sampleExercise(testCommandTemplate: String = "python -m pytest"): CodingExercise = CodingExercise(
        id = CodingExerciseId("exercise-001"),
        studyPlanId = StudyPlanId("plan-001"),
        subjectId = null,
        topicId = null,
        snippetId = null,
        type = CodingExerciseType.TESTING_TASK,
        title = "Reverse string",
        prompt = "Implement reverse_string.",
        starterCode = "def reverse_string(value):\n    return value[::-1]\n",
        expectedOutput = "3 passed",
        hints = listOf("Use slicing."),
        solutionNotes = "Prefer readable tests before optimizing.",
        language = ProgrammingLanguage.PYTHON,
        difficulty = 2,
        status = CodingExerciseStatus.NOT_STARTED,
        runCommandTemplate = "python main.py",
        testCommandTemplate = testCommandTemplate,
        files = emptyList(),
        tags = listOf("strings"),
        lastResultJson = null,
        retryAtEpochMillis = null,
        createdAtEpochMillis = 100L,
        updatedAtEpochMillis = 100L,
    )
}
