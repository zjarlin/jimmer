package site.addzero.lsi.jimmer.immutable

import site.addzero.lsi.jimmer.immutable.generator.ImmutableGeneratorTestFixtures
import site.addzero.lsi.jimmer.immutable.generator.ImmutableGenerationMode
import site.addzero.lsi.jimmer.immutable.metadata.generator.ImmutableArtifactShape
import site.addzero.lsi.jimmer.immutable.metadata.generator.ImmutableGeneratedOutput
import site.addzero.lsi.jimmer.immutable.metadata.generator.toGeneratedOutput
import site.addzero.lsi.poet.LsiFileSpec
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile

internal object ImmutableTestSupport {

    val repoRoot: Path by lazy {
        var current = Path.of("").toAbsolutePath().normalize()
        while (current.parent != null) {
            if (Files.exists(current.resolve("settings.gradle.kts"))) {
                return@lazy current
            }
            current = current.parent
        }
        error("Cannot locate repository root from ${Path.of("").toAbsolutePath()}")
    }

    fun sharedGeneratedOutput(mode: ImmutableGenerationMode): ImmutableGeneratedOutput =
        listOf(ImmutableGeneratorTestFixtures.referenceSourceGenerationPlan()).toGeneratedOutput(
            jacksonTypes = ImmutableGeneratorTestFixtures.jacksonTypes(),
            existingEntitiesResourceFile = null,
            isResourceGenerationIgnored = true,
            isModuleRequired = false,
            generationMode = mode,
            currentVersionValue = ImmutableGeneratorTestFixtures.CURRENT_VERSION_VALUE,
        )

    fun sharedGeneratedFileSpecs(mode: ImmutableGenerationMode): List<LsiFileSpec> =
        sharedGeneratedOutput(mode).sourceFileSpecs

    fun sharedArtifactShapes(mode: ImmutableGenerationMode): List<ImmutableArtifactShape> =
        sharedGeneratedFileSpecs(mode).map(ImmutableArtifactShape::from)

    fun metadataModelFiles(): List<Path> =
        Files.list(metadataModelDirectory()).use { paths ->
            paths
                .filter { it.isRegularFile() && it.extension == "kt" }
                .sorted()
                .toList()
        }

    fun generatorFiles(): List<Path> =
        Files.walk(generatorDirectory()).use { paths ->
            paths
                .filter { it.isRegularFile() && it.extension == "kt" }
                .sorted()
                .toList()
        }

    fun aptMainSourceFiles(): List<Path> {
        val aptRoot = repoRoot.resolve("project/jimmer-apt")
        return Files.walk(aptRoot).use { paths ->
            paths
                .filter { Files.isRegularFile(it) }
                .filter { it.toString().endsWith(".java") || it.toString().endsWith(".kt") }
                .filter { isMainSourceUnder(it, aptRoot) }
                .sorted()
                .toList()
        }
    }

    private fun metadataModelDirectory(): Path {
        val candidates = listOf(
            repoRoot.resolve("project/compiler/immutable/immutable-metadata-model/src/main/kotlin/site/addzero/lsi/jimmer/immutable/metadata/model"),
            repoRoot.resolve("project/compiler/immutable/immutable-metadata-generator/../immutable-metadata-model/src/main/kotlin/site/addzero/lsi/jimmer/immutable/metadata/model").normalize(),
        )
        return candidates.firstOrNull(Files::exists)
            ?: error("Cannot locate immutable metadata model directory from $repoRoot")
    }

    private fun generatorDirectory(): Path {
        val candidates = listOf(
            repoRoot.resolve("project/compiler/immutable/immutable-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/immutable/generator"),
            repoRoot.resolve("project/compiler/immutable/immutable-metadata-generator/src/test/kotlin/../main/kotlin/site/addzero/lsi/jimmer/immutable/generator").normalize(),
        )
        return candidates.firstOrNull(Files::exists)
            ?: error("Cannot locate immutable generator directory from $repoRoot")
    }

    private fun isMainSourceUnder(file: Path, root: Path): Boolean {
        val relative = root.relativize(file).invariantSeparatorsPathString
        if (!relative.contains("/src/main/")) {
            return false
        }
        if (relative.contains("/build/")) {
            return false
        }
        return true
    }
}
