package site.addzero.lsi.jimmer.exportdoc

import kotlin.test.Test
import kotlin.test.assertEquals
import site.addzero.lsi.core.LsiSymbolId

class ExportDocSnapshotTest {

    @Test
    fun `规范快照转义字段并产生稳定指纹`() {
        val configurationId = LsiSymbolId.packageScope("demo")
        val typeId = LsiSymbolId.type("demo.Book")
        val schema = ExportDocSchema(
            effectiveConfigurationIds = listOf(configurationId),
            exportedTypeIds = listOf(typeId),
            entries = listOf(
                ExportDocEntry(
                    declarationId = typeId,
                    key = "demo.Book|name",
                    content = "first\\line\nsecond",
                ),
            ),
        )

        assertEquals(
            """
                configuration|${configurationId.value}
                type|${typeId.value}
                doc|${typeId.value}|demo.Book\|name|first\\line\nsecond
            """.trimIndent() + "\n",
            schema.normalizedSnapshot(),
        )
        assertEquals(64, schema.fingerprint().length)
        assertEquals(schema.fingerprint(), schema.copy().fingerprint())
    }
}
