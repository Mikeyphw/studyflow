package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.IdiomCard
import com.mikeyphw.studyflow.core.model.LanguageDeckProfile
import com.mikeyphw.studyflow.core.model.LanguageExpression
import com.mikeyphw.studyflow.core.model.LanguageExpressionType
import com.mikeyphw.studyflow.core.model.LanguageReviewAttempt
import com.mikeyphw.studyflow.core.model.LanguageReviewOutcome
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.VocabularyCard
import kotlinx.coroutines.flow.Flow

/** Production repository for Language Deck plans. */
interface LanguageLearningRepository {
    fun observeSnapshot(planId: StudyPlanId): Flow<LanguageLearningSnapshot>

    suspend fun saveDeck(deck: LanguageDeckProfile): LanguageDeckProfile
    suspend fun saveExpression(expression: LanguageExpression): LanguageExpression
    suspend fun saveIdiomCard(card: IdiomCard): IdiomCard
    suspend fun saveVocabularyCard(card: VocabularyCard): VocabularyCard
    suspend fun recordReviewAttempt(planId: StudyPlanId, attempt: LanguageReviewAttempt): LanguageReviewAttempt
    suspend fun applyImportPreview(planId: StudyPlanId, preview: StudyFlowLanguageImportPreview): LanguageLearningSnapshot
}

data class LanguageLearningSnapshot(
    val studyPlanId: StudyPlanId,
    val deck: LanguageDeckProfile?,
    val expressions: List<LanguageExpression>,
    val reviewAttempts: List<LanguageReviewAttempt>,
) {
    val idiomCount: Int = expressions.count { expression -> expression.type == LanguageExpressionType.IDIOM }
    val vocabularyCount: Int = expressions.count { expression -> expression.type == LanguageExpressionType.VOCABULARY }
    val learnedCount: Int = reviewAttempts
        .filter { attempt -> attempt.outcome == LanguageReviewOutcome.CORRECT }
        .map { attempt -> attempt.expressionId }
        .distinct()
        .size
    val dueCount: Int = (expressions.size - learnedCount).coerceAtLeast(0)
    val usageAccuracyPercent: Int = if (reviewAttempts.isEmpty()) 0 else {
        val correct = reviewAttempts.count { attempt -> attempt.outcome == LanguageReviewOutcome.CORRECT }
        ((correct.toDouble() / reviewAttempts.size.toDouble()) * 100.0).toInt().coerceIn(0, 100)
    }
    val registerCoveragePercent: Int = if (expressions.isEmpty()) 0 else {
        val covered = expressions.map { expression -> expression.register }.distinct().size
        ((covered.toDouble() / 6.0) * 100.0).toInt().coerceIn(0, 100)
    }
}

data class StudyFlowLanguageImportPreview(
    val sourcePreview: com.mikeyphw.studyflow.core.model.StudyFlowImportPreview,
) {
    init {
        require(sourcePreview.canImport) { "Language import preview must be valid before applying." }
        require(sourcePreview.rows.all { row ->
            row.kind == com.mikeyphw.studyflow.core.model.StudyFlowImportRowKind.LANGUAGE_IDIOM
        }) { "Language import preview contains non-language rows." }
    }
}

class ManageLanguageLearning(
    private val repository: LanguageLearningRepository,
) {
    fun observeSnapshot(planId: StudyPlanId): Flow<LanguageLearningSnapshot> = repository.observeSnapshot(planId)

    suspend fun saveDeck(deck: LanguageDeckProfile): LanguageDeckProfile = repository.saveDeck(deck)

    suspend fun saveExpression(expression: LanguageExpression): LanguageExpression = repository.saveExpression(expression)

    suspend fun saveIdiomCard(card: IdiomCard): IdiomCard = repository.saveIdiomCard(card)

    suspend fun saveVocabularyCard(card: VocabularyCard): VocabularyCard = repository.saveVocabularyCard(card)

    suspend fun recordReviewAttempt(planId: StudyPlanId, attempt: LanguageReviewAttempt): LanguageReviewAttempt =
        repository.recordReviewAttempt(planId, attempt)

    suspend fun applyImportPreview(
        planId: StudyPlanId,
        preview: StudyFlowLanguageImportPreview,
    ): LanguageLearningSnapshot = repository.applyImportPreview(planId, preview)
}
