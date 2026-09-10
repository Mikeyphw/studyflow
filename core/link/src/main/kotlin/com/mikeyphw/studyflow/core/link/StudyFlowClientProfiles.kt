package com.mikeyphw.studyflow.core.link

/**
 * SFLP10/SFLP11 shared client capability profiles.
 *
 * These profiles make every StudyFlow surface a negotiated peer. They intentionally avoid
 * declaring Android as a hub: Web, Android TV, Desktop, and Android all advertise only the
 * capabilities they can really fulfill, and the topology layer decides the route.
 */
object StudyFlowClientProfiles {
    val AndroidPhone: List<StudyFlowLinkCapability> = listOf(
        StudyFlowLinkCapabilities.WorkspaceRead,
        StudyFlowLinkCapabilities.WorkspaceWrite,
        StudyFlowLinkCapabilities.WorkspaceSync,
        StudyFlowLinkCapabilities.PlanRead,
        StudyFlowLinkCapabilities.PlanWrite,
        StudyFlowLinkCapabilities.ScheduleRead,
        StudyFlowLinkCapabilities.ScheduleWrite,
        StudyFlowLinkCapabilities.SessionExecute,
        StudyFlowLinkCapabilities.SessionControl,
        StudyFlowLinkCapabilities.SessionPresent,
        StudyFlowLinkCapabilities.QuestionAnswer,
        StudyFlowLinkCapabilities.QuestionAuthor,
        StudyFlowLinkCapabilities.ReviewExecute,
        StudyFlowLinkCapabilities.ReviewEdit,
        StudyFlowLinkCapabilities.GoalRead,
        StudyFlowLinkCapabilities.GoalComplete,
        StudyFlowLinkCapabilities.GoalEdit,
        StudyFlowLinkCapabilities.LibraryRead,
        StudyFlowLinkCapabilities.LibraryImport,
        StudyFlowLinkCapabilities.LibraryPresent,
        StudyFlowLinkCapabilities.DocumentRead,
        StudyFlowLinkCapabilities.DocumentPresent,
        StudyFlowLinkCapabilities.WritingRead,
        StudyFlowLinkCapabilities.WritingEdit,
        StudyFlowLinkCapabilities.AnalyticsRead,
        StudyFlowLinkCapabilities.BackupExport,
        StudyFlowLinkCapabilities.BackupImport,
        StudyFlowLinkCapabilities.DeviceControl,
        StudyFlowLinkCapabilities.DevicePresentationSource,
        StudyFlowLinkCapabilities.TextInputProvide,
        StudyFlowLinkCapabilities.AudioOutput,
        StudyFlowLinkCapabilities.VideoOutput,
        StudyFlowLinkCapabilities.TouchInput,
        StudyFlowLinkCapabilities.ClipboardRead,
        StudyFlowLinkCapabilities.ClipboardWrite,
        StudyFlowLinkCapabilities.ShareReceive,
        StudyFlowLinkCapabilities.ImportPreview,
        StudyFlowLinkCapabilities.ConflictReview,
    )

    val Desktop: List<StudyFlowLinkCapability> = AndroidPhone + listOf(
        StudyFlowLinkCapabilities.KeyboardInput,
    )

    val Web: List<StudyFlowLinkCapability> = listOf(
        StudyFlowLinkCapabilities.WorkspaceRead,
        StudyFlowLinkCapabilities.WorkspaceWrite,
        StudyFlowLinkCapabilities.WorkspaceSync,
        StudyFlowLinkCapabilities.PlanRead,
        StudyFlowLinkCapabilities.PlanWrite,
        StudyFlowLinkCapabilities.ScheduleRead,
        StudyFlowLinkCapabilities.ScheduleWrite,
        StudyFlowLinkCapabilities.SessionExecute,
        StudyFlowLinkCapabilities.SessionControl,
        StudyFlowLinkCapabilities.SessionPresent,
        StudyFlowLinkCapabilities.QuestionAnswer,
        StudyFlowLinkCapabilities.ReviewExecute,
        StudyFlowLinkCapabilities.GoalRead,
        StudyFlowLinkCapabilities.GoalComplete,
        StudyFlowLinkCapabilities.LibraryRead,
        StudyFlowLinkCapabilities.LibraryPresent,
        StudyFlowLinkCapabilities.DocumentRead,
        StudyFlowLinkCapabilities.DocumentPresent,
        StudyFlowLinkCapabilities.WritingRead,
        StudyFlowLinkCapabilities.WritingEdit,
        StudyFlowLinkCapabilities.AnalyticsRead,
        StudyFlowLinkCapabilities.BackupExport,
        StudyFlowLinkCapabilities.BackupImport,
        StudyFlowLinkCapabilities.DeviceControl,
        StudyFlowLinkCapabilities.DevicePresentationSource,
        StudyFlowLinkCapabilities.TextInputProvide,
        StudyFlowLinkCapabilities.KeyboardInput,
        StudyFlowLinkCapabilities.TouchInput,
        StudyFlowLinkCapabilities.ClipboardRead,
        StudyFlowLinkCapabilities.ClipboardWrite,
        StudyFlowLinkCapabilities.ShareReceive,
        StudyFlowLinkCapabilities.ImportPreview,
        StudyFlowLinkCapabilities.ConflictReview,
    )

    val WebTv: List<StudyFlowLinkCapability> = listOf(
        StudyFlowLinkCapabilities.WorkspaceRead,
        StudyFlowLinkCapabilities.WorkspaceSync,
        StudyFlowLinkCapabilities.ScheduleRead,
        StudyFlowLinkCapabilities.SessionControl,
        StudyFlowLinkCapabilities.SessionPresent,
        StudyFlowLinkCapabilities.QuestionAnswer,
        StudyFlowLinkCapabilities.ReviewExecute,
        StudyFlowLinkCapabilities.GoalRead,
        StudyFlowLinkCapabilities.LibraryRead,
        StudyFlowLinkCapabilities.LibraryPresent,
        StudyFlowLinkCapabilities.DocumentRead,
        StudyFlowLinkCapabilities.DocumentPresent,
        StudyFlowLinkCapabilities.AnalyticsRead,
        StudyFlowLinkCapabilities.DeviceControl,
        StudyFlowLinkCapabilities.DevicePresentationSink,
        StudyFlowLinkCapabilities.DpadInput,
        StudyFlowLinkCapabilities.AudioOutput,
        StudyFlowLinkCapabilities.VideoOutput,
        StudyFlowLinkCapabilities.ClipboardRead,
        StudyFlowLinkCapabilities.ShareReceive,
        StudyFlowLinkCapabilities.ImportPreview,
        StudyFlowLinkCapabilities.ConflictReview,
    )

    val AndroidTv: List<StudyFlowLinkCapability> = WebTv + listOf(
        StudyFlowLinkCapabilities.BackupExport,
    )

    fun capabilitiesFor(kind: StudyFlowLinkClientKind): List<StudyFlowLinkCapability> = when (kind) {
        StudyFlowLinkClientKind.ANDROID -> AndroidPhone
        StudyFlowLinkClientKind.DESKTOP -> Desktop
        StudyFlowLinkClientKind.WEB -> Web
        StudyFlowLinkClientKind.WEB_TV -> WebTv
        StudyFlowLinkClientKind.ANDROID_TV -> AndroidTv
    }.distinct()
}
