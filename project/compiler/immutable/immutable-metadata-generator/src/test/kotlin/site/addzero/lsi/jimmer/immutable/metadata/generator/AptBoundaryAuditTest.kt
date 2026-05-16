package site.addzero.lsi.jimmer.immutable.metadata.generator

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import site.addzero.lsi.jimmer.immutable.ImmutableTestSupport
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name

class AptBoundaryAuditTest {

    @Test
    fun `project jimmer apt stays free of javapoet and reverse poet bridges`() {
        for (file in aptMainSources()) {
            val source = Files.readString(file)
            assertFalse(source.contains("import com.squareup.javapoet"), "${file.name} must not import JavaPoet")
            assertFalse(source.contains("com.squareup.javapoet."), "${file.name} must not reference JavaPoet")
        assertFalse(source.contains("toJavaPoet("), "${file.name} must not call toJavaPoet")
    }
    }

    @Test
    fun `apt legacy context bridge is deleted`() {
        assertFalse(
            Files.exists(
                ImmutableTestSupport.repoRoot.resolve("project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/Context.java")
            )
        )
    }

    @Test
    fun `legacy apt immutable meta stays quarantined to local helper points`() {
        val repoRoot = ImmutableTestSupport.repoRoot
        val aptRoot = repoRoot.resolve("project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt")
        val hits = Files.walk(aptRoot).use { paths ->
            paths
                .filter { Files.isRegularFile(it) }
                .filter { it.toString().endsWith(".java") }
                .filter { !it.invariantSeparatorsPathString.contains("/immutable/meta/") }
                .map { file ->
                    val source = Files.readString(file)
                    if (source.contains("import org.babyfish.jimmer.apt.immutable.meta.")) {
                        aptRoot.relativize(file).invariantSeparatorsPathString
                    } else {
                        ""
                    }
                }
                .filter { it.isNotEmpty() }
                .sorted()
                .toList()
        }

        assertEquals(emptyList<String>(), hits)
    }

    @Test
    fun `legacy apt immutable meta subtree is deleted`() {
        val metaRoot = ImmutableTestSupport.repoRoot.resolve(
            "project/jimmer-apt/src/main/java/org/babyfish/jimmer/apt/immutable/meta"
        )
        val remainingSources = if (Files.exists(metaRoot)) {
            Files.walk(metaRoot).use { paths ->
                paths
                    .filter { Files.isRegularFile(it) }
                    .filter { it.toString().endsWith(".java") || it.toString().endsWith(".kt") }
                    .toList()
            }
        } else {
            emptyList()
        }
        assertTrue(remainingSources.isEmpty(), "$metaRoot must not contain source files: $remainingSources")
    }

    private fun aptMainSources(): List<Path> = ImmutableTestSupport.aptMainSourceFiles()
}
