package com.mikeyphw.studyflow.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
