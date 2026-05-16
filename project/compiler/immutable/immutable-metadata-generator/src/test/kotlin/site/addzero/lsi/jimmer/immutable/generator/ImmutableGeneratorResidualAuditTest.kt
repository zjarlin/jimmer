package site.addzero.lsi.jimmer.immutable.generator

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import site.addzero.lsi.jimmer.immutable.ImmutableTestSupport
import kotlin.io.path.readLines

class ImmutableGeneratorResidualAuditTest {

    @Test
    fun `raw lsi code usage is limited to approved exceptions`() {
        val rawCallAllowedFiles = setOf(
            "LsiGeneratorPoetSupport.kt",
        )
        val codeExpressionAllowedFiles = setOf(
            "LsiGeneratorPoetSupport.kt",
        )

        val unexpectedRawCalls = mutableListOf<String>()
        val unexpectedCodeExpressions = mutableListOf<String>()

        for (file in generatorFiles()) {
            file.readLines().forEachIndexed { index, line ->
                val fileName = file.fileName.toString()
                if ((line.contains("rawStatement(") || line.contains("rawExpression(")) &&
                    fileName !in rawCallAllowedFiles
                ) {
                    unexpectedRawCalls += "${file.fileName}:${index + 1}: ${line.trim()}"
                }
                if (line.contains("LsiCodeExpression(") && fileName !in codeExpressionAllowedFiles) {
                    unexpectedCodeExpressions += "${file.fileName}:${index + 1}: ${line.trim()}"
                }
            }
        }

        assertTrue(
            unexpectedRawCalls.isEmpty(),
            "Unexpected raw LSI calls:\n${unexpectedRawCalls.joinToString("\n")}",
        )
        assertTrue(
            unexpectedCodeExpressions.isEmpty(),
            "Unexpected LsiCodeExpression usage:\n${unexpectedCodeExpressions.joinToString("\n")}",
        )
    }

    @Test
    fun `kotlin only lambda types stay inside approved immutable generators`() {
        val allowedFiles = setOf(
            "DraftBlockMetadataExt.kt",
            "ImmutableCallbackMetadataExt.kt",
            "DraftGenerator.kt",
            "FetcherGenerator.kt",
            "FetcherDslGenerator.kt",
            "PropsGenerator.kt",
        )

        val unexpectedLambdaUsages = mutableListOf<String>()

        for (file in generatorFiles()) {
            file.readLines().forEachIndexed { index, line ->
                if ((line.contains("LsiLambdaTypeName") || line.contains("toLsiLambdaTypeName(")) &&
                    file.fileName.toString() !in allowedFiles
                ) {
                    unexpectedLambdaUsages += "${file.fileName}:${index + 1}: ${line.trim()}"
                }
            }
        }

        assertTrue(
            unexpectedLambdaUsages.isEmpty(),
            "Unexpected Kotlin-only lambda leakage:\n${unexpectedLambdaUsages.joinToString("\n")}",
        )
    }

    @Test
    fun `shared immutable generator naming stays lsi only`() {
        val unexpectedPlatformNamedConverters = mutableListOf<String>()

        for (file in generatorFiles()) {
            file.readLines().forEachIndexed { index, line ->
                if (Regex("\\bto(Java|Kotlin)[A-Z][A-Za-z0-9_]*\\(").containsMatchIn(line)) {
                    unexpectedPlatformNamedConverters += "${file.fileName}:${index + 1}: ${line.trim()}"
                }
            }
        }

        assertTrue(
            unexpectedPlatformNamedConverters.isEmpty(),
            "Unexpected platform-named shared converters:\n${unexpectedPlatformNamedConverters.joinToString("\n")}",
        )
    }

    private fun generatorFiles() = ImmutableTestSupport.generatorFiles()
}
