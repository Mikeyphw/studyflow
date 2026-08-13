package com.mikeyphw.studyflow.feature.library

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.mikeyphw.studyflow.core.domain.StudyResourceLaunchPlan
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

internal sealed interface StudyResourceLaunchResult {
    data object Opened : StudyResourceLaunchResult
    data object MissingSource : StudyResourceLaunchResult
    data object InvalidSource : StudyResourceLaunchResult
    data object MissingPrivateFile : StudyResourceLaunchResult
    data object UnsafePrivatePath : StudyResourceLaunchResult
    data object IntegrityFailure : StudyResourceLaunchResult
    data object NoHandler : StudyResourceLaunchResult
    data object LaunchFailed : StudyResourceLaunchResult
}

internal fun Context.launchStudyResource(plan: StudyResourceLaunchPlan): StudyResourceLaunchResult = try {
    val intent = when (plan) {
        is StudyResourceLaunchPlan.WebPage -> viewIntent(plan.uri.toUri(), null)
        is StudyResourceLaunchPlan.ExternalVideo -> viewIntent(plan.uri.toUri(), "video/*")
        is StudyResourceLaunchPlan.ExternalDocument -> viewIntent(plan.uri.toUri(), plan.mimeType)
        is StudyResourceLaunchPlan.LinkedDocument -> {
            val uri = plan.uri.toUri()
            val matches = contentResolver.openInputStream(uri)?.use { input ->
                streamIntegrityMatches(input, plan.byteSize, plan.checksumSha256)
            } ?: false
            if (!matches) return StudyResourceLaunchResult.IntegrityFailure
            viewIntent(uri, plan.mimeType)
        }
        is StudyResourceLaunchPlan.AppPrivateAnnex -> {
            val shareableRoot = File(filesDir, "shareable-annexes").canonicalFile
            val file = File(filesDir, plan.storageReference).canonicalFile
            if (!file.path.startsWith(shareableRoot.path + File.separator)) {
                return StudyResourceLaunchResult.UnsafePrivatePath
            }
            if (!file.isFile) return StudyResourceLaunchResult.MissingPrivateFile
            val matches = file.inputStream().use { input ->
                streamIntegrityMatches(input, plan.byteSize, plan.checksumSha256)
            }
            if (!matches) return StudyResourceLaunchResult.IntegrityFailure
            val contentUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            viewIntent(contentUri, plan.mimeType)
        }
        is StudyResourceLaunchPlan.InvalidSource -> return StudyResourceLaunchResult.InvalidSource
        StudyResourceLaunchPlan.MissingSource -> return StudyResourceLaunchResult.MissingSource
    }
    startActivity(intent)
    StudyResourceLaunchResult.Opened
} catch (_: ActivityNotFoundException) {
    StudyResourceLaunchResult.NoHandler
} catch (_: Exception) {
    StudyResourceLaunchResult.LaunchFailed
}

private fun viewIntent(uri: Uri, mimeType: String?): Intent = Intent(Intent.ACTION_VIEW).apply {
    if (mimeType.isNullOrBlank()) setData(uri) else setDataAndType(uri, mimeType)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (uri.scheme.equals("content", ignoreCase = true)) {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

private fun streamIntegrityMatches(
    input: InputStream,
    expectedSize: Long,
    expectedChecksum: String?,
): Boolean {
    val digest = expectedChecksum?.takeIf(String::isNotBlank)?.let { MessageDigest.getInstance("SHA-256") }
    var bytes = 0L
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        bytes += count
        if (bytes > expectedSize) return false
        digest?.update(buffer, 0, count)
    }
    if (bytes != expectedSize) return false
    if (digest != null) {
        val actual = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        if (!actual.equals(expectedChecksum, ignoreCase = true)) return false
    }
    return true
}
