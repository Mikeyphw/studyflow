package com.mikeyphw.studyflow.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContentImportTemplatesTest {
    @Test
    fun `programming csv preview validates rows before import`() {
        val csv = """
            type,module,topic,title,prompt,difficulty,language,tags
            exercise,Kotlin,Null safety,Safe length,Return nullable string length,2,kotlin,kotlin|null-safety
        """.trimIndent()

        val preview = previewProgrammingCourseCsv(csv)

        assertTrue(preview.canImport)
        assertEquals(StudyFlowImportRowKind.PROGRAMMING_EXERCISE, preview.rows.single().kind)
        assertEquals("Safe length", preview.rows.single().title)
        assertEquals(listOf("kotlin", "null-safety"), preview.rows.single().tags)
    }

    @Test
    fun `language idiom csv preview reports missing required headers`() {
        val csv = """
            expression,example
            call it a day,After three hours let's call it a day.
        """.trimIndent()

        val preview = previewLanguageIdiomsCsv(csv)

        assertFalse(preview.canImport)
        assertTrue(preview.issues.any { it.message.contains("meaning") })
        assertTrue(preview.issues.any { it.message.contains("register") })
    }

    @Test
    fun `markdown previews support programming and idiom templates`() {
        val programming = """
            # Programming course import template
            ## Module: Kotlin basics
            ### Exercise: Safe length
            Prompt:
            Write the function.
        """.trimIndent()
        val idioms = """
            # Language idioms import template
            ## Idiom: call it a day
            Meaning: stop working for now
            Tags: work, daily speech
        """.trimIndent()

        assertTrue(previewProgrammingCourseMarkdown(programming).canImport)
        assertTrue(previewLanguageIdiomsMarkdown(idioms).canImport)
        assertEquals("call it a day", previewLanguageIdiomsMarkdown(idioms).rows.single().title)
    }
}
