package com.mikeyphw.studyflow.core.navigation

import kotlinx.serialization.Serializable

@Serializable
data object PlanListRoute

@Serializable
data object CreatePlanRoute

@Serializable
data class DashboardRoute(val planId: String)

@Serializable
data object SettingsRoute
