package com.mikeyphw.studyflow.core.model

enum class LegislationProvisionKind {
    ARTICLE,
    PARAGRAPH,
    ITEM,
    SUBITEM,
    OTHER,
}

data class LegislationDocument(
    val id: LegislationDocumentId,
    val studyPlanId: StudyPlanId,
    val title: String,
    val sourceReference: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    init {
        require(title.isNotBlank()) { "Legislation document title cannot be blank." }
    }
}

data class LegislationProvision(
    val id: LegislationProvisionId,
    val legislationDocumentId: LegislationDocumentId,
    val studyPlanId: StudyPlanId,
    val reference: String,
    val parentReference: String?,
    val kind: LegislationProvisionKind,
    val text: String,
    val position: Int,
    val read: Boolean,
    val important: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    init {
        require(reference.isNotBlank()) { "Legislation provision reference cannot be blank." }
        require(text.isNotBlank()) { "Legislation provision text cannot be blank." }
        require(position >= 0) { "Legislation provision position cannot be negative." }
    }
}

data class LegislationEvidenceLink(
    val id: LegislationEvidenceLinkId,
    val studyPlanId: StudyPlanId,
    val provisionId: LegislationProvisionId,
    val questionId: QuestionId? = null,
    val errorEntryId: ErrorEntryId? = null,
    val reviewItemId: ReviewItemId? = null,
) {
    init {
        require(listOf(questionId, errorEntryId, reviewItemId).count { it != null } == 1) {
            "Legislation evidence link must reference exactly one evidence target."
        }
    }
}

data class LegislationStudySnapshot(
    val studyPlanId: StudyPlanId,
    val documents: List<LegislationDocument>,
    val provisions: List<LegislationProvision>,
    val evidenceLinks: List<LegislationEvidenceLink>,
) {
    fun linksFor(provisionId: LegislationProvisionId): List<LegislationEvidenceLink> =
        evidenceLinks.filter { it.provisionId == provisionId }
}
