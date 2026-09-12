package com.mikeyphw.studyflow.reminders

import android.content.Context
import com.mikeyphw.studyflow.core.link.StudyFlowCompactAttentionProjection

internal data class AndroidCompactCompanionState(
    val occurrenceId: String?,
    val title: String?,
    val body: String?,
    val actionLabel: String?,
)

internal class AndroidCompactCompanionStateStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("studyflow-att08-compact", Context.MODE_PRIVATE)

    fun update(projection: StudyFlowCompactAttentionProjection) {
        val card = projection.cards.firstOrNull { it.companionId.value == "android-quick-tile" }
            ?: projection.cards.firstOrNull()
        prefs.edit()
            .putString("occurrence", card?.occurrenceId?.value)
            .putString("title", card?.title)
            .putString("body", card?.body)
            .putString("action", card?.actions?.firstOrNull()?.label)
            .apply()
    }

    fun snapshot(): AndroidCompactCompanionState = AndroidCompactCompanionState(
        occurrenceId = prefs.getString("occurrence", null),
        title = prefs.getString("title", null),
        body = prefs.getString("body", null),
        actionLabel = prefs.getString("action", null),
    )
}
