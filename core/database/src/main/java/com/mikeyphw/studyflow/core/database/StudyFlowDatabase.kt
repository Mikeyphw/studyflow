package com.mikeyphw.studyflow.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.mikeyphw.studyflow.core.database.dao.DatabaseMetadataDao
import com.mikeyphw.studyflow.core.database.dao.PlanOwnershipDao
import com.mikeyphw.studyflow.core.database.dao.ProgrammingCourseDao
import com.mikeyphw.studyflow.core.database.dao.PublicExamDao
import com.mikeyphw.studyflow.core.database.dao.LanguageLearningDao
import com.mikeyphw.studyflow.core.database.dao.StudyDiscoveryDao
import com.mikeyphw.studyflow.core.database.dao.StudyAnalyticsDao
import com.mikeyphw.studyflow.core.database.dao.StudyLibraryDao
import com.mikeyphw.studyflow.core.database.dao.StudyPlanDao
import com.mikeyphw.studyflow.core.database.dao.StudyPlanningDao
import com.mikeyphw.studyflow.core.database.dao.StudyPracticeDao
import com.mikeyphw.studyflow.core.database.dao.StudyRetentionDao
import com.mikeyphw.studyflow.core.database.dao.StudyStructureDao
import com.mikeyphw.studyflow.core.database.dao.StudySupportDao
import com.mikeyphw.studyflow.core.database.entity.AnnexEntity
import com.mikeyphw.studyflow.core.database.entity.CodeSnippetEntity
import com.mikeyphw.studyflow.core.database.entity.CodingExerciseEntity
import com.mikeyphw.studyflow.core.database.entity.MiniProjectEntity
import com.mikeyphw.studyflow.core.database.entity.ProgrammingCourseProfileEntity
import com.mikeyphw.studyflow.core.database.entity.TermuxRuntimePreferenceEntity
import com.mikeyphw.studyflow.core.database.entity.LanguageDeckProfileEntity
import com.mikeyphw.studyflow.core.database.entity.LanguageExpressionEntity
import com.mikeyphw.studyflow.core.database.entity.LanguageReviewAttemptEntity
import com.mikeyphw.studyflow.core.database.entity.IdiomCardEntity
import com.mikeyphw.studyflow.core.database.entity.VocabularyCardEntity
import com.mikeyphw.studyflow.core.database.entity.TermuxCommandRequestEntity
import com.mikeyphw.studyflow.core.database.entity.TermuxCommandResultEntity
import com.mikeyphw.studyflow.core.database.entity.DatabaseMetadataEntity
import com.mikeyphw.studyflow.core.database.entity.ErrorEntryEntity
import com.mikeyphw.studyflow.core.database.entity.ExamNoticeRevisionEntity
import com.mikeyphw.studyflow.core.database.entity.ExamStageEntity
import com.mikeyphw.studyflow.core.database.entity.FlashcardEntity
import com.mikeyphw.studyflow.core.database.entity.MockExamEntity
import com.mikeyphw.studyflow.core.database.entity.PlanSnapshotEntity
import com.mikeyphw.studyflow.core.database.entity.PlannedActivityEntity
import com.mikeyphw.studyflow.core.database.entity.QuestionEntity
import com.mikeyphw.studyflow.core.database.entity.ReviewItemEntity
import com.mikeyphw.studyflow.core.database.entity.ScheduleRuleEntity
import com.mikeyphw.studyflow.core.database.entity.StudyGoalEntity
import com.mikeyphw.studyflow.core.database.entity.StudyCycleEntity
import com.mikeyphw.studyflow.core.database.entity.StudyCycleItemEntity
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
        ExamNoticeRevisionEntity::class,
        SubjectEntity::class,
        TopicEntity::class,
        StudyStrategyEntity::class,
        StudyCycleEntity::class,
        StudyCycleItemEntity::class,
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
        ProgrammingCourseProfileEntity::class,
        CodeSnippetEntity::class,
        CodingExerciseEntity::class,
        MiniProjectEntity::class,
        TermuxRuntimePreferenceEntity::class,
        TermuxCommandRequestEntity::class,
        TermuxCommandResultEntity::class,
        LanguageDeckProfileEntity::class,
        LanguageExpressionEntity::class,
        IdiomCardEntity::class,
        VocabularyCardEntity::class,
        LanguageReviewAttemptEntity::class,
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

    abstract fun studyDiscoveryDao(): StudyDiscoveryDao

    abstract fun studyAnalyticsDao(): StudyAnalyticsDao

    abstract fun studyPracticeDao(): StudyPracticeDao

    abstract fun studyRetentionDao(): StudyRetentionDao

    abstract fun studySupportDao(): StudySupportDao

    abstract fun planOwnershipDao(): PlanOwnershipDao

    abstract fun databaseMetadataDao(): DatabaseMetadataDao

    abstract fun programmingCourseDao(): ProgrammingCourseDao

    abstract fun languageLearningDao(): LanguageLearningDao

    abstract fun publicExamDao(): PublicExamDao

    companion object {
        const val SCHEMA_VERSION = 12
        const val DATABASE_NAME = "studyflow.db"

        fun create(context: Context): StudyFlowDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                StudyFlowDatabase::class.java,
                DATABASE_NAME,
            )
                .addMigrations(*StudyFlowMigrations.All)
                .addCallback(
                    object : RoomDatabase.Callback() {
                        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                            StudyFlowIntegrity.install(db)
                        }

                        override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                            StudyFlowIntegrity.install(db)
                        }
                    },
                )
                .build()
    }
}
