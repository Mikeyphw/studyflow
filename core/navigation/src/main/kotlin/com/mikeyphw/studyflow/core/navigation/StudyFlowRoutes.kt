package com.mikeyphw.studyflow.core.navigation

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Serializable
data object PlanListRoute

@Serializable
data object CreatePlanRoute

@Serializable
data class EditPlanRoute(val planId: String)

@Serializable
data class PlanSettingsRoute(val planId: String)

@Serializable
data class PlanPortabilityRoute(val planId: String = "")

@Serializable
data class DashboardRoute(val planId: String)

@Serializable
data class AnalyticsRoute(val planId: String)

@Serializable
data class SubjectsRoute(val planId: String)

@Serializable
data class ScheduleRoute(val planId: String)

@Serializable
data class SessionsRoute(val planId: String)

@Serializable
data class LibraryRoute(val planId: String)

@Serializable
data class PracticeRoute(val planId: String)

@Serializable
data class ProgressRoute(val planId: String)

@Serializable
data class ReviewsRoute(val planId: String)

@Serializable
data class RemindersRoute(val planId: String)

@Serializable
data object PrivacyRoute

@Serializable
data object SyncRoute

@Serializable
data object DebugWorkbenchRoute

@Serializable
data object RuntimeSmokeRoute

@Serializable
data object SearchRoute

@Serializable
data object SettingsRoute

@Keep
@Serializable
enum class StudyFlowWorkspaceRoot {
    TODAY,
    PLAN,
    PRACTICE,
    PROGRESS,
}

@Keep
@Serializable
enum class PlanWorkspaceTab {
    OVERVIEW,
    SCHEDULE,
    CONTENT,
    LIBRARY,
}

@Keep
@Serializable
enum class PracticeWorkspaceTab {
    REVIEWS,
    QUESTIONS,
    ERRORS,
}

@Keep
@Serializable
enum class ProgressWorkspacePeriod(val days: Int) {
    SEVEN_DAYS(7),
    THIRTY_DAYS(30),
    NINETY_DAYS(90),
}

@Serializable
data class TodayRoute(val planId: String = "")

@Serializable
data class PlanWorkspaceRoute(
    val planId: String = "",
    val tab: PlanWorkspaceTab = PlanWorkspaceTab.OVERVIEW,
)

@Serializable
data class PracticeWorkspaceRoute(
    val planId: String = "",
    val tab: PracticeWorkspaceTab = PracticeWorkspaceTab.REVIEWS,
)

@Serializable
data class ProgressWorkspaceRoute(
    val planId: String = "",
    val period: ProgressWorkspacePeriod = ProgressWorkspacePeriod.SEVEN_DAYS,
)

@Serializable
data class FocusSessionRoute(
    val planId: String,
    val sessionId: String,
)

@Serializable
data class PlanPickerRoute(
    val root: StudyFlowWorkspaceRoot,
    val planId: String = "",
    val planTab: PlanWorkspaceTab = PlanWorkspaceTab.OVERVIEW,
    val practiceTab: PracticeWorkspaceTab = PracticeWorkspaceTab.REVIEWS,
    val progressPeriod: ProgressWorkspacePeriod = ProgressWorkspacePeriod.SEVEN_DAYS,
)

@Serializable
data class QuickAddRoute(
    val root: StudyFlowWorkspaceRoot,
    val planId: String,
)

@Serializable
data class SystemHubRoute(val planId: String = "")

@Serializable
data class ScheduleManagerRoute(val planId: String)

@Serializable
data class ContentManagerRoute(val planId: String)

@Serializable
data class LibraryManagerRoute(val planId: String)

@Serializable
data class QuestionBankRoute(val planId: String)

@Serializable
data class ReviewCenterRoute(val planId: String)

@Serializable
data class ProgressCenterRoute(val planId: String)

@Serializable
data class AnalyticsDetailRoute(val planId: String)

fun legacyWorkspaceRoute(route: Any): Any = when (route) {
    is DashboardRoute -> TodayRoute(route.planId)
    is AnalyticsRoute -> ProgressWorkspaceRoute(route.planId)
    is SubjectsRoute -> PlanWorkspaceRoute(route.planId, PlanWorkspaceTab.CONTENT)
    is ScheduleRoute -> PlanWorkspaceRoute(route.planId, PlanWorkspaceTab.SCHEDULE)
    is LibraryRoute -> PlanWorkspaceRoute(route.planId, PlanWorkspaceTab.LIBRARY)
    is PracticeRoute -> PracticeWorkspaceRoute(route.planId, PracticeWorkspaceTab.QUESTIONS)
    is ProgressRoute -> ProgressWorkspaceRoute(route.planId)
    is ReviewsRoute -> PracticeWorkspaceRoute(route.planId, PracticeWorkspaceTab.REVIEWS)
    else -> route
}

