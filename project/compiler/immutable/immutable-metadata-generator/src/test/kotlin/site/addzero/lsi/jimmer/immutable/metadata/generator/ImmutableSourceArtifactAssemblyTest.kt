package site.addzero.lsi.jimmer.immutable.metadata.generator

import site.addzero.lsi.jimmer.immutable.generator.ImmutableGenerationMode

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import site.addzero.lsi.codegen.GeneratorException
import site.addzero.lsi.poet.LsiCallableSpec
import site.addzero.lsi.poet.LsiCallableSpecKind
import site.addzero.lsi.poet.LsiFileSpec

class ImmutableSourceArtifactAssemblyTest {

    @Test
    fun `core artifact must stay java shared`() {
        val blockedCoreFileSpec = LsiFileSpec(
            packageName = "test.model",
            name = "BlockedCore",
            topLevelCallables = listOf(
                LsiCallableSpec(
                    kind = LsiCallableSpecKind.FUNCTION,
                    name = "blocked",
                )
            ),
        )

        val ex = assertThrows<GeneratorException> {
            immutableSourceFileSpecs(
                coreFileSpec = blockedCoreFileSpec,
                generationMode = ImmutableGenerationMode.JAVA_SHARED,
            )
        }

        assertTrue(ex.message!!.contains("BlockedCore"), ex.message)
        assertTrue(ex.message!!.contains("JAVA_SHARED"), ex.message)
    }

    @Test
    fun `dsl sidecar must stay kotlin sidecar`() {
        val ex = assertThrows<GeneratorException> {
            immutableSourceFileSpecs(
                coreFileSpec = LsiFileSpec(
                    packageName = "test.model",
                    name = "BookDraft",
                ),
                generationMode = ImmutableGenerationMode.KOTLIN_FULL,
            ) {
                LsiFileSpec(
                    packageName = "test.model",
                    name = "BookDraftDsl",
                )
            }
        }

        assertTrue(ex.message!!.contains("BookDraftDsl"), ex.message)
        assertTrue(ex.message!!.contains("KOTLIN_SIDECAR"), ex.message)
    }

    @Test
    fun `dsl sidecar is emitted only when enabled`() {
        val fileSpecs = immutableSourceFileSpecs(
            coreFileSpec = LsiFileSpec(
                packageName = "test.model",
                name = "BookDraft",
            ),
            generationMode = ImmutableGenerationMode.JAVA_SHARED,
        ) {
            LsiFileSpec(
                packageName = "test.model",
                name = "BookDraftDsl",
                topLevelCallables = listOf(
                    LsiCallableSpec(
                        kind = LsiCallableSpecKind.FUNCTION,
                        name = "store",
                    )
                ),
            )
        }

        assertEquals(listOf("test.model.BookDraft"), fileSpecs.map { it.qualifiedName })
    }
}
