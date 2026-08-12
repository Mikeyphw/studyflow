package com.mikeyphw.studyflow

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import com.mikeyphw.studyflow.core.domain.ReminderNotificationType
import com.mikeyphw.studyflow.reminders.StudyReminderExtraPlanId
import com.mikeyphw.studyflow.reminders.StudyReminderExtraType

data class StudyReminderLaunch(
    val planId: String,
    val type: ReminderNotificationType,
)

class MainActivity : AppCompatActivity() {
    private val reminderLaunch = mutableStateOf<StudyReminderLaunch?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        reminderLaunch.value = intent.consumeStudyReminderLaunch()
        val container = (application as StudyFlowApplication).container
        setContent {
            StudyFlowApp(
                container = container,
                reminderLaunch = reminderLaunch.value,
                onReminderLaunchConsumed = { reminderLaunch.value = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        reminderLaunch.value = intent.consumeStudyReminderLaunch()
        setIntent(intent)
    }
}

private fun Intent.consumeStudyReminderLaunch(): StudyReminderLaunch? {
    val rawPlanId = getStringExtra(StudyReminderExtraPlanId)
    val rawType = getStringExtra(StudyReminderExtraType)
    removeExtra(StudyReminderExtraPlanId)
    removeExtra(StudyReminderExtraType)

    val planId = rawPlanId?.takeIf(String::isNotBlank) ?: return null
    val type = rawType
        ?.let { raw -> runCatching { ReminderNotificationType.valueOf(raw) }.getOrNull() }
        ?: return null
    return StudyReminderLaunch(planId = planId, type = type)
}
