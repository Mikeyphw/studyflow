package com.mikeyphw.studyflow.core.navigation

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
data object RuntimeSmokeRoute

@Serializable
data object SearchRoute

@Serializable
data object SettingsRoute
