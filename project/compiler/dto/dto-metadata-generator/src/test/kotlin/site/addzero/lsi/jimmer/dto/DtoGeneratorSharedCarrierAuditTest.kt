package site.addzero.lsi.jimmer.dto

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class DtoGeneratorSharedCarrierAuditTest {

    @Test
    fun `dto generator reuses shared built in carrier helper`() {
        val source = Files.readString(
            locateRepoRoot().resolve(
                "project/compiler/dto/dto-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/dto/DtoGenerator.kt"
            )
        )

        assertTrue(
            source.contains("toBuiltInLsiClassNameOrNull"),
            "DtoGenerator 必须复用共享 built-in carrier helper\n$source",
        )
        assertTrue(
            source.contains("toBoxedPrimitiveLsiClassNameOrNull"),
            "DtoGenerator 注解 typeRef boxing 必须复用共享 primitive boxing helper\n$source",
        )
        assertFalse(
            source.contains("TypeRef.TN_MUTABLE_LIST -> LsiClassName.bestGuess(\"kotlin.collections.MutableList\")"),
            "DtoGenerator 不得保留本地 collection carrier truth table\n$source",
        )
        assertFalse(
            source.contains("TypeRef.TN_BOOLEAN -> LsiClassName.bestGuess(\"kotlin.Boolean\")"),
            "DtoGenerator 不得保留本地 primitive carrier truth table\n$source",
        )
        assertFalse(
            source.contains("java.lang.\${typeRef.typeName}"),
            "DtoGenerator 不得手写 primitive wrapper 字符串拼接\n$source",
        )
    }

    private fun locateRepoRoot(): Path {
        var current = Path.of("").toAbsolutePath()
        while (current.parent != null) {
            if (Files.exists(current.resolve("settings.gradle.kts"))) {
                return current
            }
            current = current.parent
        }
        error("Cannot locate repository root from ${Path.of("").toAbsolutePath()}")
    }
}
