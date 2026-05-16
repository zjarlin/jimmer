package site.addzero.lsi.jimmer.dto

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.name

class DtoSharedBoundaryAuditTest {

    private val legacyKotlinPoetFiles = setOf(
        "DtoGenerator.kt",
        "LegacyKotlinPoetCompat.kt",
    )

    private val platformNamedConverterRegex = Regex("\\bto(Java|Kotlin)[A-Z][A-Za-z0-9_]*\\(")

    @Test
    fun `dto shared module stays free of frontend and javapoet platform leaks`() {
        for (file in DtoTestSupport.dtoSharedSources()) {
            val source = Files.readString(file)
            assertFalse(source.contains("com.google.devtools.ksp"), "${file.name} must not import KSP")
            assertFalse(source.contains("javax.annotation.processing"), "${file.name} must not import APT processing types")
            assertFalse(source.contains("javax.lang.model"), "${file.name} must not import lang model types")
            assertFalse(source.contains("com.squareup.javapoet"), "${file.name} must not import JavaPoet")
            assertFalse(source.contains("toJavaPoet("), "${file.name} must not call toJavaPoet")
            assertFalse(source.contains("createSourceFile("), "${file.name} must not write files directly")
            assertFalse(platformNamedConverterRegex.containsMatchIn(source), "${file.name} must not expose platform-named converters")
        }
    }

    @Test
    fun `dto kotlinpoet usage stays isolated to transitional compat files`() {
        for (file in DtoTestSupport.dtoSharedSources()) {
            val source = Files.readString(file)
            if (file.name in legacyKotlinPoetFiles) {
                continue
            }
            assertFalse(source.contains("com.squareup.kotlinpoet"), "${file.name} must not import KotlinPoet")
            assertFalse(source.contains("toKotlinPoet("), "${file.name} must not call toKotlinPoet")
        }
    }
}
