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
        reminderLaunch.value = intent.toStudyReminderLaunch()
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
        setIntent(intent)
        reminderLaunch.value = intent.toStudyReminderLaunch()
    }
}

private fun Intent.toStudyReminderLaunch(): StudyReminderLaunch? {
    val planId = getStringExtra(StudyReminderExtraPlanId)?.takeIf(String::isNotBlank) ?: return null
    val type = getStringExtra(StudyReminderExtraType)
        ?.let { raw -> runCatching { ReminderNotificationType.valueOf(raw) }.getOrNull() }
        ?: return null
    return StudyReminderLaunch(planId = planId, type = type)
}
