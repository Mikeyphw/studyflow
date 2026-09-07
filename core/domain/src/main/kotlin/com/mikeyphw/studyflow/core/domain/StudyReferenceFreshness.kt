package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudyMaterial
import com.mikeyphw.studyflow.core.model.StudyMaterialId
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class StudyReferenceOrigin {
    EDITAL_2022,
}

enum class StudyReferenceFreshnessStatus {
    UNTRACKED,
    NEEDS_VERIFICATION,
    CURRENT,
    POSSIBLY_STALE,
}

data class StudyReferenceProvenance(
    val origin: StudyReferenceOrigin?,
    val verifiedOn: LocalDate?,
    val supersedesMaterialId: StudyMaterialId?,
)

data class StudyReferenceFreshness(
    val status: StudyReferenceFreshnessStatus,
    val ageDays: Long?,
)

/**
 * Stores provenance in the existing material tag column so backup/restore and portability keep the
 * history without introducing a parallel reference store.
 */
object StudyReferenceProvenanceCodec {
    private const val OriginPrefix = "studyflow:reference-origin="
    private const val VerifiedPrefix = "studyflow:reference-verified-on="
    private const val SupersedesPrefix = "studyflow:reference-supersedes="

    fun decode(material: StudyMaterial): StudyReferenceProvenance = decode(material.tags)

    fun decode(tags: List<String>): StudyReferenceProvenance {
        val origin = tags.valueFor(OriginPrefix)?.let { raw ->
            runCatching { StudyReferenceOrigin.valueOf(raw) }.getOrNull()
        }
        val verifiedOn = tags.valueFor(VerifiedPrefix)?.let { raw ->
            runCatching { LocalDate.parse(raw) }.getOrNull()
        }
        val supersedes = tags.valueFor(SupersedesPrefix)?.takeIf(String::isNotBlank)?.let(::StudyMaterialId)
        return StudyReferenceProvenance(origin, verifiedOn, supersedes)
    }

    fun encode(
        tags: List<String>,
        origin: StudyReferenceOrigin?,
        verifiedOn: LocalDate?,
        supersedesMaterialId: StudyMaterialId?,
    ): List<String> = buildList {
        addAll(tags.filterNot(::isManagedTag))
        origin?.let { add("$OriginPrefix${it.name}") }
        verifiedOn?.let { add("$VerifiedPrefix$it") }
        supersedesMaterialId?.let { add("$SupersedesPrefix${it.value}") }
    }.distinct()

    private fun List<String>.valueFor(prefix: String): String? =
        firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)

    private fun isManagedTag(tag: String): Boolean =
        tag.startsWith(OriginPrefix) || tag.startsWith(VerifiedPrefix) || tag.startsWith(SupersedesPrefix)
}

/** Product freshness policy, not an edital claim. */
object StudyReferenceFreshnessPolicy {
    const val PossiblyStaleAfterDays: Long = 90

    fun evaluate(material: StudyMaterial, today: LocalDate): StudyReferenceFreshness =
        evaluate(StudyReferenceProvenanceCodec.decode(material), today)

    fun evaluate(provenance: StudyReferenceProvenance, today: LocalDate): StudyReferenceFreshness {
        if (provenance.origin == null) return StudyReferenceFreshness(StudyReferenceFreshnessStatus.UNTRACKED, null)
        val verifiedOn = provenance.verifiedOn
            ?: return StudyReferenceFreshness(StudyReferenceFreshnessStatus.NEEDS_VERIFICATION, null)
        val age = ChronoUnit.DAYS.between(verifiedOn, today).coerceAtLeast(0)
        return StudyReferenceFreshness(
            status = if (age > PossiblyStaleAfterDays) {
                StudyReferenceFreshnessStatus.POSSIBLY_STALE
            } else {
                StudyReferenceFreshnessStatus.CURRENT
            },
            ageDays = age,
        )
    }
}
