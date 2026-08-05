package com.mikeyphw.studyflow.core.navigation

import java.lang.reflect.Modifier
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowWorkspaceRoutesTest {
    @Test
    fun everyLegacyWorkspaceRouteTranslatesToDeterministicTarget() {
        val expected = listOf(
            DashboardRoute("p") to TodayRoute("p"),
            AnalyticsRoute("p") to ProgressWorkspaceRoute("p"),
            SubjectsRoute("p") to PlanWorkspaceRoute("p", PlanWorkspaceTab.CONTENT),
            ScheduleRoute("p") to PlanWorkspaceRoute("p", PlanWorkspaceTab.SCHEDULE),
            LibraryRoute("p") to PlanWorkspaceRoute("p", PlanWorkspaceTab.LIBRARY),
            PracticeRoute("p") to PracticeWorkspaceRoute("p", PracticeWorkspaceTab.QUESTIONS),
            ProgressRoute("p") to ProgressWorkspaceRoute("p"),
            ReviewsRoute("p") to PracticeWorkspaceRoute("p", PracticeWorkspaceTab.REVIEWS),
        )

        expected.forEach { (legacy, workspace) ->
            assertEquals(workspace, legacyWorkspaceRoute(legacy))
        }
    }

    @Test
    fun unrelatedRoutesRemainUnchangedByCompatibilityTranslator() {
        val route = SettingsRoute
        assertTrue(legacyWorkspaceRoute(route) === route)
    }



    @Test
    fun typedWorkspaceCoordinatesRoundTripThroughSavedNavigationPayloads() {
        val planRoute = PlanWorkspaceRoute("plan", PlanWorkspaceTab.LIBRARY)
        val pickerRoute = PlanPickerRoute(
            root = StudyFlowWorkspaceRoot.PROGRESS,
            planId = "plan",
            planTab = PlanWorkspaceTab.CONTENT,
            practiceTab = PracticeWorkspaceTab.ERRORS,
            progressPeriod = ProgressWorkspacePeriod.NINETY_DAYS,
        )

        assertEquals(planRoute, Json.decodeFromString<PlanWorkspaceRoute>(Json.encodeToString(planRoute)))
        assertEquals(pickerRoute, Json.decodeFromString<PlanPickerRoute>(Json.encodeToString(pickerRoute)))
    }

    @Test
    fun planPickerRouteCarriesOnlyCompatibleWorkspaceCoordinates() {
        val route = PlanPickerRoute(
            root = StudyFlowWorkspaceRoot.PRACTICE,
            planId = "plan",
            planTab = PlanWorkspaceTab.LIBRARY,
            practiceTab = PracticeWorkspaceTab.ERRORS,
            progressPeriod = ProgressWorkspacePeriod.NINETY_DAYS,
        )

        assertEquals(StudyFlowWorkspaceRoot.PRACTICE, route.root)
        assertEquals("plan", route.planId)
        assertEquals(PlanWorkspaceTab.LIBRARY, route.planTab)
        assertEquals(PracticeWorkspaceTab.ERRORS, route.practiceTab)
        assertEquals(ProgressWorkspacePeriod.NINETY_DAYS, route.progressPeriod)
    }

    @Test
    fun workspaceRouteFieldsRemainIdentifiersAndSmallEnumsOnly() {
        val routeClasses = listOf(
            TodayRoute::class.java,
            PlanWorkspaceRoute::class.java,
            PracticeWorkspaceRoute::class.java,
            ProgressWorkspaceRoute::class.java,
            FocusSessionRoute::class.java,
            PlanPickerRoute::class.java,
            QuickAddRoute::class.java,
            SystemHubRoute::class.java,
            ScheduleManagerRoute::class.java,
            ContentManagerRoute::class.java,
            LibraryManagerRoute::class.java,
            QuestionBankRoute::class.java,
            ReviewCenterRoute::class.java,
            ProgressCenterRoute::class.java,
            AnalyticsDetailRoute::class.java,
        )
        val allowedTypes = setOf(
            String::class.java,
            StudyFlowWorkspaceRoot::class.java,
            PlanWorkspaceTab::class.java,
            PracticeWorkspaceTab::class.java,
            ProgressWorkspacePeriod::class.java,
        )

        routeClasses.forEach { routeClass ->
            val fields = routeClass.declaredFields.filterNot { field ->
                Modifier.isStatic(field.modifiers) || field.isSynthetic
            }
            assertTrue("${routeClass.simpleName} must stay lightweight", fields.isNotEmpty())
            fields.forEach { field ->
                assertTrue(
                    "${routeClass.simpleName}.${field.name} has unsupported ${field.type.simpleName}",
                    field.type in allowedTypes,
                )
            }
        }
    }
}
