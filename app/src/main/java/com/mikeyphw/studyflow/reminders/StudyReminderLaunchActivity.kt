package com.mikeyphw.studyflow.reminders

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.mikeyphw.studyflow.MainActivity
import com.mikeyphw.studyflow.StudyReminderLaunch
import com.mikeyphw.studyflow.core.domain.ReminderNotificationType
import com.mikeyphw.studyflow.core.model.StudyPlanId

/**
 * Non-exported trampoline used by reminder/widget PendingIntents.
 *
 * The exported launcher never accepts routing extras. Android delivers the immutable PendingIntent
 * to this component, which validates the internal payload and hands it to the process-local store
 * before foregrounding [MainActivity].
 */
// This is an internal PendingIntent trampoline, not an app launch/splash screen.
@SuppressLint("CustomSplashScreen")
class StudyReminderLaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launch = intent.toStudyReminderLaunch()
        if (launch != null) {
            StudyReminderLaunchStore.publish(launch)
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
            )
        }
        finish()
    }
}

internal object StudyReminderLaunchStore {
    private val lock = Any()
    private var pending: StudyReminderLaunch? = null

    fun publish(launch: StudyReminderLaunch) = synchronized(lock) {
        pending = launch
    }

    fun consume(): StudyReminderLaunch? = synchronized(lock) {
        val launch = pending
        pending = null
        launch
    }
}

internal fun studyReminderOpenIntent(
    context: Context,
    planId: StudyPlanId,
    type: ReminderNotificationType,
    identity: Uri,
): Intent = Intent(context.applicationContext, StudyReminderLaunchActivity::class.java).apply {
    data = identity
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    putExtra(StudyReminderExtraPlanId, planId.value)
    putExtra(StudyReminderExtraType, type.name)
}

private fun Intent.toStudyReminderLaunch(): StudyReminderLaunch? {
    val planId = getStringExtra(StudyReminderExtraPlanId)?.takeIf(String::isNotBlank) ?: return null
    val type = getStringExtra(StudyReminderExtraType)
        ?.let { raw -> enumValues<ReminderNotificationType>().firstOrNull { it.name == raw } }
        ?: return null
    return StudyReminderLaunch(planId = planId, type = type)
}
