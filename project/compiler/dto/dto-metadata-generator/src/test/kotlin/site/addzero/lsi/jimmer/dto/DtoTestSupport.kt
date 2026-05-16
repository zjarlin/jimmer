package site.addzero.lsi.jimmer.dto

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

object DtoTestSupport {

    val repoRoot: Path =
        generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .firstOrNull { Files.exists(it.resolve("settings.gradle.kts")) }
            ?: error("Cannot locate repository root")

    fun readSource(vararg relativePaths: String): String =
        locateSource(*relativePaths)
            ?.let(Files::readString)
            ?: error("Cannot locate source: ${relativePaths.joinToString()}")

    fun locateSource(vararg relativePaths: String): Path? =
        relativePaths
            .asSequence()
            .map { repoRoot.resolve(it).normalize() }
            .firstOrNull(Files::exists)

    fun dtoSharedSources(): List<Path> =
        sequenceOf(
            "src/main/kotlin/site/addzero/lsi/jimmer/dto",
            "project/compiler/dto/dto-metadata-generator/src/main/kotlin/site/addzero/lsi/jimmer/dto",
        ).map { repoRoot.resolve(it).normalize() }
            .firstOrNull(Files::isDirectory)
            ?.let { root ->
                Files.walk(root)
                    .filter { Files.isRegularFile(it) && it.name.endsWith(".kt") }
                    .sorted()
                    .toList()
            }
            ?: error("Cannot locate DTO shared sources")
}
