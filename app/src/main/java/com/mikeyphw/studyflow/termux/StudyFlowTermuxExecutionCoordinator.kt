package com.mikeyphw.studyflow.termux

import android.content.Context
import com.mikeyphw.studyflow.core.domain.ManageProgrammingCourse
import com.mikeyphw.studyflow.core.model.CodingExerciseId
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.TermuxExerciseRunMode
import com.mikeyphw.studyflow.core.model.sanitizeTermuxWorkspaceSlug

/**
 * Four-stage StudyFlow→Termux coordinator.
 *
 * The domain repository first durably persists CREATE_WORKSPACE, WRITE_EXERCISE_FILES,
 * RUN_COMMAND and READ_RESULT request rows. Only after the correlated RUN_COMMAND row exists does
 * the Android bridge send the public Termux RUN_COMMAND intent. Returned PendingIntent broadcasts
 * are admitted by exact request id and then persisted back through the repository.
 */
object StudyFlowTermuxExecutionCoordinator {
    suspend fun enqueueAndLaunchExercise(
        context: Context,
        programmingCourse: ManageProgrammingCourse,
        planId: StudyPlanId,
        exerciseId: CodingExerciseId,
        runMode: TermuxExerciseRunMode = TermuxExerciseRunMode.TEST,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): StudyFlowTermuxLaunchResult {
        if (!StudyFlowTermuxRunCommandBridge.isTermuxRunCommandAvailable(context)) {
            return StudyFlowTermuxLaunchResult.FAILED_UNAVAILABLE
        }
        val requestPrefix = sanitizeTermuxWorkspaceSlug(
            listOf("sf", planId.value, exerciseId.value, nowEpochMillis.toString()).joinToString("-"),
        )
        val runRequest = programmingCourse.enqueueTermuxExerciseRun(
            planId = planId,
            exerciseId = exerciseId,
            runMode = runMode,
            requestIdPrefix = requestPrefix,
        )
        val launch = StudyFlowTermuxRunCommandBridge.launchRunCommand(context, runRequest)
        if (launch.sent) {
            programmingCourse.markTermuxRequestWaitingForTermux(runRequest.id)
        }
        return launch
    }
}
