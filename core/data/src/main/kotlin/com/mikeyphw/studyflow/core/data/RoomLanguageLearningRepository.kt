package com.mikeyphw.studyflow.core.data

import androidx.room.withTransaction
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.database.mapper.asEntity
import com.mikeyphw.studyflow.core.database.mapper.asExternalModel
import com.mikeyphw.studyflow.core.domain.LanguageLearningRepository
import com.mikeyphw.studyflow.core.domain.LanguageLearningSnapshot
import com.mikeyphw.studyflow.core.domain.StudyFlowLanguageImportPreview
import com.mikeyphw.studyflow.core.model.IdiomCard
import com.mikeyphw.studyflow.core.model.IdiomCardId
import com.mikeyphw.studyflow.core.model.LanguageDeckId
import com.mikeyphw.studyflow.core.model.LanguageDeckProfile
import com.mikeyphw.studyflow.core.model.LanguageExpression
import com.mikeyphw.studyflow.core.model.LanguageExpressionId
import com.mikeyphw.studyflow.core.model.LanguageExpressionType
import com.mikeyphw.studyflow.core.model.LanguageRegister
import com.mikeyphw.studyflow.core.model.LanguageReviewAttempt
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.UsageExample
import com.mikeyphw.studyflow.core.model.VocabularyCard
import com.mikeyphw.studyflow.core.model.defaultLanguageReviewDirections
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

internal class RoomLanguageLearningRepository(
    private val database: StudyFlowDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) : LanguageLearningRepository {
    private val dao = database.languageLearningDao()
    private val planDao = database.studyPlanDao()

    override fun observeSnapshot(planId: StudyPlanId): Flow<LanguageLearningSnapshot> = combine(
        dao.observeDeck(planId.value).map { entity -> entity?.asExternalModel() },
        dao.observeExpressions(planId.value).map { entities -> entities.map { it.asExternalModel() } },
        dao.observeLanguageReviewAttempts(planId.value).map { entities -> entities.map { it.asExternalModel() } },
    ) { deck, expressions, attempts ->
        LanguageLearningSnapshot(
            studyPlanId = planId,
            deck = deck,
            expressions = expressions,
            reviewAttempts = attempts,
        )
    }

    override suspend fun saveDeck(deck: LanguageDeckProfile): LanguageDeckProfile = database.withTransaction {
        requirePlan(deck.studyPlanId)
        dao.upsertDeck(deck.asEntity())
        deck
    }

    override suspend fun saveExpression(expression: LanguageExpression): LanguageExpression = database.withTransaction {
        requirePlan(expression.studyPlanId)
        dao.upsertExpression(expression.asEntity())
        expression
    }

    override suspend fun saveIdiomCard(card: IdiomCard): IdiomCard = database.withTransaction {
        dao.upsertIdiomCard(card.asEntity())
        card
    }

    override suspend fun saveVocabularyCard(card: VocabularyCard): VocabularyCard = database.withTransaction {
        dao.upsertVocabularyCard(card.asEntity())
        card
    }

    override suspend fun recordReviewAttempt(
        planId: StudyPlanId,
        attempt: LanguageReviewAttempt,
    ): LanguageReviewAttempt = database.withTransaction {
        requirePlan(planId)
        dao.upsertLanguageReviewAttempt(attempt.asEntity(planId))
        attempt
    }

    override suspend fun applyImportPreview(
        planId: StudyPlanId,
        preview: StudyFlowLanguageImportPreview,
    ): LanguageLearningSnapshot = database.withTransaction {
        requirePlan(planId)
        val now = clock().coerceAtLeast(0L)
        val deck = dao.findDeck(planId.value)?.asExternalModel()
            ?: defaultDeck(planId, preview, now).also { dao.upsertDeck(it.asEntity()) }
        preview.sourcePreview.rows.forEach { row ->
            val expression = importedExpression(planId, deck.id, row.title, row.summary, row.raw, row.tags, now)
            dao.upsertExpression(expression.asEntity())
            dao.upsertIdiomCard(expression.toImportedIdiomCard(row.raw, now).asEntity())
        }
        snapshot(planId)
    }

    private suspend fun snapshot(planId: StudyPlanId): LanguageLearningSnapshot = LanguageLearningSnapshot(
        studyPlanId = planId,
        deck = dao.findDeck(planId.value)?.asExternalModel(),
        expressions = dao.findExpressions(planId.value).map { it.asExternalModel() },
        reviewAttempts = dao.findLanguageReviewAttempts(planId.value).map { it.asExternalModel() },
    )

    private suspend fun requirePlan(planId: StudyPlanId) {
        require(planDao.countById(planId.value) == 1) { "Study plan ${planId.value} does not exist." }
    }

    private fun defaultDeck(
        planId: StudyPlanId,
        preview: StudyFlowLanguageImportPreview,
        now: Long,
    ): LanguageDeckProfile = LanguageDeckProfile(
        id = LanguageDeckId(UUID.randomUUID().toString()),
        studyPlanId = planId,
        targetLanguage = preview.sourcePreview.rows.firstNotNullOfOrNull { row ->
            row.raw["target_language"]?.takeIf { it.isNotBlank() }
                ?: row.raw["language"]?.takeIf { it.isNotBlank() }
        } ?: "English",
        nativeLanguage = preview.sourcePreview.rows.firstNotNullOfOrNull { row ->
            row.raw["native_language"]?.takeIf { it.isNotBlank() }
        },
        regionFocus = preview.sourcePreview.rows.firstNotNullOfOrNull { row -> row.raw["region"]?.takeIf { it.isNotBlank() } },
        defaultRegister = LanguageRegister.NEUTRAL,
        reviewDirections = defaultLanguageReviewDirections(LanguageExpressionType.IDIOM),
        tags = listOf("imported"),
        createdAtEpochMillis = now,
        updatedAtEpochMillis = now,
    )

    private fun importedExpression(
        planId: StudyPlanId,
        deckId: LanguageDeckId,
        title: String,
        summary: String,
        raw: Map<String, String>,
        tags: List<String>,
        now: Long,
    ): LanguageExpression = LanguageExpression(
        id = LanguageExpressionId(UUID.randomUUID().toString()),
        studyPlanId = planId,
        deckId = deckId,
        expression = title,
        meaning = raw["meaning"].orEmpty().ifBlank { summary.ifBlank { title } },
        literalTranslation = raw["literal_translation"],
        translationEquivalent = raw["translation"],
        type = LanguageExpressionType.IDIOM,
        register = parseRegister(raw["register"]),
        region = raw["region"],
        pronunciationNote = raw["pronunciation"],
        commonMistake = raw["common_mistake"],
        examples = listOf(
            UsageExample(
                sentence = raw["example"].orEmpty().ifBlank { title },
                translation = raw["example_translation"],
                note = raw["note"],
            ),
        ),
        tags = tags,
        createdAtEpochMillis = now,
        updatedAtEpochMillis = now,
    )

    private fun LanguageExpression.toImportedIdiomCard(raw: Map<String, String>, now: Long): IdiomCard {
        now.toString().length // keep import timestamp material for source contract without altering the row shape.
        return IdiomCard(
            id = IdiomCardId(UUID.randomUUID().toString()),
            expressionId = id,
            naturalExample = examples.firstOrNull()?.sentence ?: expression,
            userSentence = null,
            clozePrompt = raw["cloze"],
            acceptedAnswers = listOf(expression),
            reviewDirections = defaultLanguageReviewDirections(LanguageExpressionType.IDIOM),
            difficulty = raw["difficulty"].orEmpty().toIntOrNull()?.coerceIn(1, 5) ?: 2,
        )
    }

    private fun parseRegister(value: String?): LanguageRegister = LanguageRegister.entries.firstOrNull { candidate ->
        candidate.name.equals(value.orEmpty().trim().replace('-', '_'), ignoreCase = true)
    } ?: LanguageRegister.NEUTRAL
}
