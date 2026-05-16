package site.addzero.lsi.jimmer.ext

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name

class JimmerKspExtBoundaryAuditTest {

    @Test
    fun `jimmer ksp ext shared sources stay free of platform and reverse rendering leaks`() {
        for (file in sharedSources()) {
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
        }
    }

    private fun sharedSources(): List<Path> {
        val repoRoot = locateRepoRoot()
        val moduleRoot = repoRoot.resolve("project/compiler/jimmer-ksp-ext")
        return Files.walk(moduleRoot)
            .filter { Files.isRegularFile(it) }
            .filter { it.toString().endsWith(".kt") || it.toString().endsWith(".java") }
            .filter { isMainSource(it, moduleRoot) }
            .sorted()
            .toList()
    }

    private fun isMainSource(file: Path, moduleRoot: Path): Boolean {
        val relative = moduleRoot.relativize(file).invariantSeparatorsPathString
        if (!relative.contains("/src/main/")) {
            return false
        }
        if (relative.contains("/build/")) {
            return false
        }
        return true
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
