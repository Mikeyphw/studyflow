package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.LegislationProvisionKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PublicExamLegislationParserTest {
    @Test
    fun `structured parser keeps portuguese article paragraph and inciso boundaries`() {
        val preview = LegislationImportParser.preview(
            title = "Constituição Federal",
            sourceReference = null,
            rawText = """
                Art. 5º Todos são iguais perante a lei.
                § 1º As normas definidoras dos direitos têm aplicação imediata.
                I - homens e mulheres são iguais em direitos e obrigações;
            """.trimIndent(),
            format = LegislationImportFormat.STRUCTURED_TEXT,
        )
        assertEquals(3, preview.provisions.size)
        assertEquals(LegislationProvisionKind.ARTICLE, preview.provisions[0].kind)
        assertEquals("Art. 5º", preview.provisions[0].reference)
        assertEquals("Art. 5º", preview.provisions[1].parentReference)
        assertTrue("iguais" in preview.provisions[0].text)
    }

    @Test
    fun `csv import preserves utf8 and explicit kind`() {
        val preview = LegislationImportParser.preview(
            title = "Lei",
            sourceReference = "local",
            rawText = "reference,text,kind\\nArt. 1º,Administração pública,ARTICLE",
            format = LegislationImportFormat.CSV,
        )
        assertEquals("Administração pública", preview.provisions.single().text)
    }
    @Test
    fun `repeated paragraph and item references get stable hierarchical keys`() {
        val preview = LegislationImportParser.preview(
            title = "Lei",
            sourceReference = null,
            rawText = """
                Art. 1º Primeiro.
                § 1º Regra A.
                I - Item A.
                Art. 2º Segundo.
                § 1º Regra B.
                I - Item B.
            """.trimIndent(),
            format = LegislationImportFormat.STRUCTURED_TEXT,
        )
        val keys = preview.provisions.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(keys[1].startsWith("article/art-1o/paragraph/"))
        assertTrue(keys[4].startsWith("article/art-2o/paragraph/"))
    }

}
