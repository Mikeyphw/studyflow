package com.mikeyphw.studyflow

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import com.mikeyphw.studyflow.core.domain.ReminderNotificationType
import com.mikeyphw.studyflow.reminders.StudyReminderLaunchStore

data class StudyReminderLaunch(
    val planId: String,
    val type: ReminderNotificationType,
)

class MainActivity : AppCompatActivity() {
    private val reminderLaunch = mutableStateOf<StudyReminderLaunch?>(null)
    private val tvControllerLaunchRequested = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        reminderLaunch.value = StudyReminderLaunchStore.consume()
        tvControllerLaunchRequested.value = intent?.action == ACTION_OPEN_TV_CONTROLLER
        val container = (application as StudyFlowApplication).container
        setContent {
            StudyFlowApp(
                container = container,
                reminderLaunch = reminderLaunch.value,
                onReminderLaunchConsumed = { reminderLaunch.value = null },
                tvControllerLaunchRequested = tvControllerLaunchRequested.value,
                onTvControllerLaunchConsumed = { tvControllerLaunchRequested.value = false },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        reminderLaunch.value = StudyReminderLaunchStore.consume()
        if (intent.action == ACTION_OPEN_TV_CONTROLLER) tvControllerLaunchRequested.value = true
        setIntent(intent)
    }

    companion object {
        const val ACTION_OPEN_TV_CONTROLLER = "com.mikeyphw.studyflow.action.TV_CONTROLLER"
    }
}
