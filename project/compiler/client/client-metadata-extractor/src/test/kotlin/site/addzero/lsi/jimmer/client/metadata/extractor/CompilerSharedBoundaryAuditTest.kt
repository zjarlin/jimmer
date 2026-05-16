package site.addzero.lsi.jimmer.client.metadata.extractor

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

class CompilerSharedBoundaryAuditTest {

    private val platformNamedConverterRegex = Regex("\\bto(Java|Kotlin)[A-Z][A-Za-z0-9_]*\\(")

    @Test
    fun `project compiler shared code stays free of platform symbol leaks`() {
        val sharedSources = CompilerAuditTestSupport.compilerSharedSources()
        assertTrue(
            sharedSources.any {
                it.toString().contains("/project/compiler/jimmer-ksp-ext/src/main/")
            },
            "shared compiler audit must include project/compiler/jimmer-ksp-ext",
        )
        for (file in sharedSources) {
            val source = Files.readString(file)
            assertFalse(source.contains("import com.google.devtools.ksp."), "${file.name} must not import KSP")
            assertFalse(source.contains("import javax.annotation.processing."), "${file.name} must not import APT processing types")
            assertFalse(source.contains("import javax.lang.model."), "${file.name} must not import lang model types")
            assertFalse(source.contains("import com.squareup.kotlinpoet"), "${file.name} must not import KotlinPoet")
            assertFalse(source.contains("import com.squareup.javapoet"), "${file.name} must not import JavaPoet")
            assertFalse(source.contains("import site.addzero.lsi.ksp."), "${file.name} must not import lsi-ksp adapter types")
            assertFalse(source.contains("import site.addzero.lsi.apt."), "${file.name} must not import lsi-apt adapter types")
            assertFalse(source.contains("toKotlinPoet("), "${file.name} must not call toKotlinPoet")
            assertFalse(source.contains("toJavaPoet("), "${file.name} must not call toJavaPoet")
            assertFalse(source.contains("renderKotlinSource("), "${file.name} must not render Kotlin source directly")
            assertFalse(source.contains("renderJavaSource("), "${file.name} must not render Java source directly")
            assertFalse(source.contains("createSourceFile("), "${file.name} must not write source files directly")
            assertFalse(source.contains("createResourceFile("), "${file.name} must not write resource files directly")
            assertFalse(platformNamedConverterRegex.containsMatchIn(source), "${file.name} must not expose platform-named converters")
        }
    }
}
