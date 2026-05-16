package site.addzero.lsi.jimmer.client.metadata.extractor

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

class CompilerSharedCoreStaticGateTest {

    private val sourceWriteCallPattern = Regex("""\.\s*createSourceFile\(""")
    private val resourceWriteCallPattern = Regex("""\.\s*createResourceFile\(""")
    private val rawPlatformWriteCallPattern = Regex("""\.\s*createNewFile\(""")

    @Test
    fun `shared compiler core stays lsi only`() {
        val sharedSources = CompilerAuditTestSupport.sharedCompilerCoreSources()
        assertTrue(
            sharedSources.any {
                it.toString().contains("/project/compiler/jimmer-ksp-ext/src/main/")
            },
            "shared compiler core audit must include project/compiler/jimmer-ksp-ext",
        )
        assertTrue(
            sharedSources.any {
                it.toString().contains("/lib/lsi/lsi-core/src/main/")
            },
            "shared compiler core audit must include lib/lsi/lsi-core",
        )
        assertTrue(
            sharedSources.any {
                it.toString().contains("/lib/lsi/lsi-jimmer/src/main/")
            },
            "shared compiler core audit must include lib/lsi/lsi-jimmer",
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
            assertFalse(source.contains("import org.babyfish.jimmer.ksp."), "${file.name} must not import KSP shell types")
            assertFalse(source.contains("import org.babyfish.jimmer.apt."), "${file.name} must not import APT shell types")
            assertFalse(source.contains("toKotlinPoet("), "${file.name} must not call toKotlinPoet")
            assertFalse(source.contains("toJavaPoet("), "${file.name} must not call toJavaPoet")
            assertFalse(source.contains("renderKotlinSource("), "${file.name} must not render Kotlin source directly")
            assertFalse(source.contains("renderJavaSource("), "${file.name} must not render Java source directly")
            assertFalse(sourceWriteCallPattern.containsMatchIn(source), "${file.name} must not write source files directly")
            assertFalse(resourceWriteCallPattern.containsMatchIn(source), "${file.name} must not write resource files directly")
            assertFalse(rawPlatformWriteCallPattern.containsMatchIn(source), "${file.name} must not write raw platform files directly")
        }
    }
}
