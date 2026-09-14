package com.mikeyphw.studyflow.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mikeyphw.studyflow.requireStudyFlowAppContainer
import com.mikeyphw.studyflow.core.domain.ReminderNotificationCandidate
import com.mikeyphw.studyflow.core.domain.ReminderNotificationContent
import com.mikeyphw.studyflow.core.domain.ReminderNotificationContentKind
import com.mikeyphw.studyflow.core.domain.ReminderNotificationType
import com.mikeyphw.studyflow.core.model.StudyPlanId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val ReminderReceiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

class StudyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != StudyReminderAction) return
        val parsed = intent.parseReminderIdentity() ?: return
        val journal = StudyFlowReceiverMutationJournal(context)
        val handle = journal.begin("reminder-deliver", "${parsed.planId.value}:${parsed.type}:${parsed.occurrenceId}")
        val result = goAsync()
        ReminderReceiverScope.launch {
            try {
                context.requireStudyFlowAppContainer().reminderDeliveryCoordinator.deliver(
                    parsed.planId,
                    parsed.type,
                    parsed.occurrenceId,
                )
                journal.complete(handle)
            } catch (error: Exception) {
                journal.fail(handle, "deliver-${error.javaClass.simpleName.lowercase()}")
            } finally {
                result.finish()
            }
        }
    }
}

class StudyReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != StudyReminderUserAction) return
        val parsed = intent.parseReminderIdentity() ?: return
        val action = intent.getStringExtra(StudyReminderExtraUserAction)
            ?.let { raw -> StudyReminderNotificationAction.entries.firstOrNull { it.name == raw } }
            ?: return
        val targetId = intent.getStringExtra(StudyReminderExtraTargetId)?.takeIf(String::isNotBlank)
        val journal = StudyFlowReceiverMutationJournal(context)
        val handle = journal.begin("reminder-action", "${parsed.planId.value}:${parsed.type}:${parsed.occurrenceId}:${action.name}")
        val result = goAsync()
        ReminderReceiverScope.launch {
            try {
                val actionResult = context.requireStudyFlowAppContainer().reminderDeliveryCoordinator
                    .handleNotificationAction(
                        planId = parsed.planId,
                        type = parsed.type,
                        occurrenceId = parsed.occurrenceId,
                        action = action,
                        targetId = targetId,
                        sourceCandidate = intent.parseReminderCandidate(parsed),
                    )
                journal.complete(handle)
                if (actionResult is StudyReminderActionResult.Open) {
                    context.startActivity(
                        studyReminderOpenIntent(
                            context = context,
                            planId = parsed.planId,
                            type = actionResult.type,
                            identity = studyReminderIdentityUri(
                                context,
                                parsed.planId,
                                actionResult.type,
                                "action-open:${parsed.occurrenceId}",
                                openApp = true,
                            ),
                            targetId = actionResult.targetId,
                            sessionId = actionResult.sessionId,
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            } catch (error: Exception) {
                journal.fail(handle, "action-${error.javaClass.simpleName.lowercase()}")
            } finally {
                result.finish()
            }
        }
    }
}

class StudyReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ReconcileReminderScheduleActions) return
        val result = goAsync()
        ReminderReceiverScope.launch {
            try {
                runCatching {
                    context.requireStudyFlowAppContainer().reminderDeliveryCoordinator.reconcileAll(
                        reason = "system:${intent.action}",
                    )
                }
                StudyFlowReceiverMutationRecovery.noteReconcile(context, "system:${intent.action}")
                StudyFlowWidgetUpdateCoordinator.refreshAll(context.applicationContext)
            } finally {
                result.finish()
            }
        }
    }
}

private data class ReminderIdentity(
    val planId: StudyPlanId,
    val type: ReminderNotificationType,
    val occurrenceId: String,
)

private fun Intent.parseReminderIdentity(): ReminderIdentity? {
    val planId = getStringExtra(StudyReminderExtraPlanId)?.takeIf(String::isNotBlank) ?: return null
    val typeName = getStringExtra(StudyReminderExtraType) ?: return null
    val type = ReminderNotificationType.entries.firstOrNull { it.name == typeName } ?: return null
    val occurrenceId = getStringExtra(StudyReminderExtraOccurrenceId)?.takeIf(String::isNotBlank) ?: return null
    return ReminderIdentity(StudyPlanId(planId), type, occurrenceId)
}


private fun Intent.parseReminderCandidate(identity: ReminderIdentity): ReminderNotificationCandidate? {
    val kind = getStringExtra(StudyReminderExtraContentKind)
        ?.let { raw -> ReminderNotificationContentKind.entries.firstOrNull { it.name == raw } }
        ?: return null
    if (!hasExtra(StudyReminderExtraScheduledAt) || !hasExtra(StudyReminderExtraFireAt)) return null
    return ReminderNotificationCandidate(
        type = identity.type,
        occurrenceId = identity.occurrenceId,
        content = ReminderNotificationContent(
            kind = kind,
            count = getIntExtra(StudyReminderExtraContentCount, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE },
            userLabel = getStringExtra(StudyReminderExtraUserLabel)?.takeIf(String::isNotBlank),
            targetId = getStringExtra(StudyReminderExtraTargetId)?.takeIf(String::isNotBlank),
        ),
        scheduledAtEpochMillis = getLongExtra(StudyReminderExtraScheduledAt, -1L).takeIf { it >= 0L } ?: return null,
        fireAtEpochMillis = getLongExtra(StudyReminderExtraFireAt, -1L).takeIf { it >= 0L } ?: return null,
        delayedByQuietHours = getBooleanExtra(StudyReminderExtraDelayedByQuietHours, false),
    )
}

private val ReconcileReminderScheduleActions = setOf(
    Intent.ACTION_BOOT_COMPLETED,
    Intent.ACTION_TIME_CHANGED,
    Intent.ACTION_TIMEZONE_CHANGED,
    Intent.ACTION_DATE_CHANGED,
    Intent.ACTION_MY_PACKAGE_REPLACED,
)
