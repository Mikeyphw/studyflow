package com.mikeyphw.studyflow.core.data

import android.content.Context
import androidx.room.withTransaction
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.database.StudyPlanGraphDuplicator
import com.mikeyphw.studyflow.core.database.entity.DatabaseMetadataEntity
import com.mikeyphw.studyflow.core.database.mapper.asEntity
import com.mikeyphw.studyflow.core.database.mapper.asExternalModel
import com.mikeyphw.studyflow.core.domain.StudyPlanRepository
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.StudyPlanSettings
import com.mikeyphw.studyflow.core.model.StudyPlanStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomStudyPlanRepository private constructor(
    private val database: StudyFlowDatabase,
    private val clock: () -> Long,
) : StudyPlanRepository {
    private val studyPlanDao = database.studyPlanDao()
    private val studySupportDao = database.studySupportDao()

    override val plans: Flow<List<StudyPlan>> =
        studyPlanDao.observeAll().map { entities -> entities.map { it.asExternalModel() } }

    override fun observePlan(planId: StudyPlanId): Flow<StudyPlan?> =
        studyPlanDao.observeById(planId.value).map { entity -> entity?.asExternalModel() }

    override fun observePlanSettings(planId: StudyPlanId): Flow<StudyPlanSettings> =
        studySupportDao.observePlanSettings(planId.value).map { entity ->
            entity?.asExternalModel() ?: StudyPlanSettings.defaults(planId)
        }

    override suspend fun create(
        plan: StudyPlan,
        settings: StudyPlanSettings,
    ) {
        require(settings.studyPlanId == plan.id) { "Plan settings must belong to the created Study Plan." }
        database.withTransaction {
            require(studyPlanDao.countById(plan.id.value) == 0) {
                "A Study Plan with id ${plan.id.value} already exists."
            }
            studyPlanDao.insert(plan.asEntity(nowEpochMillis = clock()))
            studySupportDao.upsertPlanSettings(settings.asEntity())
        }
    }

    override suspend fun replace(plan: StudyPlan) {
        database.withTransaction {
            val existing = requireNotNull(studyPlanDao.findById(plan.id.value)) {
                "Study Plan ${plan.id.value} does not exist."
            }
            val now = clock()
            studyPlanDao.upsert(
                plan.copy(
                    createdAtEpochMillis = existing.createdAtEpochMillis,
                    updatedAtEpochMillis = now,
                ).asEntity(nowEpochMillis = now),
            )
        }
    }

    override suspend fun duplicate(
        sourcePlanId: StudyPlanId,
        newPlanId: StudyPlanId,
        newName: String,
    ): StudyPlan = database.withTransaction {
        val source = requireNotNull(studyPlanDao.findById(sourcePlanId.value)) {
            "Study Plan ${sourcePlanId.value} does not exist."
        }.asExternalModel()
        require(source.status != StudyPlanStatus.TRASHED) {
            "Restore a trashed Study Plan before duplicating it."
        }
        require(studyPlanDao.countById(newPlanId.value) == 0) {
            "A Study Plan with id ${newPlanId.value} already exists."
        }

        val now = clock()
        val duplicate = source.copy(
            id = newPlanId,
            name = newName,
            status = StudyPlanStatus.DRAFT,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            trashedAtEpochMillis = null,
        )
        studyPlanDao.insert(duplicate.asEntity(nowEpochMillis = now))
        StudyPlanGraphDuplicator(database.openHelper.writableDatabase).duplicate(
            sourcePlanId = sourcePlanId.value,
            newPlanId = newPlanId.value,
        )
        if (studySupportDao.findPlanSettings(newPlanId.value) == null) {
            studySupportDao.upsertPlanSettings(StudyPlanSettings.defaults(newPlanId).asEntity())
        }
        duplicate
    }

    override suspend fun updateStatus(
        planId: StudyPlanId,
        status: StudyPlanStatus,
    ) {
        require(status != StudyPlanStatus.TRASHED) { "Use moveToTrash for trashed Study Plans." }
        database.withTransaction {
            val existing = requireNotNull(studyPlanDao.findById(planId.value)) {
                "Study Plan ${planId.value} does not exist."
            }.asExternalModel()
            require(existing.status != StudyPlanStatus.TRASHED) {
                "Restore a trashed Study Plan before changing its status."
            }
            val now = clock()
            studyPlanDao.upsert(
                existing.copy(
                    status = status,
                    updatedAtEpochMillis = now,
                    trashedAtEpochMillis = null,
                ).asEntity(now),
            )
        }
    }

    override suspend fun moveToTrash(planId: StudyPlanId) {
        database.withTransaction {
            val existing = requireNotNull(studyPlanDao.findById(planId.value)) {
                "Study Plan ${planId.value} does not exist."
            }.asExternalModel()
            val now = clock()
            studyPlanDao.upsert(
                existing.copy(
                    status = StudyPlanStatus.TRASHED,
                    updatedAtEpochMillis = now,
                    trashedAtEpochMillis = now,
                ).asEntity(now),
            )
        }
    }

    override suspend fun restoreFromTrash(planId: StudyPlanId) {
        database.withTransaction {
            val existing = requireNotNull(studyPlanDao.findById(planId.value)) {
                "Study Plan ${planId.value} does not exist."
            }.asExternalModel()
            require(existing.status == StudyPlanStatus.TRASHED) { "Study Plan is not in trash." }
            val now = clock()
            studyPlanDao.upsert(
                existing.copy(
                    status = StudyPlanStatus.PAUSED,
                    updatedAtEpochMillis = now,
                    trashedAtEpochMillis = null,
                ).asEntity(now),
            )
        }
    }

    override suspend fun updatePlanSettings(settings: StudyPlanSettings) {
        database.withTransaction {
            require(studyPlanDao.countById(settings.studyPlanId.value) == 1) {
                "Study Plan ${settings.studyPlanId.value} does not exist."
            }
            studySupportDao.upsertPlanSettings(settings.asEntity())
        }
    }

    override suspend fun deletePermanently(planId: StudyPlanId) {
        database.withTransaction {
            studyPlanDao.deleteById(planId.value)
        }
    }

    override suspend fun clear() {
        database.withTransaction {
            studyPlanDao.deleteAll()
        }
    }

    override suspend fun restoreFoundationSamples() {
        database.withTransaction {
            studyPlanDao.deleteAll()
            val now = clock()
            val samples = FoundationStudyPlans.create()
            studyPlanDao.insertAll(samples.map { plan -> plan.asEntity(nowEpochMillis = now) })
            samples.forEach { plan ->
                studySupportDao.upsertPlanSettings(StudyPlanSettings.defaults(plan.id).asEntity())
            }
        }
    }

    suspend fun seedFoundationSamplesOnce() {
        database.withTransaction {
            val metadataDao = database.databaseMetadataDao()
            if (metadataDao.findValue(FOUNDATION_SEED_KEY) == null) {
                if (studyPlanDao.countAll() == 0) {
                    val now = clock()
                    val samples = FoundationStudyPlans.create()
                    studyPlanDao.insertAll(samples.map { plan -> plan.asEntity(nowEpochMillis = now) })
                    samples.forEach { plan ->
                        studySupportDao.upsertPlanSettings(StudyPlanSettings.defaults(plan.id).asEntity())
                    }
                }
                metadataDao.upsert(DatabaseMetadataEntity(FOUNDATION_SEED_KEY, "1"))
            }
        }
    }

    companion object {
        private const val FOUNDATION_SEED_KEY = "foundation_samples_seeded"

        fun create(
            context: Context,
            clock: () -> Long = System::currentTimeMillis,
        ): RoomStudyPlanRepository = RoomStudyPlanRepository(
            database = StudyFlowDatabase.create(context),
            clock = clock,
        )
    }
}
