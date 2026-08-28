package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.ExamNoticeRevisionId
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudySyllabus
import com.mikeyphw.studyflow.core.model.StudyTargetId
import com.mikeyphw.studyflow.core.model.Subject
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.Topic
import com.mikeyphw.studyflow.core.model.TopicId
import java.security.MessageDigest
import java.text.Normalizer
import java.time.LocalDate

enum class ExamNoticeDiffKind {
    ADDED,
    REMOVED,
    CHANGED,
    UNCHANGED,
}

data class ExamNoticeDiffItem(
    val kind: ExamNoticeDiffKind,
    val existingSubjectId: SubjectId?,
    val existingTopicId: TopicId?,
    val incomingSubjectKey: String?,
    val incomingTopicKey: String?,
    val beforeName: String?,
    val afterName: String?,
) {
    init {
        require(beforeName != null || afterName != null) { "Exam notice diff item must describe content." }
        when (kind) {
            ExamNoticeDiffKind.ADDED -> require(afterName != null) { "Added content requires an incoming name." }
            ExamNoticeDiffKind.REMOVED -> require(beforeName != null) { "Removed content requires an existing name." }
            ExamNoticeDiffKind.CHANGED -> require(beforeName != null && afterName != null) {
                "Changed content requires both names."
            }
            ExamNoticeDiffKind.UNCHANGED -> require(beforeName != null && afterName != null) {
                "Unchanged content requires both names."
            }
        }
    }
}

data class ExamNoticeRevisionPreview(
    val studyPlanId: StudyPlanId,
    val studyTargetId: StudyTargetId,
    val baseRevisionId: ExamNoticeRevisionId?,
    val label: String,
    val sourceReference: String?,
    val normalizedContentSha256: String,
    val parserVersion: String,
    val publicationDateIso: String?,
    val baseSyllabusSha256: String,
    val draft: SyllabusImportDraft,
    val changes: List<ExamNoticeDiffItem>,
) {
    val addedCount: Int get() = changes.count { it.kind == ExamNoticeDiffKind.ADDED }
    val removedCount: Int get() = changes.count { it.kind == ExamNoticeDiffKind.REMOVED }
    val changedCount: Int get() = changes.count { it.kind == ExamNoticeDiffKind.CHANGED }
    val unchangedCount: Int get() = changes.count { it.kind == ExamNoticeDiffKind.UNCHANGED }
    val hasMutation: Boolean get() = addedCount + removedCount + changedCount > 0
}

data class ExamNoticeCoverage(
    val totalCanonicalItems: Int,
    val studiedCanonicalItems: Int,
    val coveragePercent: Int,
)

object ExamNoticeRevisionPlanner {
    const val ParserVersion = "cpov02-normalized-v1"

    fun preview(
        planId: StudyPlanId,
        targetId: StudyTargetId,
        baseRevisionId: ExamNoticeRevisionId?,
        importPreview: ExamNoticeImportPreview,
        existing: StudySyllabus,
        publicationDateIso: String? = null,
    ): ExamNoticeRevisionPreview {
        require(importPreview.canApply) { "Exam notice import preview is not applicable." }
        require(existing.studyPlanId == planId) { "Existing syllabus belongs to another Study Plan." }
        publicationDateIso?.let { date ->
            require(runCatching { LocalDate.parse(date) }.isSuccess) {
                "Exam notice publication date must be a real ISO date."
            }
        }
        val changes = compare(existing, importPreview.draft)
        return ExamNoticeRevisionPreview(
            studyPlanId = planId,
            studyTargetId = targetId,
            baseRevisionId = baseRevisionId,
            label = importPreview.source.displayName.trim().ifBlank { "Exam notice revision" },
            sourceReference = importPreview.source.displayName.trim().ifBlank { null },
            normalizedContentSha256 = canonicalDraftSha256(importPreview.draft),
            parserVersion = ParserVersion,
            publicationDateIso = publicationDateIso,
            baseSyllabusSha256 = canonicalSyllabusSha256(existing),
            draft = importPreview.draft,
            changes = changes,
        )
    }

    fun coverage(syllabus: StudySyllabus): ExamNoticeCoverage {
        val activeSubjects = syllabus.subjects.filterNot(Subject::archived)
        val activeSubjectIds = activeSubjects.mapTo(mutableSetOf(), Subject::id)
        val activeTopics = syllabus.topics.filter { topic ->
            !topic.archived && topic.subjectId in activeSubjectIds
        }
        val total = activeSubjects.size + activeTopics.size
        val studied = activeSubjects.count { it.progressPercent > 0 } +
            activeTopics.count { it.progressPercent > 0 || it.masteryPercent > 0 }
        return ExamNoticeCoverage(
            totalCanonicalItems = total,
            studiedCanonicalItems = studied,
            coveragePercent = if (total == 0) 0 else ((studied * 100.0) / total).toInt().coerceIn(0, 100),
        )
    }

    fun canonicalSyllabusSha256(syllabus: StudySyllabus): String = sha256(
        buildString {
            append("plan=").append(syllabus.studyPlanId.value).append('\n')
            syllabus.subjects.sortedWith(compareBy<Subject> { it.position }.thenBy { it.id.value }).forEach { subject ->
                append("S|").append(subject.id.value).append('|').append(normalize(subject.name))
                    .append('|').append(subject.position).append('|').append(subject.archived).append('\n')
            }
            syllabus.topics.sortedWith(
                compareBy<Topic> { it.subjectId.value }.thenBy { it.position }.thenBy { it.id.value },
            ).forEach { topic ->
                append("T|").append(topic.id.value).append('|').append(topic.subjectId.value).append('|')
                    .append(topic.parentTopicId?.value.orEmpty()).append('|').append(normalize(topic.name))
                    .append('|').append(topic.position).append('|').append(topic.archived).append('\n')
            }
        },
    )

    fun canonicalDraftSha256(draft: SyllabusImportDraft): String = sha256(
        buildString {
            draft.subjects.forEachIndexed { subjectIndex, subject ->
                append("S|").append(subjectIndex).append('|').append(normalize(subject.name)).append('|')
                    .append(subject.priority).append('|').append(subject.examWeight).append('\n')
                subject.topics.forEachIndexed { topicIndex, topic ->
                    append("T|").append(subjectIndex).append('|').append(topicIndex).append('|')
                        .append(topic.parentKey.orEmpty()).append('|').append(normalize(topic.name)).append('|')
                        .append(topic.priority).append('|').append(topic.difficulty).append('|')
                        .append(topic.examIncidencePercent ?: "").append('\n')
                }
            }
        },
    )

    private fun compare(existing: StudySyllabus, draft: SyllabusImportDraft): List<ExamNoticeDiffItem> {
        val changes = mutableListOf<ExamNoticeDiffItem>()
        val activeSubjects = existing.subjects.filterNot(Subject::archived).sortedBy(Subject::position)
        val unmatchedExisting = activeSubjects.toMutableList()
        val subjectMatches = linkedMapOf<String, Subject>()

        draft.subjects.forEach { incoming ->
            val exact = unmatchedExisting.firstOrNull { subject -> normalize(subject.name) == normalize(incoming.name) }
            if (exact != null) {
                unmatchedExisting.remove(exact)
                subjectMatches[incoming.key] = exact
                changes += ExamNoticeDiffItem(
                    kind = ExamNoticeDiffKind.UNCHANGED,
                    existingSubjectId = exact.id,
                    existingTopicId = null,
                    incomingSubjectKey = incoming.key,
                    incomingTopicKey = null,
                    beforeName = exact.name,
                    afterName = incoming.name,
                )
            }
        }

        draft.subjects.filterNot { it.key in subjectMatches }.forEach { incoming ->
            val candidate = bestSubjectRename(incoming.name, unmatchedExisting)
            if (candidate != null) {
                unmatchedExisting.remove(candidate)
                subjectMatches[incoming.key] = candidate
                changes += ExamNoticeDiffItem(
                    kind = ExamNoticeDiffKind.CHANGED,
                    existingSubjectId = candidate.id,
                    existingTopicId = null,
                    incomingSubjectKey = incoming.key,
                    incomingTopicKey = null,
                    beforeName = candidate.name,
                    afterName = incoming.name,
                )
            } else {
                changes += ExamNoticeDiffItem(
                    kind = ExamNoticeDiffKind.ADDED,
                    existingSubjectId = null,
                    existingTopicId = null,
                    incomingSubjectKey = incoming.key,
                    incomingTopicKey = null,
                    beforeName = null,
                    afterName = incoming.name,
                )
            }
        }

        unmatchedExisting.forEach { subject ->
            changes += ExamNoticeDiffItem(
                kind = ExamNoticeDiffKind.REMOVED,
                existingSubjectId = subject.id,
                existingTopicId = null,
                incomingSubjectKey = null,
                incomingTopicKey = null,
                beforeName = subject.name,
                afterName = null,
            )
        }

        draft.subjects.forEach { incomingSubject ->
            val existingSubject = subjectMatches[incomingSubject.key]
            if (existingSubject == null) {
                incomingSubject.topics.forEach { topic ->
                    changes += ExamNoticeDiffItem(
                        kind = ExamNoticeDiffKind.ADDED,
                        existingSubjectId = null,
                        existingTopicId = null,
                        incomingSubjectKey = incomingSubject.key,
                        incomingTopicKey = topic.key,
                        beforeName = null,
                        afterName = topic.name,
                    )
                }
                return@forEach
            }
            compareTopics(
                existingTopics = existing.topics
                    .filter { topic -> topic.subjectId == existingSubject.id && !topic.archived }
                    .sortedBy(Topic::position),
                incomingSubject = incomingSubject,
                existingSubject = existingSubject,
                changes = changes,
            )
        }
        return changes
    }

    private fun compareTopics(
        existingTopics: List<Topic>,
        incomingSubject: SyllabusSubjectDraft,
        existingSubject: Subject,
        changes: MutableList<ExamNoticeDiffItem>,
    ) {
        val unmatchedExisting = existingTopics.toMutableList()
        val matchedIncoming = mutableSetOf<String>()
        incomingSubject.topics.forEach { incoming ->
            val exact = unmatchedExisting.firstOrNull { topic -> normalize(topic.name) == normalize(incoming.name) }
            if (exact != null) {
                unmatchedExisting.remove(exact)
                matchedIncoming += incoming.key
                changes += ExamNoticeDiffItem(
                    kind = ExamNoticeDiffKind.UNCHANGED,
                    existingSubjectId = existingSubject.id,
                    existingTopicId = exact.id,
                    incomingSubjectKey = incomingSubject.key,
                    incomingTopicKey = incoming.key,
                    beforeName = exact.name,
                    afterName = incoming.name,
                )
            }
        }
        incomingSubject.topics.filterNot { it.key in matchedIncoming }.forEach { incoming ->
            val candidate = bestTopicRename(incoming.name, unmatchedExisting)
            if (candidate != null) {
                unmatchedExisting.remove(candidate)
                changes += ExamNoticeDiffItem(
                    kind = ExamNoticeDiffKind.CHANGED,
                    existingSubjectId = existingSubject.id,
                    existingTopicId = candidate.id,
                    incomingSubjectKey = incomingSubject.key,
                    incomingTopicKey = incoming.key,
                    beforeName = candidate.name,
                    afterName = incoming.name,
                )
            } else {
                changes += ExamNoticeDiffItem(
                    kind = ExamNoticeDiffKind.ADDED,
                    existingSubjectId = existingSubject.id,
                    existingTopicId = null,
                    incomingSubjectKey = incomingSubject.key,
                    incomingTopicKey = incoming.key,
                    beforeName = null,
                    afterName = incoming.name,
                )
            }
        }
        unmatchedExisting.forEach { topic ->
            changes += ExamNoticeDiffItem(
                kind = ExamNoticeDiffKind.REMOVED,
                existingSubjectId = existingSubject.id,
                existingTopicId = topic.id,
                incomingSubjectKey = null,
                incomingTopicKey = null,
                beforeName = topic.name,
                afterName = null,
            )
        }
    }

    private fun bestSubjectRename(name: String, candidates: List<Subject>): Subject? = candidates
        .map { candidate -> candidate to similarity(name, candidate.name) }
        .filter { (_, score) -> score >= 0.58 }
        .sortedWith(compareByDescending<Pair<Subject, Double>> { it.second }.thenBy { it.first.position })
        .firstOrNull()
        ?.first

    private fun bestTopicRename(name: String, candidates: List<Topic>): Topic? = candidates
        .map { candidate -> candidate to similarity(name, candidate.name) }
        .filter { (_, score) -> score >= 0.62 }
        .sortedWith(compareByDescending<Pair<Topic, Double>> { it.second }.thenBy { it.first.position })
        .firstOrNull()
        ?.first

    private fun similarity(left: String, right: String): Double {
        val a = normalize(left)
        val b = normalize(right)
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val distance = levenshtein(a, b)
        return 1.0 - distance.toDouble() / maxOf(a.length, b.length).toDouble()
    }

    private fun levenshtein(left: String, right: String): Int {
        var previous = IntArray(right.length + 1) { it }
        left.forEachIndexed { leftIndex, leftChar ->
            val current = IntArray(right.length + 1)
            current[0] = leftIndex + 1
            right.forEachIndexed { rightIndex, rightChar ->
                current[rightIndex + 1] = minOf(
                    current[rightIndex] + 1,
                    previous[rightIndex + 1] + 1,
                    previous[rightIndex] + if (leftChar == rightChar) 0 else 1,
                )
            }
            previous = current
        }
        return previous[right.length]
    }

    private fun normalize(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.encodeToByteArray())
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
