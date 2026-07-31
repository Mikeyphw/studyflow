package com.mikeyphw.studyflow.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.mikeyphw.studyflow.core.database.dao.DatabaseMetadataDao
import com.mikeyphw.studyflow.core.database.dao.PlanOwnershipDao
import com.mikeyphw.studyflow.core.database.dao.StudyLibraryDao
import com.mikeyphw.studyflow.core.database.dao.StudyPlanDao
import com.mikeyphw.studyflow.core.database.dao.StudyPlanningDao
import com.mikeyphw.studyflow.core.database.dao.StudyPracticeDao
import com.mikeyphw.studyflow.core.database.dao.StudyRetentionDao
import com.mikeyphw.studyflow.core.database.dao.StudyStructureDao
import com.mikeyphw.studyflow.core.database.dao.StudySupportDao
import com.mikeyphw.studyflow.core.database.entity.AnnexEntity
import com.mikeyphw.studyflow.core.database.entity.DatabaseMetadataEntity
import com.mikeyphw.studyflow.core.database.entity.ErrorEntryEntity
import com.mikeyphw.studyflow.core.database.entity.ExamStageEntity
import com.mikeyphw.studyflow.core.database.entity.FlashcardEntity
import com.mikeyphw.studyflow.core.database.entity.MockExamEntity
import com.mikeyphw.studyflow.core.database.entity.PlanSnapshotEntity
import com.mikeyphw.studyflow.core.database.entity.PlannedActivityEntity
import com.mikeyphw.studyflow.core.database.entity.QuestionEntity
import com.mikeyphw.studyflow.core.database.entity.ReviewItemEntity
import com.mikeyphw.studyflow.core.database.entity.ScheduleRuleEntity
import com.mikeyphw.studyflow.core.database.entity.StudyGoalEntity
import com.mikeyphw.studyflow.core.database.entity.StudyMaterialEntity
import com.mikeyphw.studyflow.core.database.entity.StudyNoteEntity
import com.mikeyphw.studyflow.core.database.entity.StudyPlanEntity
import com.mikeyphw.studyflow.core.database.entity.StudyPlanSettingsEntity
import com.mikeyphw.studyflow.core.database.entity.StudySessionEntity
import com.mikeyphw.studyflow.core.database.entity.StudyStrategyEntity
import com.mikeyphw.studyflow.core.database.entity.StudyTargetEntity
import com.mikeyphw.studyflow.core.database.entity.StudyTestEntity
import com.mikeyphw.studyflow.core.database.entity.SubjectEntity
import com.mikeyphw.studyflow.core.database.entity.TopicEntity
import com.mikeyphw.studyflow.core.database.entity.VideoResourceEntity

@Database(
    entities = [
        StudyPlanEntity::class,
        StudyTargetEntity::class,
        ExamStageEntity::class,
        SubjectEntity::class,
        TopicEntity::class,
        StudyStrategyEntity::class,
        ScheduleRuleEntity::class,
        PlannedActivityEntity::class,
        StudySessionEntity::class,
        StudyGoalEntity::class,
        StudyMaterialEntity::class,
        VideoResourceEntity::class,
        AnnexEntity::class,
        QuestionEntity::class,
        StudyTestEntity::class,
        MockExamEntity::class,
        ReviewItemEntity::class,
        FlashcardEntity::class,
        ErrorEntryEntity::class,
        StudyNoteEntity::class,
        PlanSnapshotEntity::class,
        StudyPlanSettingsEntity::class,
        DatabaseMetadataEntity::class,
    ],
    version = StudyFlowDatabase.SCHEMA_VERSION,
    exportSchema = true,
)
abstract class StudyFlowDatabase : RoomDatabase() {
    abstract fun studyPlanDao(): StudyPlanDao

    abstract fun studyStructureDao(): StudyStructureDao

    abstract fun studyPlanningDao(): StudyPlanningDao

    abstract fun studyLibraryDao(): StudyLibraryDao

    abstract fun studyPracticeDao(): StudyPracticeDao

    abstract fun studyRetentionDao(): StudyRetentionDao

    abstract fun studySupportDao(): StudySupportDao

    abstract fun planOwnershipDao(): PlanOwnershipDao

    abstract fun databaseMetadataDao(): DatabaseMetadataDao

    companion object {
        const val SCHEMA_VERSION = 1
        const val DATABASE_NAME = "studyflow.db"

        fun create(context: Context): StudyFlowDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                StudyFlowDatabase::class.java,
                DATABASE_NAME,
            ).build()
    }
}
