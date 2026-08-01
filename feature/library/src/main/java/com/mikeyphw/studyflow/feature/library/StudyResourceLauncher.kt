package com.mikeyphw.studyflow.feature.library

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.mikeyphw.studyflow.core.domain.StudyResourceLaunchPlan
import java.io.File

internal fun Context.launchStudyResource(plan: StudyResourceLaunchPlan): Boolean = runCatching {
    val intent = when (plan) {
        is StudyResourceLaunchPlan.WebPage -> viewIntent(Uri.parse(plan.uri), null)
        is StudyResourceLaunchPlan.ExternalVideo -> viewIntent(Uri.parse(plan.uri), "video/*")
        is StudyResourceLaunchPlan.ExternalDocument -> viewIntent(Uri.parse(plan.uri), plan.mimeType)
        is StudyResourceLaunchPlan.AppPrivateAnnex -> {
            val file = File(filesDir, plan.storageReference).canonicalFile
            val root = filesDir.canonicalFile
            if (!file.path.startsWith(root.path + File.separator) || !file.exists()) return false
            val contentUri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                file,
            )
            viewIntent(contentUri, plan.mimeType).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        StudyResourceLaunchPlan.MissingSource -> return false
    }
    startActivity(intent)
    true
}.getOrElse { throwable ->
    if (throwable is ActivityNotFoundException) false else false
}

private fun viewIntent(uri: Uri, mimeType: String?): Intent = Intent(Intent.ACTION_VIEW).apply {
    if (mimeType.isNullOrBlank()) setData(uri) else setDataAndType(uri, mimeType)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
