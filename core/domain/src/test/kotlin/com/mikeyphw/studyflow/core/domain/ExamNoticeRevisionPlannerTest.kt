package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ContentProgressStatus
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudySyllabus
import com.mikeyphw.studyflow.core.model.StudyTargetId
import com.mikeyphw.studyflow.core.model.Subject
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.Topic
import com.mikeyphw.studyflow.core.model.TopicId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ExamNoticeRevisionPlannerTest {
    private val planId = StudyPlanId("plan")

    @Test
    fun `preview classifies additive removal rename and unchanged changes`() {
        val existing = syllabus()
        val importPreview = ExamNoticeImportPreview(
            source = ExamNoticeImportSource("edital.txt", SyllabusImportFormat.STRUCTURED_TEXT, "source"),
            draft = SyllabusImportDraft(
                listOf(
                    SyllabusSubjectDraft("s1", "Portuguese", topics = listOf(SyllabusTopicDraft("t1", null, "Grammar"))),
                    SyllabusSubjectDraft("s2", "Constitutional Law"),
                ),
            ),
            issues = emptyList(), duplicateSubjects = 0, duplicateTopics = 0,
        )
        val preview = ExamNoticeRevisionPlanner.preview(
            planId = planId,
            targetId = StudyTargetId("target"),
            baseRevisionId = null,
            importPreview = importPreview,
            existing = existing,
        )
        assertTrue(preview.unchangedCount > 0)
        assertTrue(preview.addedCount > 0)
        assertTrue(preview.removedCount > 0)
    }

    @Test
    fun `publication date rejects impossible calendar dates`() {
        assertFailsWith<IllegalArgumentException> {
            ExamNoticeRevisionPlanner.preview(
                planId, StudyTargetId("target"), null,
                ExamNoticeImportPreview(
                    ExamNoticeImportSource("x", SyllabusImportFormat.STRUCTURED_TEXT, "x"),
                    SyllabusImportDraft(listOf(SyllabusSubjectDraft("s", "Portuguese"))),
                    emptyList(), 0, 0,
                ),
                syllabus(), publicationDateIso = "2026-02-30",
            )
        }
    }

    private fun syllabus(): StudySyllabus {
        val subject = Subject(
            SubjectId("subject"), planId, "Portuguese", "", null, 3, 1.0, 60, 50, 70,
            ContentProgressStatus.IN_PROGRESS, 0, false, null,
        )
        val removedSubject = Subject(
            SubjectId("removed-subject"), planId, "Mathematics", "", null, 3, 1.0, 60, 0, 0,
            ContentProgressStatus.NOT_STARTED, 1, false, null,
        )
        val topic = Topic(
            TopicId("topic"), planId, subject.id, null, "Grammar", "", 3, 3, 30, 30, 50, 40,
            ContentProgressStatus.IN_PROGRESS, 0, false, null,
        )
        return StudySyllabus(planId, listOf(subject, removedSubject), listOf(topic))
    }
}
