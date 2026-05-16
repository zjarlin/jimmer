package site.addzero.lsi.apt.codegen

import site.addzero.lsi.apt.context.AptLsiContext
import java.io.File

object AptLsiResourceFiles {

    @JvmStatic
    @Throws(java.io.IOException::class)
    fun generatedResourceFile(path: String): File =
        AptLsiContext.lsiFiler.generatedResourceFile(path)

    @JvmStatic
    @Throws(java.io.IOException::class)
    fun generatedResourcePath(path: String): String =
        AptLsiContext.lsiFiler.generatedResourcePath(path)

    @JvmStatic
    @Throws(java.io.IOException::class)
    fun overwriteGeneratedResourceFile(path: String, content: String) {
        AptLsiContext.lsiFiler.overwriteGeneratedResourceFile(path, content)
    }

    @JvmStatic
    @Throws(java.io.IOException::class)
    fun mergePropertiesResourceFile(path: String, generatedContent: String, comment: String) {
        AptLsiContext.lsiFiler
            .mergePropertiesResourceFile(path, generatedContent, comment)
    }
}
