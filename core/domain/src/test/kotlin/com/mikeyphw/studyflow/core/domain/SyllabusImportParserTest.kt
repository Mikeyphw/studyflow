package com.mikeyphw.studyflow.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SyllabusImportParserTest {
    @Test
    fun structuredTextBuildsNestedTopics() {
        val result = SyllabusImportParser.parseStructuredText(
            """
            Constitutional Law
              Fundamental rights
                Individual rights
              Public administration
            Portuguese
              Syntax
            """.trimIndent(),
        )
        assertEquals(2, result.subjects.size)
        val topics = result.subjects.first().topics
        assertEquals(3, topics.size)
        assertNull(topics.first().parentKey)
        assertEquals(topics.first().key, topics[1].parentKey)
    }

    @Test
    fun csvSupportsQuotedValuesAndParentReferences() {
        val result = SyllabusImportParser.parseCsv(
            "subject,topic,parent,priority,difficulty,minutes\n" +
                "Law,Principles,,5,3,30\n" +
                "Law,\"Rights, duties\",Principles,4,4,60",
        )
        val topics = result.subjects.single().topics
        assertEquals("Rights, duties", topics[1].name)
        assertEquals(topics[0].key, topics[1].parentKey)
    }
    @Test
    fun csvAcceptsBomAndResolvesParentDeclaredLater() {
        val result = SyllabusImportParser.parseCsv(
            "\uFEFFsubject,topic,parent,status\n" +
                "Law,Child,Parent,studying\n" +
                "Law,Parent,,not_started",
        )
        val topics = result.subjects.single().topics
        assertEquals(topics.single { it.name == "Parent" }.key, topics.single { it.name == "Child" }.parentKey)
    }

    @Test
    fun csvRejectsMalformedNumbersStatusAndAmbiguousParents() {
        assertThrows(IllegalStateException::class.java) {
            SyllabusImportParser.parseCsv("subject,topic,priority\nLaw,Topic,nope")
        }
        assertThrows(IllegalStateException::class.java) {
            SyllabusImportParser.parseCsv("subject,topic,status\nLaw,Topic,unknown")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SyllabusImportParser.parseCsv(
                "subject,topic,parent\nLaw,Parent,\nLaw,Parent,\nLaw,Child,Parent",
            )
        }
    }

    @Test
    fun structuredTextRejectsIndentationJump() {
        assertThrows(IllegalArgumentException::class.java) {
            SyllabusImportParser.parseStructuredText("Law\n    Grandchild")
        }
    }

}
