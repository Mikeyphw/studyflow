package com.mikeyphw.studyflow.termux

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.mikeyphw.studyflow.core.model.TermuxBridgeAction
import com.mikeyphw.studyflow.core.model.TermuxCommandRequest
import com.mikeyphw.studyflow.core.model.splitTermuxCommandTemplate
import com.mikeyphw.studyflow.core.model.termuxExecutablePath

/**
 * Android RUN_COMMAND adapter for StudyFlow programming exercises.
 *
 * The model layer owns the allow-list. This adapter only translates an already validated
 * Termux RUN_COMMAND request into the public Termux intent API and attaches a one-shot
 * PendingIntent so stdout, stderr and exit code can be returned to StudyFlow.
 */
object StudyFlowTermuxRunCommandBridge {
    const val TERMUX_PACKAGE = "com.termux"
    const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
    const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
    const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"
    const val EXTRA_COMMAND_LABEL = "com.termux.RUN_COMMAND_COMMAND_LABEL"
    const val EXTRA_COMMAND_DESCRIPTION = "com.termux.RUN_COMMAND_COMMAND_DESCRIPTION"
    const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"
    const val PERMISSION_RUN_COMMAND = "com.termux.permission.RUN_COMMAND"


    fun buildRunCommandIntent(
        context: Context,
        request: TermuxCommandRequest,
        background: Boolean = true,
    ): Intent {
        require(request.action == TermuxBridgeAction.RUN_COMMAND) {
            "Only RUN_COMMAND requests can be launched through the Termux RUN_COMMAND bridge."
        }
        val template = requireNotNull(request.commandTemplate) { "Termux RUN_COMMAND requires a command template." }
        val invocation = splitTermuxCommandTemplate(template.commandLine)
        val resultIntent = Intent(context, StudyFlowTermuxResultReceiver::class.java).apply {
            putExtra(StudyFlowTermuxResultReceiver.EXTRA_REQUEST_ID, request.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            request.id.stablePendingIntentRequestCode(),
            resultIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT or mutablePendingIntentFlag(),
        )

        return Intent(ACTION_RUN_COMMAND).apply {
            setClassName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
            putExtra(EXTRA_COMMAND_PATH, termuxExecutablePath(invocation.executable))
            putExtra(EXTRA_ARGUMENTS, invocation.arguments.toTypedArray())
            putExtra(EXTRA_WORKDIR, request.toTermuxHomeWorkdir(template.workingDirectory))
            putExtra(EXTRA_BACKGROUND, background)
            putExtra(EXTRA_SESSION_ACTION, "0")
            putExtra(EXTRA_COMMAND_LABEL, template.label)
            putExtra(EXTRA_COMMAND_DESCRIPTION, "StudyFlow exercise request ${request.id}")
            putExtra(EXTRA_PENDING_INTENT, pendingIntent)
        }
    }

    fun launchRunCommand(
        context: Context,
        request: TermuxCommandRequest,
        background: Boolean = true,
    ): StudyFlowTermuxLaunchResult = runCatching {
        val intent = buildRunCommandIntent(context, request, background)
        context.startService(intent)
        StudyFlowTermuxLaunchResult.SENT
    }.getOrElse { error ->
        StudyFlowTermuxLaunchResult.FAILED.copy(message = error.message.orEmpty())
    }

    private fun TermuxCommandRequest.toTermuxHomeWorkdir(workingDirectory: String): String = buildString {
        append("~/studyflow/courses/")
        append(workspace.courseRelativePath)
        val dir = workingDirectory.trim().removePrefix("./")
        if (dir.isNotBlank() && dir != ".") {
            append('/')
            append(dir)
        }
    }

    private fun String.stablePendingIntentRequestCode(): Int = fold(17) { acc, char ->
        (acc * 31) + char.code
    }

    private fun mutablePendingIntentFlag(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        PendingIntent.FLAG_MUTABLE
    } else {
        0
    }
}

data class StudyFlowTermuxLaunchResult(
    val sent: Boolean,
    val message: String,
) {
    companion object {
        val SENT = StudyFlowTermuxLaunchResult(sent = true, message = "Sent to Termux")
        val FAILED = StudyFlowTermuxLaunchResult(sent = false, message = "Termux launch failed")
    }
}
