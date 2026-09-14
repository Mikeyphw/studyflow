package com.mikeyphw.studyflow.feature.subjects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ov15ImportHonestyContractsTest {
    @Test
    fun manualImportDraftIsBoundedBeforePreviewAndSaveableRecreationState() {
        val draft = "x".repeat(Ov15MaxManualImportChars + 10)
        val bounded = boundManualImportDraft(draft)

        assertFalse(bounded.accepted)
        assertTrue(bounded.truncated)
        assertEquals(Ov15MaxManualImportChars, bounded.text.length)
    }

    @Test
    fun normalManualImportDraftIsPreservedUnchanged() {
        val draft = "Constitutional Law\n  Fundamental rights"
        val bounded = boundManualImportDraft(draft)

        assertTrue(bounded.accepted)
        assertEquals(draft, bounded.text)
    }
}
