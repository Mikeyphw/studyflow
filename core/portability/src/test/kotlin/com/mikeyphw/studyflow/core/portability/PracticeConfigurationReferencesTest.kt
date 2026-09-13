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
            questionSnapshotV1=cTE|MULTIPLE_CHOICE|c3RhdGVtZW50|Y29ycmVjdA||3|false|false|0
            questionSnapshotV1=cTI|TRUE_FALSE|c3RhdGVtZW50|dHJ1ZQ||2|false|false|0
            answer=cTE|Y2hvc2Vu|Y29ycmVjdA|true|12|42|OTHER
        """.trimIndent()

        val remapped = PracticeConfigurationReferences.remapQuestionIds(original) { id -> "private-$id" }

        assertTrue(remapped.startsWith("questionIds=private-q1,private-q2"))
        assertTrue(remapped.contains("questionSnapshotV1=cHJpdmF0ZS1xMQ|MULTIPLE_CHOICE"))
        assertTrue(remapped.contains("answer=cHJpdmF0ZS1xMQ|Y2hvc2Vu|Y29ycmVjdA|true|12|42|OTHER"))
        assertFalse(remapped.contains("questionIds=q1"))
        assertEquals(setOf("private-q1", "private-q2"), PracticeConfigurationReferences.referencedQuestionIds(remapped))
    }

    @Test(expected = StudyPlanPackageException::class)
    fun malformedAnswerRecordFailsClosed() {
        PracticeConfigurationReferences.referencedQuestionIds("answer=broken|record")
    }

    @Test(expected = StudyPlanPackageException::class)
    fun malformedFrozenSnapshotFailsClosed() {
        PracticeConfigurationReferences.referencedQuestionIds("questionSnapshotV1=broken|record")
    }
}
