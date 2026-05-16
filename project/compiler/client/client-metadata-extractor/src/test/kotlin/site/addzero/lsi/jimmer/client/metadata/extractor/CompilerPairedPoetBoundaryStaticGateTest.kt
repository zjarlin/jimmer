package site.addzero.lsi.jimmer.client.metadata.extractor

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

class CompilerPairedPoetBoundaryStaticGateTest {

    @Test
    fun `paired processor shells and bootstraps stay free of direct poet rendering`() {
        for (file in pairedBoundaryAndBootstrapSources()) {
            val source = Files.readString(file)
            assertFalse(source.contains("import com.squareup.kotlinpoet"), "${file.name} must not import KotlinPoet")
            assertFalse(source.contains("import com.squareup.javapoet"), "${file.name} must not import JavaPoet")
            assertFalse(source.contains("toKotlinPoet("), "${file.name} must not call toKotlinPoet")
            assertFalse(source.contains("toJavaPoet("), "${file.name} must not call toJavaPoet")
            assertFalse(source.contains("renderKotlinSource("), "${file.name} must not render Kotlin source directly")
            assertFalse(source.contains("renderJavaSource("), "${file.name} must not render Java source directly")
            assertFalse(source.contains("createNewFile("), "${file.name} must not write raw KSP files directly")
            assertFalse(source.contains("import javax.annotation.processing.Filer"), "${file.name} must not import raw annotation filer")
        }
    }

    private fun pairedBoundaryAndBootstrapSources(): List<Path> {
        val repoRoot = CompilerAuditTestSupport.repoRoot
        val roots = listOf(
            repoRoot.resolve("project/jimmer-ksp/src/main/kotlin/org/babyfish/jimmer/ksp"),
            repoRoot.resolve("project/compiler/client/jimmer-ksp-client/src/main/kotlin/org/babyfish/jimmer/ksp/client"),
            repoRoot.resolve("project/compiler/dto/jimmer-ksp-dto/src/main/kotlin/org/babyfish/jimmer/ksp/dto"),
            repoRoot.resolve("project/compiler/error/jimmer-ksp-error/src/main/kotlin/org/babyfish/jimmer/lsi/error"),
            repoRoot.resolve("project/compiler/immutable/jimmer-ksp-immutable/src/main/kotlin/org/babyfish/jimmer/ksp/immutable"),
            repoRoot.resolve("project/compiler/transactional/jimmer-ksp-transactional/src/main/kotlin/org/babyfish/jimmer/ksp/transactional"),
            repoRoot.resolve("project/compiler/tuple/jimmer-ksp-tuple/src/main/kotlin/org/babyfish/jimmer/ksp/tuple"),
            repoRoot.resolve("project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/JimmerProcessor.java"),
            repoRoot.resolve("project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/client"),
            repoRoot.resolve("project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/dto"),
            repoRoot.resolve("project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/error"),
            repoRoot.resolve("project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/immutable"),
            repoRoot.resolve("project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/transactional"),
            repoRoot.resolve("project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/tuple"),
        )

        return CompilerAuditTestSupport.collectSourceFiles(roots)
    }
}
