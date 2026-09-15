package com.mikeyphw.studyflow.termux

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.mikeyphw.studyflow.core.model.TermuxBridgeAction
import com.mikeyphw.studyflow.core.model.TermuxCommandRequest
import com.mikeyphw.studyflow.core.model.splitTermuxCommandTemplate
import com.mikeyphw.studyflow.core.model.termuxExecutablePath
import com.mikeyphw.studyflow.core.model.termuxWorkspaceWorkdir
import java.security.MessageDigest

/**
 * Android RUN_COMMAND adapter for StudyFlow programming exercises.
 *
 * OV27 binds every launch to one durable request row. The generated result PendingIntent uses a
 * SHA-256 request code plus a unique action/data URI, so Android cannot collapse unrelated exercise
 * results even if a 32-bit request-code prefix collides.
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

    fun isTermuxRunCommandAvailable(context: Context): Boolean {
        val intent = Intent(ACTION_RUN_COMMAND).setClassName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
        return context.packageManager.resolveService(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
    }

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
            action = "com.mikeyphw.studyflow.termux.RESULT.${request.id}"
            data = Uri.parse("studyflow-termux://result/${Uri.encode(request.id)}")
            putExtra(StudyFlowTermuxResultReceiver.EXTRA_REQUEST_ID, request.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            request.id.sha256PendingIntentRequestCode(),
            resultIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT or mutablePendingIntentFlag(),
        )

        return Intent(ACTION_RUN_COMMAND).apply {
            setClassName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
            putExtra(EXTRA_COMMAND_PATH, termuxExecutablePath(invocation.executable))
            putExtra(EXTRA_ARGUMENTS, invocation.arguments.toTypedArray())
            putExtra(EXTRA_WORKDIR, termuxWorkspaceWorkdir(request.workspace, template.workingDirectory))
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
        StudyFlowTermuxLaunchResult(sent = true, requestId = request.id, message = "Sent to Termux")
    }.getOrElse { error ->
        StudyFlowTermuxLaunchResult(sent = false, requestId = request.id, message = error.message.orEmpty())
    }

    private fun String.sha256PendingIntentRequestCode(): Int {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
        return ((digest[0].toInt() and 0xff) shl 24) or
            ((digest[1].toInt() and 0xff) shl 16) or
            ((digest[2].toInt() and 0xff) shl 8) or
            (digest[3].toInt() and 0xff)
    }

    private fun mutablePendingIntentFlag(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        PendingIntent.FLAG_MUTABLE
    } else {
        0
    }
}

data class StudyFlowTermuxLaunchResult(
    val sent: Boolean,
    val requestId: String,
    val message: String,
) {
    companion object {
        val FAILED_UNAVAILABLE = StudyFlowTermuxLaunchResult(
            sent = false,
            requestId = "",
            message = "Termux RUN_COMMAND is unavailable",
        )
    }
}
