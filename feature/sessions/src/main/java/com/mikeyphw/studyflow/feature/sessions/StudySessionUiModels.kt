package com.mikeyphw.studyflow.feature.sessions

import androidx.annotation.StringRes
import com.mikeyphw.studyflow.core.model.StudyActivityType
import com.mikeyphw.studyflow.core.model.StudySessionStatus
import java.util.Locale

fun formatSessionDuration(seconds: Long): String {
    val safeSeconds = seconds.coerceAtLeast(0)
    val hours = safeSeconds / 3_600
    val minutes = (safeSeconds % 3_600) / 60
    val remainingSeconds = safeSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, remainingSeconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, remainingSeconds)
    }
}

@StringRes
fun StudyActivityType.labelKey(): Int = when (this) {
    StudyActivityType.THEORY -> R.string.activity_type_theory
    StudyActivityType.QUESTIONS -> R.string.activity_type_questions
    StudyActivityType.REVIEW -> R.string.activity_type_review
    StudyActivityType.FLASHCARDS -> R.string.activity_type_flashcards
    StudyActivityType.MOCK_EXAM -> R.string.activity_type_mock_exam
    StudyActivityType.WRITING -> R.string.activity_type_writing
    StudyActivityType.VIDEO -> R.string.activity_type_video
    StudyActivityType.READING -> R.string.activity_type_reading
    StudyActivityType.CUSTOM -> R.string.activity_type_custom
}

@StringRes
fun StudySessionStatus.labelKey(): Int = when (this) {
    StudySessionStatus.RUNNING -> R.string.session_status_running
    StudySessionStatus.PAUSED -> R.string.session_status_paused
    StudySessionStatus.COMPLETED -> R.string.session_status_completed
    StudySessionStatus.CANCELLED -> R.string.session_status_cancelled
}
