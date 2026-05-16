package site.addzero.lsi.jimmer.client.metadata.extractor

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

class AptClientBoundaryAuditTest {

    @Test
    fun `apt client boundary stays on shared lsi pipeline`() {
        for (file in aptClientSources()) {
            val source = Files.readString(file)
            assertFalse(source.contains("import com.squareup.javapoet"), "${file.name} must not import JavaPoet")
            assertFalse(source.contains("import com.squareup.kotlinpoet"), "${file.name} must not import KotlinPoet")
            assertFalse(source.contains("import org.babyfish.jimmer.apt.immutable.meta."), "${file.name} must not import legacy apt immutable meta")
            assertFalse(source.contains("toJavaPoet("), "${file.name} must not call toJavaPoet")
            assertFalse(source.contains("toKotlinPoet("), "${file.name} must not call toKotlinPoet")
            assertFalse(source.contains("renderJavaSource("), "${file.name} must not render Java source directly")
            assertFalse(source.contains("renderKotlinSource("), "${file.name} must not render Kotlin source directly")
            assertFalse(source.contains("createSourceFile("), "${file.name} must not write source files directly")
        }
    }

    private fun aptClientSources(): List<Path> {
        val clientRoot = CompilerAuditTestSupport.repoRoot
            .resolve("project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/client")
        return CompilerAuditTestSupport.collectSourceFiles(listOf(clientRoot))
    }
}
