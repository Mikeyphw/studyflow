package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ExamNoticeRevision
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyTarget
import com.mikeyphw.studyflow.core.model.StudyType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class PublicExamDateSource {
    CONFIRMED_TARGET,
    EXPECTED_TARGET,
    LEGACY_PLAN,
    NONE,
}

data class PublicExamResolvedDate(
    val dateIso: String?,
    val source: PublicExamDateSource,
) {
    val isConfirmed: Boolean get() = source == PublicExamDateSource.CONFIRMED_TARGET
}

object PublicExamDateResolver {
    fun resolve(plan: StudyPlan, target: StudyTarget?): PublicExamResolvedDate {
        require(target == null || target.studyPlanId == plan.id) {
            "Public-exam target belongs to another Study Plan."
        }
        return when {
            !target?.confirmedExamDateIso.isNullOrBlank() -> PublicExamResolvedDate(
                requireNotNull(target).confirmedExamDateIso,
                PublicExamDateSource.CONFIRMED_TARGET,
            )
            !target?.expectedExamDateIso.isNullOrBlank() -> PublicExamResolvedDate(
                requireNotNull(target).expectedExamDateIso,
                PublicExamDateSource.EXPECTED_TARGET,
            )
            !plan.examDateIso.isNullOrBlank() -> PublicExamResolvedDate(
                plan.examDateIso,
                PublicExamDateSource.LEGACY_PLAN,
            )
            else -> PublicExamResolvedDate(null, PublicExamDateSource.NONE)
        }
    }

    fun officialCountdownDate(plan: StudyPlan, target: StudyTarget?): PublicExamResolvedDate? {
        val resolved = resolve(plan, target)
        return when (resolved.source) {
            PublicExamDateSource.CONFIRMED_TARGET -> resolved
            PublicExamDateSource.LEGACY_PLAN -> resolved.takeIf { target == null }
            PublicExamDateSource.EXPECTED_TARGET, PublicExamDateSource.NONE -> null
        }
    }

    fun daysUntilOfficial(
        plan: StudyPlan,
        target: StudyTarget?,
        nowEpochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Int? {
        val iso = officialCountdownDate(plan, target)?.dateIso ?: return null
        val examDate = LocalDate.parse(iso)
        val today = Instant.ofEpochMilli(nowEpochMillis).atZone(zoneId).toLocalDate()
        return ChronoUnit.DAYS.between(today, examDate).toInt().coerceAtLeast(0)
    }
}

enum class PublicExamPreEditalIssue {
    NO_CONFIRMED_EXAM_DATE,
    NO_REFERENCE_NOTICE,
    REFERENCE_PUBLICATION_DATE_UNKNOWN,
    STALE_REFERENCE_NOTICE,
}

data class PublicExamPreEditalSnapshot(
    val preEdital: Boolean,
    val resolvedDate: PublicExamResolvedDate,
    val latestRevision: ExamNoticeRevision?,
    val referenceLabel: String?,
    val referenceAgeDays: Long?,
    val issues: Set<PublicExamPreEditalIssue>,
) {
    val verificationCount: Int get() = issues.size
    val officialCountdownDays: Int? get() = null
}

object PublicExamPreEditalBuilder {
    const val DefaultStaleReferenceDays = 365L

    fun build(
        plan: StudyPlan,
        target: StudyTarget?,
        revisions: List<ExamNoticeRevision>,
        nowEpochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        staleReferenceDays: Long = DefaultStaleReferenceDays,
    ): PublicExamPreEditalSnapshot? {
        if (plan.type != StudyType.PUBLIC_EXAMINATION) return null
        require(staleReferenceDays > 0) { "Reference staleness window must be positive." }
        require(target == null || target.studyPlanId == plan.id) {
            "Public-exam target belongs to another Study Plan."
        }
        require(revisions.all { revision -> revision.studyPlanId == plan.id }) {
            "Exam notice revision belongs to another Study Plan."
        }
        target?.let { savedTarget ->
            require(revisions.all { revision -> revision.studyTargetId == savedTarget.id }) {
                "Exam notice revision belongs to another Study Target."
            }
        }

        val resolvedDate = PublicExamDateResolver.resolve(plan, target)
        val latest = revisions.maxWithOrNull(
            compareBy<ExamNoticeRevision> { it.importedAtEpochMillis }.thenBy { it.id.value },
        )
        val preEdital = !resolvedDate.isConfirmed
        if (!preEdital) {
            return PublicExamPreEditalSnapshot(
                preEdital = false,
                resolvedDate = resolvedDate,
                latestRevision = latest,
                referenceLabel = latest?.label ?: target?.noticeUrl,
                referenceAgeDays = latest?.publicationDateIso?.let {
                    ageDays(it, nowEpochMillis, zoneId)
                },
                issues = emptySet(),
            )
        }

        val referenceAgeDays = latest?.publicationDateIso?.let { publicationDate ->
            ageDays(publicationDate, nowEpochMillis, zoneId)
        }
        val issues = buildSet {
            add(PublicExamPreEditalIssue.NO_CONFIRMED_EXAM_DATE)
            if (latest == null && target?.noticeUrl.isNullOrBlank()) {
                add(PublicExamPreEditalIssue.NO_REFERENCE_NOTICE)
            }
            if (
                (latest != null && latest.publicationDateIso == null) ||
                (latest == null && !target?.noticeUrl.isNullOrBlank())
            ) {
                add(PublicExamPreEditalIssue.REFERENCE_PUBLICATION_DATE_UNKNOWN)
            }
            if (referenceAgeDays != null && referenceAgeDays > staleReferenceDays) {
                add(PublicExamPreEditalIssue.STALE_REFERENCE_NOTICE)
            }
        }
        return PublicExamPreEditalSnapshot(
            preEdital = true,
            resolvedDate = resolvedDate,
            latestRevision = latest,
            referenceLabel = latest?.label ?: target?.noticeUrl,
            referenceAgeDays = referenceAgeDays,
            issues = issues,
        )
    }

    private fun ageDays(publicationDateIso: String, nowEpochMillis: Long, zoneId: ZoneId): Long {
        val publicationDate = LocalDate.parse(publicationDateIso)
        val today = Instant.ofEpochMilli(nowEpochMillis).atZone(zoneId).toLocalDate()
        return ChronoUnit.DAYS.between(publicationDate, today).coerceAtLeast(0)
    }
}
