package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.GoalType
import com.mikeyphw.studyflow.core.model.StudyStrategyType
import com.mikeyphw.studyflow.core.model.SubjectId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PublicExamOnboardingTest {
    private val planId = StudyPlanId("cpov12-plan")

    @Test
    fun `blank target stays truthful and does not invent exam identity`() {
        val blueprint = PublicExamOnboardingBuilder.build(PublicExamOnboardingInput())
        assertNull(PublicExamOnboardingBuilder.buildTarget(planId, blueprint, { "target" }, 10L))
    }

    @Test
    fun `partial target identity is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            PublicExamOnboardingInput(organization = "Agency")
        }
    }

    @Test
    fun `real target strategy review policy and goals reuse existing models`() {
        val input = PublicExamOnboardingInput(
            organization = "Agency",
            role = "Analyst",
            examBoard = "Board",
            noticeUrl = "https://example.invalid/edital",
            examDateIso = "2027-01-17",
            examDateKind = PublicExamExamDateKind.EXPECTED,
            editalImportChoice = PublicExamEditalImportChoice.NOW,
            coverageGoalDateIso = "2026-12-01",
            weeklyAvailabilityHours = 20,
            cyclePreference = PublicExamCyclePreference.STUDY_CYCLE,
            reviewDays = listOf(1, 7, 30),
            weeklyQuestionGoal = 250,
            accuracyGoalPercent = 85,
            monthlyMockGoal = 3,
        )
        val blueprint = StudyOnboardingSamplePlanWizard.build(
            OnboardingSamplePlanInput(OnboardingSamplePlanTemplate.PUBLIC_EXAM_12_WEEK, input),
        )
        val publicExam = assertNotNull(blueprint.publicExam)
        val target = assertNotNull(PublicExamOnboardingBuilder.buildTarget(planId, publicExam, { "target" }, 10L))
        assertEquals("Agency", target.organization)
        assertNull(target.confirmedExamDateIso)
        assertEquals("2027-01-17", target.expectedExamDateIso)
        assertEquals(PublicExamEditalImportChoice.NOW, publicExam.editalImportChoice)
        assertEquals(20, blueprint.weeklyGoalHours)
        assertEquals(
            StudyStrategyType.STUDY_CYCLE,
            PublicExamOnboardingBuilder.buildStrategy(planId, publicExam) { "strategy" }.type,
        )
        val settings = StudyOnboardingSamplePlanWizard.buildSettings(planId, blueprint)
        assertEquals(listOf(1, 7, 30), PublicExamReviewPolicyCodec.decode(settings.configurationJson).intervalsDays)
        val titles = PublicExamGoalPreset.entries.associateWith { it.name }
        val accuracySubject = SubjectId("subject")
        val goals = PublicExamOnboardingBuilder.buildGoals(
            planId, publicExam, titles, "2026-08-28", { itName() }, accuracySubject,
        )
        assertEquals(6, goals.size)
        assertTrue(goals.all { goal -> goal.studyPlanId == planId })
        assertEquals(accuracySubject, goals.single { it.type == GoalType.ACCURACY }.subjectId)
        assertEquals(
            "2026-12-01",
            goals.single { it.type == GoalType.TOPIC_COMPLETION }.endsOnIso,
        )
        assertTrue(goals.any { it.type == GoalType.CUSTOM })
    }

    @Test
    fun `all import templates are local repository assets`() {
        val templates = PublicExamOnboardingTemplates.importTemplates
        assertEquals(PublicExamImportTemplateKind.entries.toSet(), templates.map { it.kind }.toSet())
        assertTrue(templates.all { it.localOnly && it.repositoryPath.startsWith("samples/import/") })
        assertTrue(templates.any { it.kind == PublicExamImportTemplateKind.EDITAL && it.format == "csv" })
        assertTrue(templates.any { it.kind == PublicExamImportTemplateKind.EDITAL && it.format == "tsv" })
        assertTrue(templates.any { it.kind == PublicExamImportTemplateKind.QUESTIONS && it.format == "tsv" })
        assertTrue(templates.any { it.kind == PublicExamImportTemplateKind.LEGISLATION && it.format == "csv" })
    }

    private var idCounter = 0
    private fun itName(): String = "goal-${idCounter++}"
}
