package site.addzero.lsi.apt.codegen

import site.addzero.lsi.codegen.LsiFiler
import site.addzero.lsi.poet.LsiFileSpec
import site.addzero.lsi.poet.renderJavaSource
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import javax.annotation.processing.ProcessingEnvironment
import javax.tools.Diagnostic
import javax.tools.StandardLocation

class AptLsiFiler(val processingEnv: ProcessingEnvironment) : LsiFiler {

    override fun createSourceFile(qualifiedName: String, content: String) {
        try {
            val filer = processingEnv.filer
            val sourceFile = filer.createSourceFile(qualifiedName)
            sourceFile.openWriter().use { writer ->
                writer.write(content)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val msg = "Failed to create source file " + "$qualifiedName: ${e.message}"
            processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, msg)
        }
    }

    override fun createSourceFile(fileSpec: LsiFileSpec) {
        createSourceFile(fileSpec.qualifiedName, fileSpec.renderJavaSource())
    }

    override fun createResourceFile(path: String, content: String) {
        try {
            val normalized = path.removePrefix("/")
            val resource = processingEnv.filer.createResource(StandardLocation.CLASS_OUTPUT, "", normalized)
            resource.openWriter().use { writer ->
                writer.write(content)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val msg = "Failed to create resource file $path: ${e.message}"
            processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, msg)
        }
    }

    override fun overwriteResourceFile(path: String, content: String) {
        try {
            overwriteGeneratedResourceFile(path, content)
        } catch (e: Exception) {
            e.printStackTrace()
            val msg = "Failed to overwrite resource file $path: ${e.message}"
            processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, msg)
        }
    }

    override fun readResourceText(path: String): String? =
        try {
            val file = generatedResourceFile(path)
            if (file.exists()) {
                Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8).use { reader ->
                    reader.readText()
                }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }

    @Throws(java.io.IOException::class)
    fun generatedResourceFile(path: String): File {
        val normalized = path.removePrefix("/")
        val resource = processingEnv.filer.getResource(StandardLocation.CLASS_OUTPUT, "", normalized)
        return File(resource.name)
    }

    @Throws(java.io.IOException::class)
    fun generatedResourcePath(path: String): String =
        generatedResourceFile(path).path

    @Throws(java.io.IOException::class)
    fun overwriteGeneratedResourceFile(path: String, content: String) {
        val file = generatedResourceFile(path)
        file.parentFile?.mkdirs()
        Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8).use { writer ->
            writer.write(content)
        }
    }
}
