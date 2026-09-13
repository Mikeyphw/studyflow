package com.mikeyphw.studyflow.core.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class AnnexOrphanCleanupAccountingTest {
    @Test
    fun `failed physical delete does not report reclaimed bytes`() {
        val file = File.createTempFile("studyflow-ov05", ".annex").apply { writeBytes(ByteArray(37)) }
        try {
            val result = deleteOrphanCandidate(file) { false }
            assertEquals(0, result.removedFiles)
            assertEquals(0L, result.removedBytes)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `successful physical delete reports exact reclaimed bytes`() {
        val file = File.createTempFile("studyflow-ov05", ".annex").apply { writeBytes(ByteArray(41)) }
        val result = deleteOrphanCandidate(file)
        assertEquals(1, result.removedFiles)
        assertEquals(41L, result.removedBytes)
    }
}
