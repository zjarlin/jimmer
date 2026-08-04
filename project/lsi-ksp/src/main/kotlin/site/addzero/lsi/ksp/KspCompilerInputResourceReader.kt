package site.addzero.lsi.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import java.io.File
import java.io.IOException

class KspCompilerInputResourceReader(
    private val codeGenerator: CodeGenerator,
) {

    fun read(paths: Set<String>): Map<String, String> {
        val resourceDirectory = codeGenerator.generatedFile
            .asSequence()
            .mapNotNull(File::kspResourceDirectory)
            .firstOrNull()
            ?: return emptyMap()
        return paths.sorted().mapNotNull { path ->
            val content = try {
                resourceDirectory.resolve(path).takeIf(File::isFile)?.readText()
            } catch (_: IOException) {
                null
            }
            content?.let { path to it }
        }.toMap()
    }
}

private fun File.kspResourceDirectory(): File? {
    var child = absoluteFile
    var parent = child.parentFile
    while (parent != null) {
        if (parent.name == "ksp" && parent.parentFile?.name == "generated") {
            return child.resolve("resources")
        }
        child = parent
        parent = parent.parentFile
    }
    return null
}
