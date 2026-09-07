package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ContentProgressStatus
import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.QuestionId
import com.mikeyphw.studyflow.core.model.QuestionType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudySyllabus
import com.mikeyphw.studyflow.core.model.Subject
import com.mikeyphw.studyflow.core.model.SubjectId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BancoDoBrasilMockExamPresetTest {
    private val planId = StudyPlanId("bb-plan")

    @Test
    fun `preset preserves reference point weights and derives exactly seventy question targets`() {
        val blueprint = requireNotNull(
            BancoDoBrasilMockExamPreset.buildBlueprint(
                syllabus = syllabus(portuguese = true),
                title = "BB Agente Comercial",
            ),
        )

        assertEquals(70, blueprint.requestedQuestionCount)
        assertEquals(100.0, blueprint.sections.sumOf { it.weight }, 0.0001)
        assertEquals(listOf(11, 4, 5, 3, 5, 10, 16, 16), blueprint.sections.map { it.questionCount })
        assertEquals("Cesgranrio", blueprint.examBoard)
        assertEquals(300, blueprint.timeLimitMinutes)
        assertEquals(null, blueprint.minimumOverallScorePercent)
        assertEquals(ExamScoringMode.NO_PENALTY, blueprint.scoringPolicy.mode)
    }

    @Test
    fun `preset accepts Portuguese and English localized BB subject names`() {
        assertNotNull(BancoDoBrasilMockExamPreset.buildBlueprint(syllabus(portuguese = true), "BB"))
        assertNotNull(BancoDoBrasilMockExamPreset.buildBlueprint(syllabus(portuguese = false), "BB"))
    }

    @Test
    fun `preview refuses incomplete local question coverage instead of substituting other subjects`() {
        val syllabus = syllabus(portuguese = true)
        val questions = questionsFor(syllabus).dropLast(1)

        val result = requireNotNull(
            BancoDoBrasilMockExamPreset.preview(syllabus, questions, title = "BB mock"),
        )

        assertEquals(1, result.missingQuestionCount)
        assertFalse(result.executable)
    }

    @Test
    fun `preview filters Cesgranrio and keeps essay training separate`() {
        val syllabus = syllabus(portuguese = true)
        val objective = questionsFor(syllabus)
        val wrongBoard = question("wrong-board", syllabus.subjects.first(), "FGV")
        val essay = question(
            id = "essay",
            subject = syllabus.subjects.first(),
            board = "Fundação Cesgranrio",
            type = QuestionType.ESSAY,
        )

        val result = requireNotNull(
            BancoDoBrasilMockExamPreset.preview(
                syllabus = syllabus,
                questions = objective + wrongBoard + essay,
                title = "BB mock",
            ),
        )

        assertTrue(result.executable)
        assertEquals(70, result.preview.selectedQuestions.size)
        assertFalse(result.preview.selectedQuestions.any { it.id.value == "wrong-board" })
        assertFalse(result.preview.selectedQuestions.any { it.type == QuestionType.ESSAY })
        assertEquals(70, result.essayMinimumScorePercent)
    }

    private fun syllabus(portuguese: Boolean): StudySyllabus {
        val names = if (portuguese) {
            listOf(
                "Língua Portuguesa",
                "Língua Inglesa",
                "Matemática",
                "Atualidades do Mercado Financeiro",
                "Matemática Financeira",
                "Conhecimentos Bancários",
                "Conhecimentos de Informática",
                "Vendas e Negociação",
            )
        } else {
            listOf(
                "Portuguese",
                "English",
                "Mathematics",
                "Financial Market Current Affairs",
                "Financial Mathematics",
                "Banking Knowledge",
                "Information Technology",
                "Sales and Negotiation",
            )
        }
        val weights = listOf(15.0, 5.0, 7.5, 5.0, 7.5, 15.0, 22.5, 22.5)
        return StudySyllabus(
            studyPlanId = planId,
            subjects = names.mapIndexed { index, name ->
                Subject(
                    id = SubjectId("subject-$index"),
                    studyPlanId = planId,
                    name = name,
                    description = "",
                    colorArgb = null,
                    priority = 5,
                    examWeight = weights[index],
                    estimatedMinutes = 0,
                    progressPercent = 0,
                    accuracyPercent = 0,
                    status = ContentProgressStatus.NOT_STARTED,
                    position = index,
                    archived = false,
                )
            },
            topics = emptyList(),
        )
    }

    private fun questionsFor(syllabus: StudySyllabus): List<Question> {
        val counts = listOf(11, 4, 5, 3, 5, 10, 16, 16)
        return syllabus.subjects.flatMapIndexed { subjectIndex, subject ->
            List(counts[subjectIndex]) { questionIndex ->
                question(
                    id = "q-$subjectIndex-$questionIndex",
                    subject = subject,
                    board = if (questionIndex % 2 == 0) "Cesgranrio" else "Fundação Cesgranrio",
                )
            }
        }
    }

    private fun question(
        id: String,
        subject: Subject,
        board: String,
        type: QuestionType = QuestionType.MULTIPLE_CHOICE,
    ) = Question(
        id = QuestionId(id),
        studyPlanId = planId,
        subjectId = subject.id,
        topicId = null,
        type = type,
        statement = "Question $id",
        options = if (type == QuestionType.MULTIPLE_CHOICE) listOf("A", "B", "C", "D") else emptyList(),
        correctAnswer = if (type == QuestionType.ESSAY) "essay" else "A",
        explanation = "",
        examBoard = board,
        source = BancoDoBrasilAgenteComercialPreset.ReferenceNotice,
        year = 2022,
        difficulty = 3,
        favorite = false,
        disputed = false,
        tags = emptyList(),
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    )
}
