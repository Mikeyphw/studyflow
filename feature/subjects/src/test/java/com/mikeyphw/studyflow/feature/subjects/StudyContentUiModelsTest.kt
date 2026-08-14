package com.mikeyphw.studyflow.feature.subjects

import com.mikeyphw.studyflow.core.model.ContentProgressStatus
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudySyllabus
import com.mikeyphw.studyflow.core.model.Subject
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.SyllabusFilter
import com.mikeyphw.studyflow.core.model.SyllabusSort
import com.mikeyphw.studyflow.core.model.Topic
import com.mikeyphw.studyflow.core.model.TopicId
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyContentUiModelsTest {
    @Test
    fun topicSearchKeepsItsSubjectAndAncestorsVisible() {
        val syllabus = sampleSyllabus()
        val visible = syllabus.visibleSubjects("individual", SyllabusFilter.ALL, SyllabusSort.POSITION)
        assertEquals(listOf("Law"), visible.map { it.subject.name })
        assertEquals(listOf("Rights"), visible.single().topicTree.map { it.topic.name })
        assertEquals(listOf("Individual rights"), visible.single().topicTree.single().children.map { it.topic.name })
    }


    @Test
    fun needsAttentionIncludesStrongSubjectWhenChildIsWeakAndKeepsAncestors() {
        val base = sampleSyllabus()
        val strongSubject = base.subjects.single().copy(progressPercent = 95)
        val topics = base.topics.map { topic ->
            if (topic.id == TopicId("individual")) topic.copy(masteryPercent = 40) else topic.copy(masteryPercent = 95)
        }
        val visible = base.copy(subjects = listOf(strongSubject), topics = topics)
            .visibleSubjects("", SyllabusFilter.NEEDS_ATTENTION, SyllabusSort.POSITION)
        assertEquals(listOf("Law"), visible.map { it.subject.name })
        assertEquals(listOf("Rights"), visible.single().topicTree.map { it.topic.name })
        assertEquals(listOf("Individual rights"), visible.single().topicTree.single().children.map { it.topic.name })
        assertEquals(1, visible.single().visibleTopicCount)
    }

    @Test
    fun moveItemPreservesEveryValue() {
        assertEquals(listOf("b", "c", "a"), moveItem(listOf("a", "b", "c"), 0, 2))
    }

    @Test
    fun bulkTopicSelectionUsesOneMergedCheckboxRow() {
        val module = Paths.get("src/main/java/com/mikeyphw/studyflow/feature/subjects/StudyContentScreen.kt")
        val root = Paths.get("feature/subjects/src/main/java/com/mikeyphw/studyflow/feature/subjects/StudyContentScreen.kt")
        val source = listOf(module, root).first { it.toFile().isFile }.toFile().readText()
        val block = source.substringAfter("if (bulkMode) {").substringBefore("Column(modifier")
        assertTrue(block.contains(".semantics(mergeDescendants = true) {}"))
        assertTrue(block.contains("role = Role.Checkbox"))
        assertTrue(block.contains("Checkbox(checked = selected, onCheckedChange = null)"))
    }

    private fun sampleSyllabus(): StudySyllabus {
        val planId = StudyPlanId("plan")
        val subjectId = SubjectId("law")
        return StudySyllabus(
            studyPlanId = planId,
            subjects = listOf(
                Subject(
                    subjectId,
                    planId,
                    "Law",
                    "",
                    null,
                    5,
                    2.0,
                    60,
                    40,
                    0,
                    ContentProgressStatus.STUDYING,
                    0,
                    false,
                ),
            ),
            topics = listOf(
                Topic(
                    TopicId("rights"),
                    planId,
                    subjectId,
                    null,
                    "Rights",
                    "",
                    4,
                    3,
                    null,
                    30,
                    40,
                    40,
                    ContentProgressStatus.STUDYING,
                    0,
                    false,
                ),
                Topic(
                    TopicId("individual"),
                    planId,
                    subjectId,
                    TopicId("rights"),
                    "Individual rights",
                    "",
                    4,
                    3,
                    null,
                    30,
                    40,
                    40,
                    ContentProgressStatus.STUDYING,
                    0,
                    false,
                ),
            ),
        )
    }
}
