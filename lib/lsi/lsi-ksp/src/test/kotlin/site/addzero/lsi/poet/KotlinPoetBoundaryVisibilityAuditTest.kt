package site.addzero.lsi.poet

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class KotlinPoetBoundaryVisibilityAuditTest {

    @Test
    fun `kotlin poet reverse bridge stays adapter internal`() {
        val source = sourceOf("lib/lsi/lsi-ksp/src/main/kotlin/site/addzero/lsi/poet/KotlinPoetExt.kt")

        listOf(
            "internal fun LsiClassName.toKotlinPoet()",
            "internal fun LsiTypeName.toKotlinPoet()",
            "internal fun LsiType.toKotlinPoet(",
            "internal fun LsiAnnotationSpec.toKotlinPoet()",
            "internal fun LsiTypeSpec.toKotlinPoet()",
            "fun LsiFileSpec.renderKotlinSource(): String",
        ).forEach { snippet ->
            assertTrue(source.contains(snippet), "KotlinPoetExt 必须包含 `$snippet`\n$source")
        }
    }

    @Test
    fun `kotlin poet compat bridges stay removed from business surface`() {
        listOf(
            "lib/lsi/lsi-ksp/src/main/kotlin/site/addzero/lsi/anno/LsiAnnotationCompatExt.kt",
            "lib/lsi/lsi-ksp/src/main/kotlin/site/addzero/lsi/clazz/LsiClassCompatExt.kt",
            "lib/lsi/lsi-ksp/src/main/kotlin/site/addzero/lsi/codegen/LsiClassNameCompatExt.kt",
            "lib/lsi/lsi-ksp/src/main/kotlin/site/addzero/lsi/type/LsiTypeCompatExt.kt",
        ).forEach { relativePath ->
            assertTrue(
                !File(repoRoot(), relativePath).exists(),
                "$relativePath 已经不允许继续作为业务可见的 KotlinPoet 兼容桥接存在"
            )
        }
    }

    @Test
    fun `unused direct kotlin poet bridges stay removed`() {
        listOf(
            "lib/lsi/lsi-ksp/src/main/kotlin/site/addzero/lsi/codegen/TypeNameCompatExt.kt",
            "lib/lsi/lsi-ksp/src/main/kotlin/site/addzero/lsi/ksp/anno/KspAnnotationUseSiteTargetKotlinPoetExt.kt",
            "lib/lsi/lsi-ksp/src/main/kotlin/site/addzero/lsi/poet/LegacyKotlinPoetToLsiExt.kt",
        ).forEach { relativePath ->
            assertTrue(
                !File(repoRoot(), relativePath).exists(),
                "$relativePath 已经是无用的公开 KotlinPoet 桥接，不能再回到主干"
            )
        }
    }

    private fun sourceOf(relativePath: String): String =
        File(repoRoot(), relativePath).readText()

    private fun repoRoot(): File =
        generateSequence(File(".").absoluteFile.normalize()) { it.parentFile }
            .first { File(it, "settings.gradle.kts").exists() }
}
