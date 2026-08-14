package com.mikeyphw.studyflow

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mikeyphw.studyflow.reminders.StudyReminderBootReceiver
import com.mikeyphw.studyflow.reminders.StudyReminderLaunchActivity
import com.mikeyphw.studyflow.reminders.StudyReminderReceiver
import com.mikeyphw.studyflow.widget.StudyFlowTodayWidgetProvider
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManifestSecurityPolicyTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val packageManager = context.packageManager

    @Test
    fun mergedManifestExposesOnlyLauncherActivity() {
        assertTrue(activityInfo(MainActivity::class.java).exported)
        assertFalse(activityInfo(StudyReminderLaunchActivity::class.java).exported)
        assertFalse(receiverInfo(StudyReminderReceiver::class.java).exported)
        assertFalse(receiverInfo(StudyReminderBootReceiver::class.java).exported)
        assertFalse(receiverInfo(StudyFlowTodayWidgetProvider::class.java).exported)
    }

    @Test
    fun mergedManifestDisablesPlatformBackupAndKeepsProviderPrivate() {
        @Suppress("DEPRECATION")
        val applicationInfo = packageManager.getApplicationInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        assertFalse(applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)

        val provider = packageManager.getProviderInfo(
            ComponentName(context, androidx.core.content.FileProvider::class.java),
            PackageManager.GET_META_DATA,
        )
        assertFalse(provider.exported)
        assertTrue(provider.grantUriPermissions)
        assertNotNull(provider.metaData)
    }

    @Test
    fun fileProviderGrantsOnlyConfiguredShareRoots() {
        val allowed = File(context.cacheDir, "support-bundles/manifest-policy.txt").apply {
            parentFile?.mkdirs()
            writeText("policy")
        }
        val privateFile = File(context.cacheDir, "manifest-policy-private.txt").apply { writeText("private") }
        try {
            assertNotNull(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", allowed))
            val rejected = try {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", privateFile)
                false
            } catch (_: IllegalArgumentException) {
                true
            }
            assertTrue(rejected)
        } finally {
            allowed.delete()
            privateFile.delete()
        }
    }

    private fun activityInfo(type: Class<*>) = packageManager.getActivityInfo(ComponentName(context, type), 0)
    private fun receiverInfo(type: Class<*>) = packageManager.getReceiverInfo(ComponentName(context, type), 0)
}
