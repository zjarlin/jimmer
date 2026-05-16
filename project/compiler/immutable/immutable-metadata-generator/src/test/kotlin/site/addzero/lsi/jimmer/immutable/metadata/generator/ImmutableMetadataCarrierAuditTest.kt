package site.addzero.lsi.jimmer.immutable.metadata.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import site.addzero.lsi.jimmer.immutable.ImmutableTestSupport
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.io.path.readText

class ImmutableMetadataCarrierAuditTest {

    @Test
    fun `immutable metadata model rejects frontend and poet renderer leakage`() {
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
            "LsiLambdaTypeName",
            "renderJavaSource",
            "renderKotlinSource",
            "toJavaPoet(",
            "toKotlinPoet(",
            "topLevelCallables",
            "topLevelProperties",
            "memberImports",
        )

        for (file in metadataModelFiles()) {
            val source = file.readText()
            for (snippet in forbiddenSnippets) {
                assertFalse(
                    source.contains(snippet),
                    "immutable metadata model must stay frontend-neutral and renderer-free: ${file.name} contains `$snippet`\n$source",
                )
            }
        }
    }

    @Test
    fun `immutable metadata model keeps only approved lsi carriers`() {
        val actualImportsByFile = metadataModelFiles().associate { file ->
            file.name to file.readLines()
                .map(String::trim)
                .filter { it.startsWith("import site.addzero.lsi.") }
                .toSet()
        }

        val expectedImportsByFile = mapOf(
            "ImmutableAssociatedIdMetadata.kt" to setOf(
                "import site.addzero.lsi.poet.LsiTypeName",
            ),
            "ImmutableBuilderMetadata.kt" to setOf(
                "import site.addzero.lsi.codegen.LsiClassName",
                "import site.addzero.lsi.poet.LsiAnnotationSpec",
                "import site.addzero.lsi.poet.LsiTypeName",
            ),
            "ImmutableCallbackMetadata.kt" to setOf(
                "import site.addzero.lsi.poet.LsiTypeName",
            ),
            "ImmutableCollectedSourceMetadata.kt" to emptySet(),
            "ImmutableFetcherMetadata.kt" to setOf(
                "import site.addzero.lsi.codegen.LsiClassName",
            ),
            "ImmutableImplementorMetadata.kt" to setOf(
                "import site.addzero.lsi.codegen.LsiClassName",
            ),
            "ImmutablePropsMetadata.kt" to setOf(
                "import site.addzero.lsi.codegen.LsiClassName",
            ),
            "ImmutableSourceMetadata.kt" to emptySet(),
            "ImmutableValidationMetadata.kt" to setOf(
                "import site.addzero.lsi.anno.LsiAnnotation",
                "import site.addzero.lsi.codegen.LsiClassName",
                "import site.addzero.lsi.field.LsiField",
                "import site.addzero.lsi.poet.LsiTypeName",
            ),
        )

        assertEquals(expectedImportsByFile, actualImportsByFile)
    }

    private fun metadataModelFiles() = ImmutableTestSupport.metadataModelFiles()
}
