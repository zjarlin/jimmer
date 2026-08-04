package site.addzero.lsi.apt

import java.io.IOException
import javax.annotation.processing.Filer
import javax.tools.StandardLocation

class AptCompilerInputResourceReader(
    private val filer: Filer,
) {

    fun read(paths: Set<String>): Map<String, String> {
        return paths.sorted().mapNotNull { path ->
            val content = try {
                filer.getResource(StandardLocation.CLASS_OUTPUT, "", path)
                    .openReader(true)
                    .use { reader -> reader.readText() }
            } catch (_: IOException) {
                null
            }
            content?.let { path to it }
        }.toMap()
    }
}
