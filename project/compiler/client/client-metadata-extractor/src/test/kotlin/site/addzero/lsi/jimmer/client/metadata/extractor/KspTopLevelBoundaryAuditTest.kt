package site.addzero.lsi.jimmer.client.metadata.extractor

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

class KspTopLevelBoundaryAuditTest {

    @Test
    fun `top level ksp shell stays boundary only`() {
        for (file in kspTopLevelSources()) {
            val source = Files.readString(file)
            assertFalse(source.contains("toKotlinPoet("), "${file.name} must not call toKotlinPoet")
            assertFalse(source.contains("toJavaPoet("), "${file.name} must not call toJavaPoet")
            assertFalse(source.contains("renderKotlinSource("), "${file.name} must not render Kotlin source directly")
            assertFalse(source.contains("renderJavaSource("), "${file.name} must not render Java source directly")
            assertFalse(source.contains("createSourceFile("), "${file.name} must not write source files directly")
            assertFalse(source.contains("createNewFile("), "${file.name} must not write raw KSP files directly")
            assertFalse(source.contains("import com.squareup.kotlinpoet"), "${file.name} must not import KotlinPoet")
            assertFalse(source.contains("import com.squareup.javapoet"), "${file.name} must not import JavaPoet")
        }
    }

    private fun kspTopLevelSources(): List<Path> {
        val root = CompilerAuditTestSupport.repoRoot
            .resolve("project/jimmer-ksp/src/main/kotlin/org/babyfish/jimmer/ksp")
        return CompilerAuditTestSupport.collectSourceFiles(listOf(root))
    }
}
