package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ScheduleRuleId
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
            assertTrue(choice.topicCount >= choice.subjectCount)
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
