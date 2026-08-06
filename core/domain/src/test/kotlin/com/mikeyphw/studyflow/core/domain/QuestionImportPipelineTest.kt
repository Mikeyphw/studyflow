package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.QuestionType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionImportPipelineTest {
    private val planId = StudyPlanId("plan")

    @Test
    fun csvImportDetectsHeaderRowsAndExistingDuplicates() {
        val raw = """
            statement,correctAnswer,explanation,options,type,difficulty,tags
            What is 2+2?,4,Basic arithmetic,A|4|5,MULTIPLE_CHOICE,1,math|easy
            What is 3+3?,6,Basic arithmetic,A|6|7,MULTIPLE_CHOICE,2,math
        """.trimIndent()

        val preview = QuestionImportPipeline.preview(
            planId = planId,
            rawText = raw,
            existingQuestions = listOf(question("What is 2+2?", "4")),
        )

        assertEquals(QuestionImportFormat.CSV, preview.format)
        assertEquals(2, preview.drafts.size)
        assertEquals(1, preview.acceptedCount)
        assertEquals(1, preview.rejectedCount)
        assertTrue(preview.drafts.first().duplicateOfExisting)
        assertFalse(preview.drafts.last().duplicateOfExisting)
    }

    @Test
    fun markdownImportBuildsValidatedDrafts() {
        val raw = """
            # What is judicial review?
            Answer: Constitutional control
            Explanation: Courts review compatibility with the constitution.
            Options: Judicial control|Executive veto|Budget review
            Tags: constitution; courts
            Difficulty: 4
        """.trimIndent()

        val preview = QuestionImportPipeline.preview(planId, raw, existingQuestions = emptyList())

        assertEquals(QuestionImportFormat.MARKDOWN, preview.format)
        assertEquals(1, preview.acceptedCount)
        assertEquals(QuestionType.MULTIPLE_CHOICE, preview.drafts.single().type)
        assertEquals(listOf("constitution", "courts"), preview.drafts.single().tags)
    }

    @Test
    fun ankiStyleTsvIsAcceptedAndRepeatedRowsAreSkipped() {
        val raw = "Question one\tAnswer one\tExplained\nQuestion one\tAnswer one\tExplained again"

        val preview = QuestionImportPipeline.preview(planId, raw, existingQuestions = emptyList())

        assertEquals(QuestionImportFormat.TSV, preview.format)
        assertEquals(1, preview.acceptedCount)
        assertEquals(1, preview.duplicateCount)
        assertEquals(QuestionType.SHORT_ANSWER, preview.drafts.first().type)
    }


    @Test
    fun csvImportHandlesQuotedCommasAndEscapedQuotes() {
        val raw = """
            statement,correctAnswer,explanation,options,type
            "Who said ""I study"", today?",A,Quote handling,A|B,MULTIPLE_CHOICE
        """.trimIndent()

        val preview = QuestionImportPipeline.preview(planId, raw, existingQuestions = emptyList())

        assertEquals(1, preview.acceptedCount)
        assertEquals("Who said \"I study\", today?", preview.drafts.single().statement)
    }

    @Test
    fun invalidRowsAreReported() {
        val preview = QuestionImportPipeline.preview(
            planId = planId,
            rawText = "statement,answer,options\n,Answer,A|B",
            existingQuestions = emptyList(),
        )

        assertTrue(preview.hasErrors)
        assertEquals(0, preview.acceptedCount)
    }

    private fun question(statement: String, answer: String) = Question(
        id = QuestionId(statement.lowercase().replace(" ", "-")),
        studyPlanId = planId,
        subjectId = null,
        topicId = null,
        type = QuestionType.MULTIPLE_CHOICE,
        statement = statement,
        options = listOf("A", answer),
        correctAnswer = answer,
        explanation = "Explanation",
        examBoard = null,
        source = null,
        year = null,
        difficulty = 3,
        favorite = false,
        disputed = false,
        tags = emptyList(),
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    )
}
