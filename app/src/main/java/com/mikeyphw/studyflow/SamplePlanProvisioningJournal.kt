package com.mikeyphw.studyflow

import android.content.Context
import com.mikeyphw.studyflow.core.domain.OnboardingSamplePlanTemplate
import com.mikeyphw.studyflow.core.model.StudyPlanId

internal data class PendingSampleProvisioning(
    val template: OnboardingSamplePlanTemplate,
    val proposedPlanId: StudyPlanId,
)

internal interface SamplePlanProvisioningJournal {
    fun pending(): PendingSampleProvisioning?
    fun begin(template: OnboardingSamplePlanTemplate, proposedPlanId: StudyPlanId)
    fun updateCommittedPlan(template: OnboardingSamplePlanTemplate, planId: StudyPlanId)
    fun consume(template: OnboardingSamplePlanTemplate, planId: StudyPlanId)
    fun clearPending()
}

internal class SharedPreferencesSamplePlanProvisioningJournal(context: Context) : SamplePlanProvisioningJournal {
    private val preferences = context.applicationContext.getSharedPreferences(
        "studyflow_sample_provisioning_journal_v1",
        Context.MODE_PRIVATE,
    )

    override fun pending(): PendingSampleProvisioning? {
        val templateName = preferences.getString(TemplateKey, null) ?: return null
        val planId = preferences.getString(PlanIdKey, null)?.takeIf(String::isNotBlank) ?: return null
        val template = OnboardingSamplePlanTemplate.entries.firstOrNull { it.name == templateName } ?: return null
        return PendingSampleProvisioning(template, StudyPlanId(planId))
    }

    override fun begin(template: OnboardingSamplePlanTemplate, proposedPlanId: StudyPlanId) {
        preferences.edit()
            .putString(TemplateKey, template.name)
            .putString(PlanIdKey, proposedPlanId.value)
            .commit()
    }

    override fun updateCommittedPlan(template: OnboardingSamplePlanTemplate, planId: StudyPlanId) {
        begin(template, planId)
    }

    override fun consume(template: OnboardingSamplePlanTemplate, planId: StudyPlanId) {
        val current = pending()
        if (current?.template == template && current.proposedPlanId == planId) {
            clearPending()
        }
    }

    override fun clearPending() {
        preferences.edit().remove(TemplateKey).remove(PlanIdKey).commit()
    }

    private companion object {
        const val TemplateKey = "pending_template"
        const val PlanIdKey = "pending_plan_id"
    }
}

internal class InMemorySamplePlanProvisioningJournal : SamplePlanProvisioningJournal {
    private var value: PendingSampleProvisioning? = null
    override fun pending(): PendingSampleProvisioning? = value
    override fun begin(template: OnboardingSamplePlanTemplate, proposedPlanId: StudyPlanId) {
        value = PendingSampleProvisioning(template, proposedPlanId)
    }
    override fun updateCommittedPlan(template: OnboardingSamplePlanTemplate, planId: StudyPlanId) {
        value = PendingSampleProvisioning(template, planId)
    }
    override fun consume(template: OnboardingSamplePlanTemplate, planId: StudyPlanId) {
        if (value?.template == template && value?.proposedPlanId == planId) value = null
    }
    override fun clearPending() {
        value = null
    }
}
