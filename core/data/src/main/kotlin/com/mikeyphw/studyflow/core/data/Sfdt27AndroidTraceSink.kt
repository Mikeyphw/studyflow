package com.mikeyphw.studyflow.core.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.AtomicFile
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.diagnostics.Sfdt27DiagnosticTrace
import java.io.File

/** Private atomic file; never send its raw bytes to CI artifacts. */
internal object Sfdt27AndroidTraceSink {
    private var installed = false

    @Synchronized
    fun install(context: Context, database: StudyFlowDatabase) {
        val app = context.applicationContext
        if ((app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0 || installed) return
        installed = true
        val atomic = AtomicFile(File(app.filesDir, "sfdt27-diag-trace.txt"))
        Sfdt27DiagnosticTrace.installDebugSink { sanitized ->
            val stream = atomic.startWrite()
            try {
                stream.write(sanitized)
                stream.fd.sync()
                atomic.finishWrite(stream)
            } catch (error: Exception) {
                atomic.failWrite(stream)
                throw error
            }
        }
        Sfdt27DiagnosticTrace.installProductionRead { planId ->
            val planExists = database.studyPlanDao().countById(planId) == 1
            val settingsExists = database.studySupportDao().findPlanSettings(planId) != null
            planExists to settingsExists
        }
    }
}
