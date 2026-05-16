package site.addzero.lsi.jimmer.client.metadata.extractor

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

class CompilerSharedBuildBoundaryAuditTest {

    private val forbiddenMainDependencyPatterns = listOf(
        Regex("""(?m)^\s*(implementation|api|compileOnly|runtimeOnly)\(projects\.lib\.lsi\.lsiKsp\)"""),
        Regex("""(?m)^\s*(implementation|api|compileOnly|runtimeOnly)\(projects\.lib\.lsi\.lsiApt\)"""),
        Regex("""(?m)^\s*(implementation|api|compileOnly|runtimeOnly)\(project\(":lib:lsi:lsi-ksp"\)\)"""),
        Regex("""(?m)^\s*(implementation|api|compileOnly|runtimeOnly)\(project\(":lib:lsi:lsi-apt"\)\)"""),
        Regex("""(?m)^\s*(implementation|api|compileOnly|runtimeOnly)\(libs\.kotlinpoet\)"""),
        Regex("""(?m)^\s*(implementation|api|compileOnly|runtimeOnly)\(libs\.javapoet\)"""),
    )

    @Test
    fun `shared compiler module build scripts stay free of platform main dependencies`() {
        val buildScripts = sharedCompilerBuildScripts()
        assertTrue(
            buildScripts.any {
                it.toString().endsWith("/project/compiler/jimmer-ksp-ext/build.gradle.kts")
            },
            "shared build audit must include project/compiler/jimmer-ksp-ext/build.gradle.kts",
        )
        for (buildScript in buildScripts) {
            val source = Files.readString(buildScript)
            for (pattern in forbiddenMainDependencyPatterns) {
                assertFalse(
                    pattern.containsMatchIn(source),
                    "${buildScript.name} must not declare shared main dependency `${pattern.pattern}`",
                )
            }
        }
    }

    @Test
    fun `immutable shared modules no longer keep dto compiler as a leaked direct dependency`() {
        val extractor = CompilerAuditTestSupport.sourceOf(
            "project/compiler/immutable/immutable-metadata-extractor/build.gradle.kts"
        )
        val generator = CompilerAuditTestSupport.sourceOf(
            "project/compiler/immutable/immutable-metadata-generator/build.gradle.kts"
        )

        CompilerAuditTestSupport.assertContainsNone(
            extractor,
            listOf("implementation(projects.project.jimmerDtoCompiler)"),
            "immutable metadata extractor build script",
        )
        CompilerAuditTestSupport.assertContainsNone(
            generator,
            listOf("implementation(projects.project.jimmerDtoCompiler)"),
            "immutable metadata generator build script",
        )
    }

    @Test
    fun `jimmer ksp ext no longer keeps dto compiler bridge dependencies`() {
        val source = CompilerAuditTestSupport.sourceOf(
            "project/compiler/jimmer-ksp-ext/build.gradle.kts"
        )

        CompilerAuditTestSupport.assertContainsNone(
            source,
            listOf("implementation(projects.project.jimmerDtoCompiler)"),
            "jimmer-ksp-ext build script",
        )
    }

    private fun sharedCompilerBuildScripts(): List<Path> {
        val repoRoot = CompilerAuditTestSupport.repoRoot
        val scripts = listOf(
            "project/compiler/jimmer-ksp-ext/build.gradle.kts",
            "project/compiler/client/client-metadata-extractor/build.gradle.kts",
            "project/compiler/client/client-metadata-generator/build.gradle.kts",
            "project/compiler/client/client-metadata-model/build.gradle.kts",
            "project/compiler/dto/dto-metadata-generator/build.gradle.kts",
            "project/compiler/error/error-metadata-extractor/build.gradle.kts",
            "project/compiler/error/error-metadata-generator/build.gradle.kts",
            "project/compiler/error/error-metadata-model/build.gradle.kts",
            "project/compiler/immutable/immutable-metadata-extractor/build.gradle.kts",
            "project/compiler/immutable/immutable-metadata-generator/build.gradle.kts",
            "project/compiler/immutable/immutable-metadata-model/build.gradle.kts",
            "project/compiler/transactional/tx-metadata-extractor/build.gradle.kts",
            "project/compiler/transactional/tx-metadata-generator/build.gradle.kts",
            "project/compiler/transactional/tx-metadata-model/build.gradle.kts",
            "project/compiler/tuple/tuple-metadata-extractor/build.gradle.kts",
            "project/compiler/tuple/tuple-metadata-generator/build.gradle.kts",
            "project/compiler/tuple/tuple-metadata-model/build.gradle.kts",
        ).map(repoRoot::resolve)
        return scripts.filter(Files::exists)
    }
}
