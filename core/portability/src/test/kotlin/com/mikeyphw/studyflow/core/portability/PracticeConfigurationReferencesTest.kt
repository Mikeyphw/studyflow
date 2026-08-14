package com.mikeyphw.studyflow.core.portability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeConfigurationReferencesTest {
    @Test
    fun remapsQuestionListAndAnsweredQuestionIdsWithoutTouchingAnswerPayload() {
        val original = """
            questionIds=q1,q2
            answer=cTE|Y2hvc2Vu|Y29ycmVjdA|true|12|42
        """.trimIndent()

        val remapped = PracticeConfigurationReferences.remapQuestionIds(original) { id -> "private-$id" }

        assertTrue(remapped.startsWith("questionIds=private-q1,private-q2"))
        assertTrue(remapped.contains("answer=cHJpdmF0ZS1xMQ|Y2hvc2Vu|Y29ycmVjdA|true|12|42"))
        assertFalse(remapped.contains("questionIds=q1"))
        assertEquals(setOf("private-q1", "private-q2"), PracticeConfigurationReferences.referencedQuestionIds(remapped))
    }

    @Test(expected = StudyPlanPackageException::class)
    fun malformedAnswerRecordFailsClosed() {
        PracticeConfigurationReferences.referencedQuestionIds("answer=broken|record")
    }
}
