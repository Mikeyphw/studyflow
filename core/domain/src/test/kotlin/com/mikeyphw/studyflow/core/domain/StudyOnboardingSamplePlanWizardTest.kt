package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ExamStageType
import com.mikeyphw.studyflow.core.model.ScheduleRuleId
import com.mikeyphw.studyflow.core.model.StudyTargetId
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyType
import com.mikeyphw.studyflow.core.model.SubjectId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyOnboardingSamplePlanWizardTest {
    @Test
    fun `choices expose every template as preview-first local blueprints`() {
        val choices = StudyOnboardingSamplePlanWizard.choices()

        assertEquals(OnboardingSamplePlanTemplate.entries.size, choices.size)
        assertEquals(OnboardingSamplePlanTemplate.entries.toSet(), choices.map { it.template }.toSet())
        choices.forEach { choice ->
            assertTrue(choice.localOnly)
            assertTrue(choice.previewFirst)
            assertTrue(choice.userInitiatedOnly)
            assertTrue(choice.subjectCount >= 2)
            if (choice.template == OnboardingSamplePlanTemplate.BB_AGENTE_COMERCIAL_PRE_EDITAL) {
                assertEquals(0, choice.topicCount)
            } else {
                assertTrue(choice.topicCount >= choice.subjectCount)
            }
            assertTrue(choice.weeklyGoalHours in 1..100)
            assertTrue(choice.preferredSessionMinutes in 5..240)
            assertFalse(choice.scheduleRules.isEmpty())
        }
    }

    @Test
    fun `public examination sample contains exam-safe defaults and structured content`() {
        val blueprint = StudyOnboardingSamplePlanWizard.build(
            OnboardingSamplePlanInput(OnboardingSamplePlanTemplate.PUBLIC_EXAM_12_WEEK),
        )

        assertEquals(StudyType.PUBLIC_EXAMINATION, blueprint.studyType)
        assertTrue(blueprint.strictExamModeEnabled)
        assertTrue(blueprint.adaptivePlanningEnabled)
        assertEquals(3, blueprint.subjectCount)
        assertEquals(7, blueprint.topicCount)
        assertTrue(
            OnboardingSampleSubjectKind.QUESTIONS_AND_CASES in blueprint.subjects.map { it.kind },
        )
    }

    @Test
    fun `BB Agente Comercial preset preserves official reference weights without invented edital details`() {
        val blueprint = StudyOnboardingSamplePlanWizard.build(
            OnboardingSamplePlanInput(OnboardingSamplePlanTemplate.BB_AGENTE_COMERCIAL_PRE_EDITAL),
        )

        assertEquals(StudyType.PUBLIC_EXAMINATION, blueprint.studyType)
        assertEquals(19, blueprint.weeklyGoalHours)
        assertEquals(8, blueprint.subjectCount)
        assertEquals(0, blueprint.topicCount)
        assertEquals(1_140, blueprint.weeklyScheduleMinutes)
        assertEquals(100.0, blueprint.subjects.sumOf { it.examWeight }, 0.0001)
        assertTrue(blueprint.subjects.all { it.examStageKind == OnboardingSampleExamStageKind.OBJECTIVE })
        assertTrue(blueprint.subjects.all { it.estimatedMinutes == 0 })
        assertEquals(BancoDoBrasilAgenteComercialPreset.ObjectiveQuestionCount, 70)
        assertEquals(BancoDoBrasilAgenteComercialPreset.ObjectiveMaximumPoints, 100.0, 0.0001)
        val profile = requireNotNull(blueprint.publicExam)
        assertEquals("Banco do Brasil", profile.organization)
        assertEquals("Escriturário — Agente Comercial", profile.role)
        assertEquals("Fundação Cesgranrio", profile.examBoard)
        assertEquals(null, profile.examDateIso)
        assertEquals(PublicExamCyclePreference.FIXED_WEEKLY, profile.cyclePreference)
        assertEquals(listOf(1, 7, 30), profile.reviewPolicy.intervalsDays)
        assertEquals(0, profile.weeklyQuestionGoal)
        assertEquals(80, profile.accuracyGoalPercent)
        assertEquals(2, blueprint.examStages.size)
        assertEquals(ExamStageType.OBJECTIVE, blueprint.examStages[0].type)
        assertEquals(100.0, blueprint.examStages[0].weight, 0.0001)
        assertEquals(ExamStageType.ESSAY, blueprint.examStages[1].type)
        assertEquals(70, blueprint.examStages[1].minimumScorePercent)

        var next = 0
        val (stages, ids) = StudyOnboardingSamplePlanWizard.buildExamStages(
            planId = StudyPlanId("bb"),
            targetId = StudyTargetId("bb-target"),
            blueprint = blueprint,
            stageNames = mapOf(
                OnboardingSampleExamStageKind.OBJECTIVE to "Prova objetiva",
                OnboardingSampleExamStageKind.ESSAY to "Redação",
            ),
            idFactory = { "stage-${++next}" },
        )
        assertEquals(2, stages.size)
        assertEquals("Prova objetiva", stages[0].name)
        assertEquals("Redação", stages[1].name)
        assertEquals(stages[0].id, ids.getValue(OnboardingSampleExamStageKind.OBJECTIVE))
    }

    @Test
    fun `wizard rejects non-local automatic or destructive setup`() {
        fun build(input: OnboardingSamplePlanInput) = StudyOnboardingSamplePlanWizard.build(input)

        assertFailsWithMessage("local") {
            build(
                OnboardingSamplePlanInput(
                    template = OnboardingSamplePlanTemplate.FREE_STUDY_HABIT,
                    localOnly = false,
                ),
            )
        }
        assertFailsWithMessage("preview") {
            build(
                OnboardingSamplePlanInput(
                    template = OnboardingSamplePlanTemplate.FREE_STUDY_HABIT,
                    previewFirst = false,
                ),
            )
        }
        assertFailsWithMessage("user initiated") {
            build(
                OnboardingSamplePlanInput(
                    template = OnboardingSamplePlanTemplate.FREE_STUDY_HABIT,
                    userInitiatedOnly = false,
                ),
            )
        }
    }

    @Test
    fun `settings and schedule rules are derived without repository mutation`() {
        val planId = StudyPlanId("onboarding-plan")
        val blueprint = StudyOnboardingSamplePlanWizard.build(
            OnboardingSamplePlanInput(OnboardingSamplePlanTemplate.UNIVERSITY_SEMESTER),
        )
        val settings = StudyOnboardingSamplePlanWizard.buildSettings(planId, blueprint)
        var sequence = 0
        val subjectIds = blueprint.subjects.associate { subject -> subject.kind to SubjectId(subject.kind.name) }
        val rules = StudyOnboardingSamplePlanWizard.buildScheduleRules(
            planId = planId,
            blueprint = blueprint,
            subjectIdsByKind = subjectIds,
            idFactory = { "rule-${++sequence}" },
        )

        assertEquals(planId, settings.studyPlanId)
        assertEquals(blueprint.preferredSessionMinutes, settings.preferredSessionMinutes)
        assertEquals(blueprint.adaptivePlanningEnabled, settings.adaptivePlanningEnabled)
        assertEquals(blueprint.reviewNotificationsEnabled, settings.reviewNotificationsEnabled)
        assertEquals(blueprint.strictExamModeEnabled, settings.strictExamModeEnabled)
        assertEquals(blueprint.scheduleRules.size, rules.size)
        assertEquals(ScheduleRuleId("rule-1"), rules.first().id)
        assertEquals(planId, rules.first().studyPlanId)
        assertTrue(rules.all { rule -> rule.enabled && !rule.locked })
    }
}

private fun assertFailsWithMessage(
    expectedMessagePart: String,
    block: () -> Unit,
) {
    val failure = runCatching(block).exceptionOrNull()
    assertTrue("Expected failure containing $expectedMessagePart", failure is IllegalArgumentException)
    assertTrue(
        "Expected message containing $expectedMessagePart but was ${failure?.message}",
        failure?.message?.contains(expectedMessagePart, ignoreCase = true) == true,
    )
}
