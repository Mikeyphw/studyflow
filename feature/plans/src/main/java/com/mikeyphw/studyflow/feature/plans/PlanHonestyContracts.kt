package com.mikeyphw.studyflow.feature.plans

import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

internal fun canOpenPlanFromList(plan: StudyPlan): Boolean = plan.status !in setOf(
    StudyPlanStatus.ARCHIVED,
    StudyPlanStatus.TRASHED,
)

internal fun truthfulNextActivityAvailable(hasConcreteActivity: Boolean): Boolean = hasConcreteActivity

internal fun daysUntilExamLocalCalendar(
    examDateIso: String,
    nowEpochMillis: Long = System.currentTimeMillis(),
    locale: Locale = Locale.ROOT,
): Long? {
    val formatter = SimpleDateFormat("yyyy-MM-dd", locale).apply { isLenient = false }
    val position = ParsePosition(0)
    val parsed = formatter.parse(examDateIso, position) ?: return null
    if (position.index != examDateIso.length) return null
    val today = Calendar.getInstance().apply {
        timeInMillis = nowEpochMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val exam = Calendar.getInstance().apply {
        timeInMillis = parsed.time
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val delta = exam.timeInMillis - today.timeInMillis
    return Math.floorDiv(delta, 86_400_000L)
}
