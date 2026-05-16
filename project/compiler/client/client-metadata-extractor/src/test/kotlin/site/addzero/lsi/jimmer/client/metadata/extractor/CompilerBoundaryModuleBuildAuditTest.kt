package site.addzero.lsi.jimmer.client.metadata.extractor

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

class CompilerBoundaryModuleBuildAuditTest {

    private val forbiddenPoetDependencyPatterns = listOf(
        Regex("""(?m)^\s*(implementation|api|compileOnly|runtimeOnly)\(libs\.kotlinpoet\)"""),
        Regex("""(?m)^\s*(implementation|api|compileOnly|runtimeOnly)\(libs\.javapoet\)"""),
    )

    private val forbiddenCompilerShellAdapterPatterns = listOf(
        Regex("""(?m)^\s*(implementation|api|compileOnly|runtimeOnly)\(projects\.lib\.lsi\.lsiKsp\)"""),
        Regex("""(?m)^\s*(implementation|api|compileOnly|runtimeOnly)\(projects\.lib\.lsi\.lsiApt\)"""),
        Regex("""(?m)^\s*(implementation|api|compileOnly|runtimeOnly)\(project\(":lib:lsi:lsi-ksp"\)\)"""),
        Regex("""(?m)^\s*(implementation|api|compileOnly|runtimeOnly)\(project\(":lib:lsi:lsi-apt"\)\)"""),
    )

    @Test
    fun `compiler shell build scripts stay free of poet and direct adapter dependencies`() {
        for (buildScript in compilerShellBuildScripts()) {
            val source = Files.readString(buildScript)
            for (pattern in forbiddenPoetDependencyPatterns + forbiddenCompilerShellAdapterPatterns) {
                assertFalse(
                    pattern.containsMatchIn(source),
                    "${buildScript.name} must not declare `${pattern.pattern}`",
                )
            }
        }
    }

    @Test
    fun `top level bootstraps keep poet out of build scripts`() {
        for (buildScript in topLevelBootstrapBuildScripts()) {
            val source = Files.readString(buildScript)
            for (pattern in forbiddenPoetDependencyPatterns) {
                assertFalse(
                    pattern.containsMatchIn(source),
                    "${buildScript.name} must not declare `${pattern.pattern}`",
                )
            }
        }
    }

    private fun compilerShellBuildScripts(): List<Path> {
        val repoRoot = CompilerAuditTestSupport.repoRoot
        return listOf(
            "project/compiler/client/jimmer-ksp-client/build.gradle.kts",
            "project/compiler/dto/jimmer-ksp-dto/build.gradle.kts",
            "project/compiler/error/jimmer-ksp-error/build.gradle.kts",
            "project/compiler/immutable/jimmer-ksp-immutable/build.gradle.kts",
            "project/compiler/transactional/jimmer-ksp-transactional/build.gradle.kts",
            "project/compiler/tuple/jimmer-ksp-tuple/build.gradle.kts",
        ).map(repoRoot::resolve)
    }

    private fun topLevelBootstrapBuildScripts(): List<Path> {
        val repoRoot = CompilerAuditTestSupport.repoRoot
        return listOf(
            "project/jimmer-ksp/build.gradle.kts",
            "project/jimmer-apt/build.gradle.kts",
        ).map(repoRoot::resolve)
    }
}
