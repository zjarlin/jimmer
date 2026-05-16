package site.addzero.lsi.poet

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class JavaPoetBoundaryVisibilityAuditTest {

    @Test
    fun `java poet reverse bridge stays adapter internal`() {
        val source = sourceOf("lib/lsi/lsi-apt/src/main/kotlin/site/addzero/lsi/poet/JavaPoetExt.kt")

        listOf(
            "internal fun LsiClassName.toJavaPoet()",
            "internal fun LsiTypeName.toJavaPoet()",
            "internal fun LsiAnnotationSpec.toJavaPoet()",
            "internal fun LsiTypeSpec.toJavaPoet()",
            "internal fun LsiCallableSpec.toJavaPoet(",
            "fun LsiFileSpec.renderJavaSource(): String",
        ).forEach { snippet ->
            assertTrue(source.contains(snippet), "JavaPoetExt 必须包含 `$snippet`\n$source")
        }
    }

    @Test
    fun `legacy java poet bulk reverse bridge stays removed`() {
        val legacyPath = "lib/lsi/lsi-apt/src/main/kotlin/site/addzero/lsi/poet/LegacyJavaPoetToLsiExt.kt"
        assertTrue(
            !File(repoRoot(), legacyPath).exists(),
            "$legacyPath 已经不允许继续作为 bulk JavaPoet -> LSI 迁移桥接存在"
        )
    }

    private fun sourceOf(relativePath: String): String =
        File(repoRoot(), relativePath).readText()

    private fun repoRoot(): File =
        generateSequence(File(".").absoluteFile.normalize()) { it.parentFile }
            .first { File(it, "settings.gradle.kts").exists() }
}
