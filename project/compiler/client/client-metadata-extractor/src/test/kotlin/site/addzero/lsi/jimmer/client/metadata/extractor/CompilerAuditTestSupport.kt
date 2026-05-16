package site.addzero.lsi.jimmer.client.metadata.extractor

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

internal object CompilerAuditTestSupport {

    private val compilerBoundaryModulePrefixes = listOf(
        "client/jimmer-ksp-client/",
        "dto/jimmer-ksp-dto/",
        "error/jimmer-ksp-error/",
        "immutable/jimmer-ksp-immutable/",
        "transactional/jimmer-ksp-transactional/",
        "tuple/jimmer-ksp-tuple/",
    )

    val repoRoot: Path by lazy {
        var current = Path.of("").toAbsolutePath()
        while (current.parent != null) {
            if (Files.exists(current.resolve("settings.gradle.kts"))) {
                return@lazy current
            }
            current = current.parent
        }
        error("Cannot locate repository root from ${Path.of("").toAbsolutePath()}")
    }

    fun sourceOf(relativePath: String): String =
        Files.readString(repoRoot.resolve(relativePath))

    fun assertContainsAll(source: String, snippets: List<String>, owner: String) {
        for (snippet in snippets) {
            assertTrue(source.contains(snippet), "$owner must contain `$snippet`\n$source")
        }
    }

    fun assertContainsNone(source: String, snippets: List<String>, owner: String) {
        for (snippet in snippets) {
            assertFalse(source.contains(snippet), "$owner must not contain `$snippet`\n$source")
        }
    }

    fun collectSourceFiles(roots: List<Path>, fileFilter: (Path) -> Boolean = ::isKotlinOrJavaSource): List<Path> =
        roots.asSequence()
            .filter(Files::exists)
            .flatMap { root ->
                when {
                    Files.isRegularFile(root) -> sequenceOf(root)
                    Files.isDirectory(root) -> Files.walk(root)
                        .filter { Files.isRegularFile(it) }
                        .filter(fileFilter)
                        .sorted()
                        .toList()
                        .asSequence()

                    else -> emptySequence()
                }
            }
            .distinct()
            .toList()

    fun isMainSource(file: Path, moduleRoot: Path): Boolean {
        val relative = moduleRoot.relativize(file).invariantSeparatorsPathString
        if (!relative.startsWith("src/main/") && !relative.contains("/src/main/")) {
            return false
        }
        if (relative.contains("/build/")) {
            return false
        }
        return isKotlinOrJavaSource(file)
    }

    fun compilerSharedSources(): List<Path> {
        val compilerRoot = repoRoot.resolve("project/compiler")
        return collectSourceFiles(listOf(compilerRoot)) {
            isCompilerSharedMainSource(it, compilerRoot)
        }
    }

    fun sharedCompilerCoreSources(): List<Path> {
        val compilerRoot = repoRoot.resolve("project/compiler")
        val lsiCoreRoot = repoRoot.resolve("lib/lsi/lsi-core")
        val lsiJimmerRoot = repoRoot.resolve("lib/lsi/lsi-jimmer")
        return collectSourceFiles(
            listOf(compilerRoot, lsiCoreRoot, lsiJimmerRoot)
        ) { file ->
            when {
                file.startsWith(compilerRoot) -> isCompilerSharedMainSource(file, compilerRoot)
                file.startsWith(lsiCoreRoot) -> isMainSource(file, lsiCoreRoot)
                file.startsWith(lsiJimmerRoot) -> isMainSource(file, lsiJimmerRoot)
                else -> false
            }
        }
    }

    private fun isCompilerSharedMainSource(file: Path, compilerRoot: Path): Boolean {
        if (!isMainSource(file, compilerRoot)) {
            return false
        }
        val relative = compilerRoot.relativize(file).invariantSeparatorsPathString
        return compilerBoundaryModulePrefixes.none { relative.startsWith(it) }
    }

    private fun isKotlinOrJavaSource(file: Path): Boolean {
        val path = file.invariantSeparatorsPathString
        return !path.contains("/build/") && (path.endsWith(".kt") || path.endsWith(".java"))
    }
}
