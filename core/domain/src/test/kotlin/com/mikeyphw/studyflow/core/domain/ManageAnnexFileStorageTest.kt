package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.Annex
import com.mikeyphw.studyflow.core.model.AnnexId
import com.mikeyphw.studyflow.core.model.AnnexStorageMode
import com.mikeyphw.studyflow.core.model.StudyMaterialId
import com.mikeyphw.studyflow.core.model.StudyPlanId
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.TopicId
import java.io.InputStream
import org.junit.Assert.assertThrows
import org.junit.Test

class ManageAnnexFileStorageTest {
    @Test
    fun rejectsBlankName() {
        val manager = ManageAnnexFileStorage(FakeRepository())
        assertThrows(IllegalArgumentException::class.java) {
            manager.importCopy(
                studyPlanId = StudyPlanId("plan"),
                displayName = " ",
                mimeType = "application/pdf",
                input = byteArrayOf(1).inputStream(),
            )
        }
    }

    private class FakeRepository : AnnexFileStorageRepository {
        override suspend fun importAnnexCopy(
            studyPlanId: StudyPlanId,
            subjectId: SubjectId?,
            topicId: TopicId?,
            materialId: StudyMaterialId?,
            displayName: String,
            mimeType: String,
            declaredByteSize: Long?,
            input: InputStream,
        ): Annex = Annex(
            id = AnnexId("annex"),
            studyPlanId = studyPlanId,
            subjectId = subjectId,
            topicId = topicId,
            materialId = materialId,
            displayName = displayName,
            mimeType = mimeType,
            byteSize = 1,
            checksumSha256 = null,
            storageMode = AnnexStorageMode.IMPORTED_COPY,
            storageReference = "study-plans/plan/annexes/annex/file.pdf",
            persistedPermission = false,
            createdAtEpochMillis = 1,
            updatedAtEpochMillis = 1,
        )

        override suspend fun deleteStoredAnnex(studyPlanId: StudyPlanId, annexId: AnnexId) = Unit

        override suspend fun cleanupOrphanedAnnexFiles(studyPlanId: StudyPlanId): AnnexFileCleanupResult =
            AnnexFileCleanupResult(0, 0)
    }
}
