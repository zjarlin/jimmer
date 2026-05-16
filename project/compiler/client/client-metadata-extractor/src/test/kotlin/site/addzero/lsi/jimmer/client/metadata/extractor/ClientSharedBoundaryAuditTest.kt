package site.addzero.lsi.jimmer.client.metadata.extractor

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

class ClientSharedBoundaryAuditTest {

    private val platformNamedConverterRegex = Regex("\\bto(Java|Kotlin)[A-Z][A-Za-z0-9_]*\\(")

    @Test
    fun `client shared modules stay free of frontend and poet platform leaks`() {
        for (file in clientSharedSources()) {
            val source = Files.readString(file)
            assertFalse(source.contains("com.google.devtools.ksp"), "${file.name} must not import KSP")
            assertFalse(source.contains("javax.annotation.processing"), "${file.name} must not import APT processing types")
            assertFalse(source.contains("javax.lang.model"), "${file.name} must not import lang model types")
            assertFalse(source.contains("com.squareup.kotlinpoet"), "${file.name} must not import KotlinPoet")
            assertFalse(source.contains("com.squareup.javapoet"), "${file.name} must not import JavaPoet")
            assertFalse(source.contains("site.addzero.lsi.ksp."), "${file.name} must not import lsi-ksp adapter types")
            assertFalse(source.contains("site.addzero.lsi.apt."), "${file.name} must not import lsi-apt adapter types")
            assertFalse(source.contains("toKotlinPoet("), "${file.name} must not call toKotlinPoet")
            assertFalse(source.contains("toJavaPoet("), "${file.name} must not call toJavaPoet")
            assertFalse(platformNamedConverterRegex.containsMatchIn(source), "${file.name} must not expose platform-named converters")
            assertFalse(source.contains("renderKotlinSource("), "${file.name} must not render Kotlin source directly")
            assertFalse(source.contains("renderJavaSource("), "${file.name} must not render Java source directly")
            assertFalse(source.contains("createSourceFile("), "${file.name} must not write source files directly")
            assertFalse(source.contains("createResourceFile("), "${file.name} must not write resource files directly")
        }
    }

    private fun clientSharedSources(): List<Path> {
        val repoRoot = CompilerAuditTestSupport.repoRoot
        val roots = listOf(
            repoRoot.resolve("project/compiler/client/client-metadata-extractor/src/main/kotlin/site/addzero/lsi/jimmer/client"),
            repoRoot.resolve("project/compiler/client/client-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/client"),
            repoRoot.resolve("project/compiler/client/client-metadata-model/src/main/kotlin/site/addzero/lsi/jimmer/client"),
        )
        return CompilerAuditTestSupport.collectSourceFiles(roots) { it.toString().endsWith(".kt") }
    }
}
