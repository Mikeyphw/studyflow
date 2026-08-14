package com.mikeyphw.studyflow

import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyFlowFileProviderScopeTest {
    @Test
    fun providerSharesDedicatedAnnexRootButRejectsPrivatePlanStorage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val annex = File(context.filesDir, "shareable-annexes/test/allowed.txt").apply {
            parentFile?.mkdirs()
            writeText("allowed")
        }
        val privatePlanFile = File(context.filesDir, "study-plans/plan/snapshots/private.studyplan").apply {
            parentFile?.mkdirs()
            writeText("private")
        }
        val authority = "${context.packageName}.fileprovider"

        val allowed = FileProvider.getUriForFile(context, authority, annex)
        assertEquals("content", allowed.scheme)
        assertThrows(IllegalArgumentException::class.java) {
            FileProvider.getUriForFile(context, authority, privatePlanFile)
        }
    }
}
