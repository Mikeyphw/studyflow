package com.mikeyphw.studyflow.core.data

import android.content.Context
import androidx.room.withTransaction
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.database.entity.DatabaseMetadataEntity
import com.mikeyphw.studyflow.core.database.mapper.asEntity
import com.mikeyphw.studyflow.core.database.mapper.asExternalModel
import com.mikeyphw.studyflow.core.domain.StudyPlanRepository
import com.mikeyphw.studyflow.core.model.StudyPlan
import com.mikeyphw.studyflow.core.model.StudyPlanId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomStudyPlanRepository private constructor(
    private val database: StudyFlowDatabase,
    private val clock: () -> Long,
) : StudyPlanRepository {
    private val studyPlanDao = database.studyPlanDao()

    override val plans: Flow<List<StudyPlan>> =
        studyPlanDao.observeAll().map { entities -> entities.map { it.asExternalModel() } }

    override fun observePlan(planId: StudyPlanId): Flow<StudyPlan?> =
        studyPlanDao.observeById(planId.value).map { entity -> entity?.asExternalModel() }

    override suspend fun create(plan: StudyPlan) {
        database.withTransaction {
            require(studyPlanDao.countById(plan.id.value) == 0) {
                "A Study Plan with id ${plan.id.value} already exists."
            }
            studyPlanDao.insert(plan.asEntity(nowEpochMillis = clock()))
        }
    }

    override suspend fun replace(plan: StudyPlan) {
        database.withTransaction {
            require(studyPlanDao.countById(plan.id.value) == 1) {
                "Study Plan ${plan.id.value} does not exist."
            }
            val now = clock()
            studyPlanDao.upsert(
                plan.copy(updatedAtEpochMillis = now).asEntity(nowEpochMillis = now),
            )
        }
    }

    override suspend fun delete(planId: StudyPlanId) {
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
            studyPlanDao.insertAll(
                FoundationStudyPlans.create().map { plan -> plan.asEntity(nowEpochMillis = now) },
            )
        }
    }

    suspend fun seedFoundationSamplesOnce() {
        database.withTransaction {
            val metadataDao = database.databaseMetadataDao()
            if (metadataDao.findValue(FOUNDATION_SEED_KEY) == null) {
                if (studyPlanDao.countAll() == 0) {
                    val now = clock()
                    studyPlanDao.insertAll(
                        FoundationStudyPlans.create().map { plan -> plan.asEntity(nowEpochMillis = now) },
                    )
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
