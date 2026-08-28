package com.mikeyphw.studyflow.core.model

data class ExamNoticeRevision(
    val id: ExamNoticeRevisionId,
    val studyPlanId: StudyPlanId,
    val studyTargetId: StudyTargetId,
    val label: String,
    val sourceReference: String?,
    val importedAtEpochMillis: Long,
    val normalizedContentSha256: String,
    val parserVersion: String,
    val publicationDateIso: String?,
    val addedCount: Int,
    val removedCount: Int,
    val changedCount: Int,
    val unchangedCount: Int,
) {
    init {
        require(label.isNotBlank()) { "Exam notice revision label cannot be blank." }
        require(normalizedContentSha256.matches(Regex("[0-9a-f]{64}"))) {
            "Exam notice revision SHA-256 must be lowercase hexadecimal."
        }
        require(parserVersion.isNotBlank()) { "Exam notice parser version cannot be blank." }
        publicationDateIso?.let { date -> requireValidIsoDate(date, "Exam notice publication date") }
        require(addedCount >= 0) { "Added count cannot be negative." }
        require(removedCount >= 0) { "Removed count cannot be negative." }
        require(changedCount >= 0) { "Changed count cannot be negative." }
        require(unchangedCount >= 0) { "Unchanged count cannot be negative." }
    }
}

data class StudyCycle(
    val id: StudyCycleId,
    val studyPlanId: StudyPlanId,
    val name: String,
    val enabled: Boolean,
    val currentItemId: StudyCycleItemId?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    init {
        require(name.isNotBlank()) { "Study cycle name cannot be blank." }
        require(updatedAtEpochMillis >= createdAtEpochMillis) {
            "Study cycle update time cannot precede creation time."
        }
    }
}

data class StudyCycleItem(
    val id: StudyCycleItemId,
    val studyCycleId: StudyCycleId,
    val studyPlanId: StudyPlanId,
    val subjectId: SubjectId?,
    val topicId: TopicId?,
    val position: Int,
    val targetDurationMinutes: Int,
    val enabled: Boolean,
    val activityType: StudyActivityType,
    val completionRule: String?,
) {
    init {
        require(position >= 0) { "Study cycle item position cannot be negative." }
        require(targetDurationMinutes in 5..240) {
            "Study cycle target duration must be between 5 and 240 minutes."
        }
        require(topicId == null || subjectId != null) {
            "A topic-bound study cycle item must also reference its subject."
        }
        require(completionRule == null || completionRule.isNotBlank()) {
            "Study cycle completion rule cannot be blank."
        }
    }
}

data class StudyCycleSnapshot(
    val cycle: StudyCycle,
    val items: List<StudyCycleItem>,
) {
    init {
        require(items.all { item ->
            item.studyCycleId == cycle.id && item.studyPlanId == cycle.studyPlanId
        }) { "Study cycle items must belong to their cycle and Study Plan." }
        require(items.map(StudyCycleItem::position).distinct().size == items.size) {
            "Study cycle item positions must be unique."
        }
    }

    val orderedItems: List<StudyCycleItem>
        get() = items.sortedWith(compareBy<StudyCycleItem> { it.position }.thenBy { it.id.value })
}

data class StudyCycleSessionBinding(
    val cycleId: StudyCycleId,
    val itemId: StudyCycleItemId,
) 
