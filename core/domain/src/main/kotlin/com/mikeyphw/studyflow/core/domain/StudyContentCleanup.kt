package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.Annex
import com.mikeyphw.studyflow.core.model.ContentProgressStatus
import com.mikeyphw.studyflow.core.model.ErrorEntry
import com.mikeyphw.studyflow.core.model.Flashcard
import com.mikeyphw.studyflow.core.model.PlannedActivity
import com.mikeyphw.studyflow.core.model.PlannedActivityStatus
import com.mikeyphw.studyflow.core.model.Question
import com.mikeyphw.studyflow.core.model.ReviewItem
import com.mikeyphw.studyflow.core.model.ReviewStatus
import com.mikeyphw.studyflow.core.model.StudyMaterial
import com.mikeyphw.studyflow.core.model.StudyNote
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import com.mikeyphw.studyflow.core.model.StudySyllabus
import com.mikeyphw.studyflow.core.model.VideoResource

/** Phase 60 content-health categories. Domain values stay structured so UI owns all visible prose. */
enum class StudyContentCleanupIssueKind {
    DUPLICATE_QUESTION,
    ORPHAN_TOPIC,
    STALE_RESOURCE,
    STALE_PLAN,
    INCONSISTENT_TAG,
    STALE_REVIEW,
    BROKEN_SCHEDULE_ALIGNMENT,
}

enum class StudyContentCleanupSeverity {
    INFO,
    REVIEW,
    ACTION,
}

enum class StudyContentCleanupPreviewActionKind {
    REVIEW_DUPLICATE,
    OPEN_CONTENT,
    REVIEW_RESOURCE,
    REVIEW_PLAN,
    NORMALIZE_TAG,
    REVIEW_QUEUE_LINK,
    REVIEW_SCHEDULE_LINK,
}

data class StudyContentCleanupIssue(
    val kind: StudyContentCleanupIssueKind,
    val severity: StudyContentCleanupSeverity,
    val count: Int,
    val affectedIds: List<String>,
    val canonicalValue: String? = null,
) {
    init {
        require(count > 0) { "Cleanup issue count must be positive." }
        require(affectedIds.isNotEmpty()) { "Cleanup issue must identify affected content." }
    }
}

data class StudyContentCleanupPreviewAction(
    val kind: StudyContentCleanupPreviewActionKind,
    val issueKind: StudyContentCleanupIssueKind,
    val affectedCount: Int,
    val destructive: Boolean = false,
    val requiresConfirmation: Boolean = true,
) {
    init {
        require(affectedCount > 0) { "Cleanup preview action count must be positive." }
    }
}

data class StudyContentCleanupReport(
    val generatedAtEpochMillis: Long,
    val healthScorePercent: Int,
    val issues: List<StudyContentCleanupIssue>,
    val previewActions: List<StudyContentCleanupPreviewAction>,
) {
    init {
        require(generatedAtEpochMillis >= 0L) { "Cleanup report time cannot be negative." }
        require(healthScorePercent in 0..100) { "Cleanup health score must be between 0 and 100." }
        require(previewActions.none(StudyContentCleanupPreviewAction::destructive)) {
            "Phase 60 cleanup previews must remain non-destructive."
        }
    }

    val issueCount: Int = issues.sumOf(StudyContentCleanupIssue::count)
    val actionCount: Int = issues.filter { it.severity == StudyContentCleanupSeverity.ACTION }
        .sumOf(StudyContentCleanupIssue::count)
    val reviewCount: Int = issues.filter { it.severity == StudyContentCleanupSeverity.REVIEW }
        .sumOf(StudyContentCleanupIssue::count)
    val infoCount: Int = issues.filter { it.severity == StudyContentCleanupSeverity.INFO }
        .sumOf(StudyContentCleanupIssue::count)
}

data class StudyContentCleanupInput(
    val plan: StudyPlan,
    val syllabus: StudySyllabus,
    val questions: List<Question>,
    val materials: List<StudyMaterial>,
    val videos: List<VideoResource>,
    val annexes: List<Annex>,
    val reviewItems: List<ReviewItem>,
    val flashcards: List<Flashcard>,
    val errorEntries: List<ErrorEntry>,
    val plannedActivities: List<PlannedActivity>,
    val notes: List<StudyNote>,
    val nowEpochMillis: Long,
)

object StudyContentCleanupEngine {
    private const val STALE_PLAN_MILLIS = 60L * 86_400_000L
    private const val STALE_RESOURCE_MILLIS = 90L * 86_400_000L

    fun inspect(input: StudyContentCleanupInput): StudyContentCleanupReport {
        val issues = buildList {
            addAll(duplicateQuestionIssues(input.questions))
            addAll(orphanTopicIssues(input))
            addAll(staleResourceIssues(input))
            stalePlanIssue(input)?.let(::add)
            addAll(inconsistentTagIssues(input))
            addAll(staleReviewIssues(input))
            addAll(brokenScheduleIssues(input))
        }.sortedWith(
            compareByDescending<StudyContentCleanupIssue> { it.severity.priority }
                .thenBy { it.kind.ordinal },
        )
        return StudyContentCleanupReport(
            generatedAtEpochMillis = input.nowEpochMillis,
            healthScorePercent = healthScore(issues),
            issues = issues,
            previewActions = issues.map(::previewAction),
        )
    }

    private fun duplicateQuestionIssues(questions: List<Question>): List<StudyContentCleanupIssue> = questions
        .groupBy(::duplicateQuestionKey)
        .values
        .filter { group -> group.size > 1 }
        .map { group ->
            StudyContentCleanupIssue(
                kind = StudyContentCleanupIssueKind.DUPLICATE_QUESTION,
                severity = StudyContentCleanupSeverity.REVIEW,
                count = group.size - 1,
                affectedIds = group.sortedBy(Question::createdAtEpochMillis).map { it.id.value },
                canonicalValue = group.minByOrNull(Question::createdAtEpochMillis)?.id?.value,
            )
        }

    private fun orphanTopicIssues(input: StudyContentCleanupInput): List<StudyContentCleanupIssue> {
        val childTopicIds = input.syllabus.topics.mapNotNull { it.parentTopicId }.toSet()
        val referencedTopicIds = buildSet {
            addAll(input.questions.mapNotNull { it.topicId })
            addAll(input.materials.mapNotNull { it.topicId })
            addAll(input.videos.mapNotNull { it.topicId })
            addAll(input.annexes.mapNotNull { it.topicId })
            addAll(input.reviewItems.mapNotNull { it.topicId })
            addAll(input.flashcards.mapNotNull { it.topicId })
            addAll(input.errorEntries.mapNotNull { it.topicId })
            addAll(input.plannedActivities.mapNotNull { it.topicId })
            addAll(input.notes.mapNotNull { it.topicId })
        }
        val orphanIds = input.syllabus.topics.filter { topic ->
            !topic.archived &&
                topic.status != ContentProgressStatus.ARCHIVED &&
                topic.id !in childTopicIds &&
                topic.id !in referencedTopicIds
        }.map { it.id.value }
        return orphanIds.toSingleIssue(
            kind = StudyContentCleanupIssueKind.ORPHAN_TOPIC,
            severity = StudyContentCleanupSeverity.INFO,
        )
    }

    private fun staleResourceIssues(input: StudyContentCleanupInput): List<StudyContentCleanupIssue> {
        val referencedMaterialIds = buildSet {
            addAll(input.plannedActivities.mapNotNull { it.materialId })
            addAll(input.annexes.mapNotNull { it.materialId })
            addAll(input.notes.mapNotNull { it.materialId })
        }
        val referencedVideoIds = input.plannedActivities.mapNotNull { it.videoResourceId }.toSet()
        val cutoff = input.nowEpochMillis - STALE_RESOURCE_MILLIS
        val staleIds = buildList {
            input.materials.filter { material ->
                material.archived &&
                    material.id !in referencedMaterialIds &&
                    material.updatedAtEpochMillis <= cutoff
            }.forEach { add("material:${it.id.value}") }
            input.videos.filter { video ->
                video.completed &&
                    video.id !in referencedVideoIds &&
                    video.updatedAtEpochMillis <= cutoff
            }.forEach { add("video:${it.id.value}") }
        }
        return staleIds.toSingleIssue(
            kind = StudyContentCleanupIssueKind.STALE_RESOURCE,
            severity = StudyContentCleanupSeverity.INFO,
        )
    }

    private fun stalePlanIssue(input: StudyContentCleanupInput): StudyContentCleanupIssue? {
        if (input.plan.status !in setOf(StudyPlanStatus.ACTIVE, StudyPlanStatus.PAUSED)) return null
        val latestEvidence = buildList {
            add(input.plan.updatedAtEpochMillis)
            addAll(input.questions.map(Question::updatedAtEpochMillis))
            addAll(input.materials.map(StudyMaterial::updatedAtEpochMillis))
            addAll(input.videos.map(VideoResource::updatedAtEpochMillis))
            addAll(input.annexes.map(Annex::updatedAtEpochMillis))
            addAll(input.flashcards.map(Flashcard::updatedAtEpochMillis))
            addAll(input.errorEntries.map(ErrorEntry::updatedAtEpochMillis))
            addAll(input.notes.map(StudyNote::updatedAtEpochMillis))
            addAll(input.reviewItems.mapNotNull(ReviewItem::completedAtEpochMillis))
            addAll(input.plannedActivities.mapNotNull(PlannedActivity::completedAtEpochMillis))
        }.maxOrNull() ?: input.plan.updatedAtEpochMillis
        if (input.nowEpochMillis - latestEvidence < STALE_PLAN_MILLIS) return null
        return StudyContentCleanupIssue(
            kind = StudyContentCleanupIssueKind.STALE_PLAN,
            severity = StudyContentCleanupSeverity.REVIEW,
            count = 1,
            affectedIds = listOf(input.plan.id.value),
        )
    }

    private fun inconsistentTagIssues(input: StudyContentCleanupInput): List<StudyContentCleanupIssue> {
        val tags = buildList {
            input.questions.forEach { question -> question.tags.forEach { add(question.id.value to it) } }
            input.materials.forEach { material -> material.tags.forEach { add(material.id.value to it) } }
            input.flashcards.forEach { flashcard -> flashcard.tags.forEach { add(flashcard.id.value to it) } }
            input.notes.forEach { note -> note.tags.forEach { add(note.id.value to it) } }
        }
        return tags.groupBy { (_, tag) -> normalizedTag(tag) }
            .filterKeys { key -> key.isNotBlank() }
            .values
            .mapNotNull { taggedItems ->
                val variants = taggedItems.map { it.second.trim() }.filter { value -> value.isNotBlank() }.distinct()
                if (variants.size < 2) return@mapNotNull null
                val canonical = variants.groupingBy { variant -> variant.lowercase() }.eachCount()
                    .maxByOrNull { entry -> entry.value }?.key ?: variants.first().lowercase()
                StudyContentCleanupIssue(
                    kind = StudyContentCleanupIssueKind.INCONSISTENT_TAG,
                    severity = StudyContentCleanupSeverity.INFO,
                    count = variants.size,
                    affectedIds = taggedItems.map { it.first }.distinct(),
                    canonicalValue = canonical,
                )
            }
    }

    private fun staleReviewIssues(input: StudyContentCleanupInput): List<StudyContentCleanupIssue> {
        val subjectIds = input.syllabus.subjects
            .filter { subject -> !subject.archived && subject.status != ContentProgressStatus.ARCHIVED }
            .map { it.id }
            .toSet()
        val topicIds = input.syllabus.topics
            .filter { topic -> !topic.archived && topic.status != ContentProgressStatus.ARCHIVED }
            .map { it.id }
            .toSet()
        val questionIds = input.questions.map { it.id }.toSet()
        val flashcardIds = input.flashcards.filterNot(Flashcard::suspended).map { it.id }.toSet()
        val errorIds = input.errorEntries.map { it.id }.toSet()
        val staleIds = input.reviewItems.filter { review ->
            val subjectId = review.subjectId
            val topicId = review.topicId
            val questionId = review.questionId
            val flashcardId = review.flashcardId
            val errorEntryId = review.errorEntryId
            review.status in setOf(ReviewStatus.SCHEDULED, ReviewStatus.DUE) && (
                (subjectId != null && subjectId !in subjectIds) ||
                    (topicId != null && topicId !in topicIds) ||
                    (questionId != null && questionId !in questionIds) ||
                    (flashcardId != null && flashcardId !in flashcardIds) ||
                    (errorEntryId != null && errorEntryId !in errorIds)
                )
        }.map { it.id.value }
        return staleIds.toSingleIssue(
            kind = StudyContentCleanupIssueKind.STALE_REVIEW,
            severity = StudyContentCleanupSeverity.ACTION,
        )
    }

    private fun brokenScheduleIssues(input: StudyContentCleanupInput): List<StudyContentCleanupIssue> {
        val activeSubjects = input.syllabus.subjects.filterNot { it.archived }.map { it.id }.toSet()
        val activeTopics = input.syllabus.topics.filterNot { it.archived }.map { it.id }.toSet()
        val activeMaterials = input.materials.filterNot { it.archived }.map { it.id }.toSet()
        val videoIds = input.videos.map { it.id }.toSet()
        val brokenIds = input.plannedActivities.filter { activity ->
            val subjectId = activity.subjectId
            val topicId = activity.topicId
            val materialId = activity.materialId
            val videoResourceId = activity.videoResourceId
            activity.status in setOf(PlannedActivityStatus.PLANNED, PlannedActivityStatus.IN_PROGRESS) && (
                (subjectId != null && subjectId !in activeSubjects) ||
                    (topicId != null && topicId !in activeTopics) ||
                    (materialId != null && materialId !in activeMaterials) ||
                    (videoResourceId != null && videoResourceId !in videoIds)
                )
        }.map { it.id.value }
        return brokenIds.toSingleIssue(
            kind = StudyContentCleanupIssueKind.BROKEN_SCHEDULE_ALIGNMENT,
            severity = StudyContentCleanupSeverity.ACTION,
        )
    }

    private fun duplicateQuestionKey(question: Question): String = listOf(
        normalizedText(question.statement),
        normalizedText(question.correctAnswer),
        normalizedText(question.source.orEmpty()),
        normalizedText(question.examBoard.orEmpty()),
        question.tags.map(::normalizedTag).filter { tag -> tag.isNotBlank() }.sorted().joinToString("|"),
    ).joinToString("\u001f")

    private fun normalizedText(value: String): String = value.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun normalizedTag(value: String): String = normalizedText(value)

    private fun List<String>.toSingleIssue(
        kind: StudyContentCleanupIssueKind,
        severity: StudyContentCleanupSeverity,
    ): List<StudyContentCleanupIssue> = if (isEmpty()) {
        emptyList()
    } else {
        listOf(
            StudyContentCleanupIssue(
                kind = kind,
                severity = severity,
                count = size,
                affectedIds = distinct(),
            ),
        )
    }

    private fun previewAction(issue: StudyContentCleanupIssue): StudyContentCleanupPreviewAction =
        StudyContentCleanupPreviewAction(
            kind = when (issue.kind) {
                StudyContentCleanupIssueKind.DUPLICATE_QUESTION ->
                    StudyContentCleanupPreviewActionKind.REVIEW_DUPLICATE
                StudyContentCleanupIssueKind.ORPHAN_TOPIC -> StudyContentCleanupPreviewActionKind.OPEN_CONTENT
                StudyContentCleanupIssueKind.STALE_RESOURCE -> StudyContentCleanupPreviewActionKind.REVIEW_RESOURCE
                StudyContentCleanupIssueKind.STALE_PLAN -> StudyContentCleanupPreviewActionKind.REVIEW_PLAN
                StudyContentCleanupIssueKind.INCONSISTENT_TAG -> StudyContentCleanupPreviewActionKind.NORMALIZE_TAG
                StudyContentCleanupIssueKind.STALE_REVIEW -> StudyContentCleanupPreviewActionKind.REVIEW_QUEUE_LINK
                StudyContentCleanupIssueKind.BROKEN_SCHEDULE_ALIGNMENT ->
                    StudyContentCleanupPreviewActionKind.REVIEW_SCHEDULE_LINK
            },
            issueKind = issue.kind,
            affectedCount = issue.count,
        )

    private fun healthScore(issues: List<StudyContentCleanupIssue>): Int {
        val penalty = issues.sumOf { issue ->
            issue.count * when (issue.severity) {
                StudyContentCleanupSeverity.INFO -> 2
                StudyContentCleanupSeverity.REVIEW -> 5
                StudyContentCleanupSeverity.ACTION -> 10
            }
        }
        return (100 - penalty).coerceIn(0, 100)
    }

    private val StudyContentCleanupSeverity.priority: Int
        get() = when (this) {
            StudyContentCleanupSeverity.ACTION -> 3
            StudyContentCleanupSeverity.REVIEW -> 2
            StudyContentCleanupSeverity.INFO -> 1
        }
}
