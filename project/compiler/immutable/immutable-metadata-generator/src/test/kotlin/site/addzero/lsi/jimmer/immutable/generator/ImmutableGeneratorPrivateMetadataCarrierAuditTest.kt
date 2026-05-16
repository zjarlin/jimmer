package site.addzero.lsi.jimmer.immutable.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import site.addzero.lsi.jimmer.immutable.ImmutableTestSupport
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.io.path.readText

class ImmutableGeneratorPrivateMetadataCarrierAuditTest {

    @Test
    fun `immutable generator private metadata rejects renderer and frontend leakage`() {
        val forbiddenSnippets = listOf(
            "com.squareup.kotlinpoet",
            "com.squareup.javapoet",
            "com.google.devtools.ksp",
            "javax.lang.model",
            "LsiFileSpec",
            "LsiTypeSpec",
            "LsiPropertySpec",
            "LsiCallableSpec",
            "LsiCodeBlock",
            "LsiStatement",
            "LsiLambdaTypeName",
            "renderJavaSource",
            "renderKotlinSource",
            "toJavaPoet(",
            "toKotlinPoet(",
        )

        for (file in privateMetadataFiles()) {
            val source = file.readText()
            for (snippet in forbiddenSnippets) {
                assertFalse(
                    source.contains(snippet),
                    "immutable generator-private metadata must stay renderer-free and frontend-neutral: ${file.name} contains `$snippet`\n$source",
                )
            }
        }
    }

    @Test
    fun `immutable generator private metadata keeps only approved lsi carriers`() {
        val actualImportsByFile = privateMetadataFiles().associate { file ->
            file.name to file.readLines()
                .map(String::trim)
                .filter { it.startsWith("import site.addzero.lsi.") }
                .toSet()
        }

        val expectedImportsByFile = mapOf(
            "DraftImplAccessorMetadata.kt" to setOf(
                "import site.addzero.lsi.codegen.LsiClassName",
                "import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableAssociatedIdMetadata",
                "import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableCallbackMetadata",
                "import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableValidationPropMetadata",
                "import site.addzero.lsi.poet.LsiTypeName",
            ),
            "DraftMetadata.kt" to setOf(
                "import site.addzero.lsi.codegen.KOTLIN_UNIT_LSI_CLASS_NAME",
                "import site.addzero.lsi.codegen.LsiClassName",
                "import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableAssociatedIdMetadata",
                "import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableBuilderTypeMetadata",
                "import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableCallbackMetadata",
                "import site.addzero.lsi.poet.LsiTypeName",
            ),
            "ImplMetadata.kt" to setOf(
                "import site.addzero.lsi.codegen.LsiClassName",
                "import site.addzero.lsi.poet.LsiTypeName",
            ),
            "ProducerMetadata.kt" to setOf(
                "import site.addzero.lsi.codegen.LsiClassName",
                "import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableCallbackMetadata",
                "import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableImplementorTypeMetadata",
            ),
        )

        assertEquals(expectedImportsByFile, actualImportsByFile)
    }

    private fun privateMetadataFiles() =
        ImmutableTestSupport.generatorFiles().filter { it.name in PRIVATE_METADATA_FILE_NAMES }

    companion object {
        private val PRIVATE_METADATA_FILE_NAMES = setOf(
            "DraftImplAccessorMetadata.kt",
            "DraftMetadata.kt",
            "ImplMetadata.kt",
            "ProducerMetadata.kt",
        )
    }
}
