package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ContentProgressStatus
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudySyllabus
import com.mikeyphw.studyflow.core.model.Subject
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.Topic
import com.mikeyphw.studyflow.core.model.TopicId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamNoticeImportParserTest {
    @Test
    fun numberedEditalBuildsNestedHierarchyDeterministically() {
        val source = ExamNoticeImportSource(
            displayName = "edital.txt",
            format = SyllabusImportFormat.STRUCTURED_TEXT,
            text = """
                CONTEÚDO PROGRAMÁTICO
                Disciplina: Direito Constitucional
                1 Direitos fundamentais
                1.1 Direitos individuais
                1.1.1 Remédios constitucionais
                2 Administração pública
            """.trimIndent(),
        )

        val first = ExamNoticeImportParser.preview(PLAN_ID, source, emptySyllabus())
        val second = ExamNoticeImportParser.preview(PLAN_ID, source, emptySyllabus())

        assertEquals(first, second)
        val topics = first.draft.subjects.single().topics
        assertEquals(4, topics.size)
        assertEquals(topics[0].key, topics[1].parentKey)
        assertEquals(topics[1].key, topics[2].parentKey)
        assertEquals(null, topics[3].parentKey)
        assertTrue(first.issues.any { it.kind == ExamNoticeImportIssueKind.UNRECOGNIZED_HEADING })
    }

    @Test
    fun duplicateSubjectsAndTopicsAreVisibleWithoutMutatingSource() {
        val source = ExamNoticeImportSource(
            displayName = "pasted",
            format = SyllabusImportFormat.STRUCTURED_TEXT,
            text = "Disciplina: Direito Constitucional\n1 Direitos fundamentais",
        )
        val original = source.copy()

        val preview = ExamNoticeImportParser.preview(PLAN_ID, source, existingSyllabus())

        assertEquals(original, source)
        assertEquals(original, preview.source)
        assertEquals(1, preview.duplicateSubjects)
        assertEquals(1, preview.duplicateTopics)
        assertEquals(0, preview.destructiveChanges)
        assertTrue(preview.canApply)
    }

    @Test
    fun orphanNumberedTopicIsReportedInsteadOfInventingParent() {
        val source = ExamNoticeImportSource(
            displayName = "edital.txt",
            format = SyllabusImportFormat.STRUCTURED_TEXT,
            text = "Disciplina: Direito Administrativo\n1.2 Atos administrativos",
        )

        val preview = ExamNoticeImportParser.preview(PLAN_ID, source, emptySyllabus())
        val topic = preview.draft.subjects.single().topics.single()

        assertEquals(null, topic.parentKey)
        assertTrue(preview.issues.any { it.kind == ExamNoticeImportIssueKind.ORPHAN_TOPIC })
    }

    @Test
    fun tsvTemplateNormalizesIntoExistingSyllabusDraft() {
        val source = ExamNoticeImportSource(
            displayName = "edital.tsv",
            format = SyllabusImportFormat.TSV,
            text = "subject\ttopic\tparent\nPortuguês\tSintaxe\t\nPortuguês\tConcordância\tSintaxe",
        )

        val preview = ExamNoticeImportParser.preview(PLAN_ID, source, emptySyllabus())
        val topics = preview.draft.subjects.single().topics

        assertEquals(2, topics.size)
        assertEquals(topics[0].key, topics[1].parentKey)
    }

    @Test
    fun malformedOrCrossPlanPreviewFailsBeforeAnyApplyBoundaryExists() {
        assertThrows(IllegalArgumentException::class.java) {
            ExamNoticeImportParser.preview(
                PLAN_ID,
                ExamNoticeImportSource("empty", SyllabusImportFormat.STRUCTURED_TEXT, ""),
                emptySyllabus(),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExamNoticeImportParser.preview(
                PLAN_ID,
                ExamNoticeImportSource(
                    "other",
                    SyllabusImportFormat.STRUCTURED_TEXT,
                    "Disciplina: Português\n1 Sintaxe",
                ),
                StudySyllabus(StudyPlanId("other-plan"), emptyList(), emptyList()),
            )
        }
    }

    @Test
    fun additivePreviewNeverAdvertisesDestructiveChanges() {
        val source = ExamNoticeImportSource(
            displayName = "edital.csv",
            format = SyllabusImportFormat.CSV,
            text = "subject,topic\nPortuguês,Sintaxe",
        )

        val preview = ExamNoticeImportParser.preview(PLAN_ID, source, existingSyllabus())

        assertEquals(0, preview.destructiveChanges)
        assertFalse(preview.draft.subjects.isEmpty())
    }

    private fun emptySyllabus() = StudySyllabus(PLAN_ID, emptyList(), emptyList())

    private fun existingSyllabus() = StudySyllabus(
        studyPlanId = PLAN_ID,
        subjects = listOf(existingSubject()),
        topics = listOf(existingTopic()),
    )

    private fun existingSubject() = Subject(
        id = SubjectId("constitutional"),
        studyPlanId = PLAN_ID,
        name = "Direito Constitucional",
        description = "",
        colorArgb = null,
        priority = 3,
        examWeight = 1.0,
        estimatedMinutes = 0,
        progressPercent = 0,
        accuracyPercent = 0,
        status = ContentProgressStatus.NOT_STARTED,
        position = 0,
        archived = false,
    )

    private fun existingTopic() = Topic(
        id = TopicId("fundamental-rights"),
        studyPlanId = PLAN_ID,
        subjectId = SubjectId("constitutional"),
        parentTopicId = null,
        name = "Direitos fundamentais",
        description = "",
        priority = 3,
        difficulty = 3,
        examIncidencePercent = null,
        estimatedMinutes = 0,
        progressPercent = 0,
        masteryPercent = 0,
        status = ContentProgressStatus.NOT_STARTED,
        position = 0,
        archived = false,
    )

    private companion object {
        val PLAN_ID = StudyPlanId("plan")
    }
}
