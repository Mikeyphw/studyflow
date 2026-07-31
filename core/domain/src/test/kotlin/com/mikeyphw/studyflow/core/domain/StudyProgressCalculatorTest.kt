package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ContentProgressStatus
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.Subject
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.Topic
import com.mikeyphw.studyflow.core.model.TopicId
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyProgressCalculatorTest {
    @Test
    fun parentAndSubjectProgressAreDerivedFromLeafTopics() {
        val subject = subject("s")
        val parent = topic("parent", subject.id, null, progress = 0, minutes = 10)
        val first = topic("first", subject.id, parent.id, progress = 20, minutes = 30)
        val second = topic("second", subject.id, parent.id, progress = 80, minutes = 70)
        assertEquals(62, StudyProgressCalculator.subjectProgress(subject, listOf(parent, first, second)))
    }

    @Test
    fun planProgressUsesExamWeight() {
        val first = subject("a").copy(progressPercent = 20, examWeight = 1.0)
        val second = subject("b").copy(progressPercent = 80, examWeight = 3.0)
        assertEquals(65, StudyProgressCalculator.planProgress(listOf(first, second)))
    }

    private fun subject(id: String) = Subject(
        id = SubjectId(id), studyPlanId = StudyPlanId("plan"), name = id, description = "",
        colorArgb = null, priority = 3, examWeight = 1.0, estimatedMinutes = 0,
        progressPercent = 0, accuracyPercent = 0, status = ContentProgressStatus.NOT_STARTED,
        position = 0, archived = false,
    )

    private fun topic(id: String, subjectId: SubjectId, parent: TopicId?, progress: Int, minutes: Int) = Topic(
        id = TopicId(id), studyPlanId = StudyPlanId("plan"), subjectId = subjectId,
        parentTopicId = parent, name = id, description = "", priority = 3, difficulty = 3,
        examIncidencePercent = null, estimatedMinutes = minutes, progressPercent = progress,
        masteryPercent = progress, status = ContentProgressStatus.STUDYING, position = 0, archived = false,
    )
}
