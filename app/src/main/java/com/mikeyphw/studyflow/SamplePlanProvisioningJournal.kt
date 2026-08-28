package com.mikeyphw.studyflow

import android.content.Context
import androidx.core.content.edit
import com.mikeyphw.studyflow.core.domain.OnboardingSamplePlanTemplate
import com.mikeyphw.studyflow.core.domain.PublicExamEditalImportChoice
import com.mikeyphw.studyflow.core.model.StudyPlanId

internal data class PendingSampleProvisioning(
    val template: OnboardingSamplePlanTemplate,
    val proposedPlanId: StudyPlanId,
    val editalImportChoice: PublicExamEditalImportChoice = PublicExamEditalImportChoice.LATER,
)

internal interface SamplePlanProvisioningJournal {
    fun pending(): PendingSampleProvisioning?
    fun begin(
        template: OnboardingSamplePlanTemplate,
        proposedPlanId: StudyPlanId,
        editalImportChoice: PublicExamEditalImportChoice = PublicExamEditalImportChoice.LATER,
    )
    fun updateCommittedPlan(
        template: OnboardingSamplePlanTemplate,
        planId: StudyPlanId,
        editalImportChoice: PublicExamEditalImportChoice? = null,
    )
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
        val choiceName = preferences.getString(EditalImportChoiceKey, null)
        val choice = PublicExamEditalImportChoice.entries.firstOrNull { it.name == choiceName }
            ?: PublicExamEditalImportChoice.LATER
        return PendingSampleProvisioning(template, StudyPlanId(planId), choice)
    }

    override fun begin(
        template: OnboardingSamplePlanTemplate,
        proposedPlanId: StudyPlanId,
        editalImportChoice: PublicExamEditalImportChoice,
    ) {
        preferences.edit(commit = true) {
            putString(TemplateKey, template.name)
            putString(PlanIdKey, proposedPlanId.value)
            putString(EditalImportChoiceKey, editalImportChoice.name)
        }
    }

    override fun updateCommittedPlan(
        template: OnboardingSamplePlanTemplate,
        planId: StudyPlanId,
        editalImportChoice: PublicExamEditalImportChoice?,
    ) {
        val choice = editalImportChoice ?: pending()?.editalImportChoice ?: PublicExamEditalImportChoice.LATER
        begin(template, planId, choice)
    }

    override fun consume(template: OnboardingSamplePlanTemplate, planId: StudyPlanId) {
        val current = pending()
        if (current?.template == template && current.proposedPlanId == planId) {
            clearPending()
        }
    }

    override fun clearPending() {
        preferences.edit(commit = true) {
            remove(TemplateKey)
            remove(PlanIdKey)
            remove(EditalImportChoiceKey)
        }
    }

    private companion object {
        const val TemplateKey = "pending_template"
        const val PlanIdKey = "pending_plan_id"
        const val EditalImportChoiceKey = "pending_edital_import_choice"
    }
}

internal class InMemorySamplePlanProvisioningJournal : SamplePlanProvisioningJournal {
    private var value: PendingSampleProvisioning? = null
    override fun pending(): PendingSampleProvisioning? = value

    fun begin(template: OnboardingSamplePlanTemplate, proposedPlanId: StudyPlanId) {
        begin(template, proposedPlanId, PublicExamEditalImportChoice.LATER)
    }
    override fun begin(
        template: OnboardingSamplePlanTemplate,
        proposedPlanId: StudyPlanId,
        editalImportChoice: PublicExamEditalImportChoice,
    ) {
        value = PendingSampleProvisioning(template, proposedPlanId, editalImportChoice)
    }
    override fun updateCommittedPlan(
        template: OnboardingSamplePlanTemplate,
        planId: StudyPlanId,
        editalImportChoice: PublicExamEditalImportChoice?,
    ) {
        val choice = editalImportChoice ?: value?.editalImportChoice ?: PublicExamEditalImportChoice.LATER
        value = PendingSampleProvisioning(template, planId, choice)
    }
    override fun consume(template: OnboardingSamplePlanTemplate, planId: StudyPlanId) {
        if (value?.template == template && value?.proposedPlanId == planId) value = null
    }
    override fun clearPending() {
        value = null
    }
}
