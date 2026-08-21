package com.mikeyphw.studyflow.core.designsystem

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFlowCodeBlockContractTest {
    @Test
    fun `code block exposes syntax highlighting and programming-language support`() {
        val source = String(
            Files.readAllBytes(
                Paths.get(
                    "src/main/java/com/mikeyphw/studyflow/core/designsystem/StudyFlowCodeBlock.kt",
                ),
            ),
            Charsets.UTF_8,
        )

        assertTrue(source.contains("fun StudyFlowCodeBlock("))
        assertTrue(source.contains("object StudyFlowSyntaxHighlighter"))
        assertTrue(source.contains("ProgrammingLanguage.KOTLIN"))
        assertTrue(source.contains("ProgrammingLanguage.PYTHON"))
        assertTrue(source.contains("ProgrammingLanguage.JAVASCRIPT"))
        assertTrue(source.contains("ProgrammingLanguage.SQL"))
        assertTrue(source.contains("LocalClipboard"))
        assertTrue(source.contains("setClipEntry"))
        assertTrue(source.contains("SelectionContainer"))
        assertTrue(source.contains("horizontalScroll"))
        assertTrue(source.contains("lineNumberColumn"))
    }
}
