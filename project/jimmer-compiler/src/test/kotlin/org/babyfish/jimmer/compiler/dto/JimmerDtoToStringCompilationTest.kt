package org.babyfish.jimmer.compiler.dto

import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.KSPJvmConfig
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSNode
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.babyfish.jimmer.compiler.apt.JimmerProcessor
import org.babyfish.jimmer.compiler.ksp.JimmerProcessorProvider
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler

class JimmerDtoToStringCompilationTest {

    @Test
    fun `apt generated dto toString compiles and matches runtime contract`() {
        val classesDir = compileApt()

        assertEquals(EXPECTED_SNAPSHOTS, runtimeSnapshots(classesDir))
    }

    @Test
    fun `ksp generated dto toString compiles and matches runtime contract`() {
        val classesDir = compileKsp()

        assertEquals(EXPECTED_SNAPSHOTS, runtimeSnapshots(classesDir))
    }

    private fun compileApt(): File {
        val projectDir = fixtureProject("jimmer-dto-to-string-apt")
        val sourceFiles = writeSources(
            projectDir.resolve("src/main/java"),
            mapOf("demo/Sample.java" to JAVA_SOURCE),
        )
        writeDtoSource(projectDir)
        val processingClassesDir = projectDir.resolve("build/processing-classes").apply(File::mkdirs)
        val generatedDir = projectDir.resolve("build/generated").apply(File::mkdirs)
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("JDK compiler is required to run DTO toString APT tests")
        val succeeded = compiler.getStandardFileManager(
            diagnostics,
            null,
            StandardCharsets.UTF_8,
        ).use { fileManager ->
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(processingClassesDir))
            fileManager.setLocation(StandardLocation.SOURCE_OUTPUT, listOf(generatedDir))
            val task = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                listOf("-proc:only", "-classpath", runtimeClasspathText()),
                null,
                fileManager.getJavaFileObjectsFromFiles(sourceFiles),
            )
            task.setProcessors(listOf(JimmerProcessor()))
            task.call()
        }
        assertTrue(succeeded, diagnostics.toErrorMessage())
        val generatedFiles = generatedDir.walkTopDown()
            .filter { file -> file.isFile && file.extension == "java" }
            .sortedBy(File::getAbsolutePath)
            .toList()
        assertGeneratedDtoFiles(generatedFiles, "java", "APT")
        return compileWithJavac(projectDir, sourceFiles + generatedFiles)
    }

    private fun compileKsp(): File {
        val projectDir = fixtureProject("jimmer-dto-to-string-ksp")
        val sourceFiles = writeSources(
            projectDir.resolve("src/main/kotlin"),
            mapOf("demo/Sample.kt" to KOTLIN_SOURCE),
        )
        writeDtoSource(projectDir)
        val outputDir = projectDir.resolve("build/ksp").apply(File::mkdirs)
        val kotlinOutputDir = outputDir.resolve("kotlin").apply(File::mkdirs)
        val logger = CapturingKspLogger()
        val configuration = KSPJvmConfig.Builder().apply {
            moduleName = "jimmer-dto-to-string-compilation"
            sourceRoots = sourceFiles
            libraries = runtimeClasspath()
            projectBaseDir = projectDir
            outputBaseDir = outputDir
            cachesDir = outputDir.resolve("caches").apply(File::mkdirs)
            classOutputDir = outputDir.resolve("classes").apply(File::mkdirs)
            javaOutputDir = outputDir.resolve("java").apply(File::mkdirs)
            this.kotlinOutputDir = kotlinOutputDir
            resourceOutputDir = outputDir.resolve("resources").apply(File::mkdirs)
            processorOptions = mapOf("jimmer.dto.mutable" to "true")
            languageVersion = "2.1"
            apiVersion = "2.1"
            jvmTarget = "17"
            jdkHome = File(System.getProperty("java.home"))
        }.build()
        val processingExitCode = KotlinSymbolProcessing(
            configuration,
            listOf(JimmerProcessorProvider()),
            logger,
        ).execute()
        assertEquals(KotlinSymbolProcessing.ExitCode.OK, processingExitCode, logger.text())
        val generatedFiles = kotlinOutputDir.walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .sortedBy(File::getAbsolutePath)
            .toList()
        assertGeneratedDtoFiles(generatedFiles, "kt", "KSP")
        return compileWithK2(projectDir, sourceFiles + generatedFiles)
    }

    private fun compileWithJavac(
        projectDir: File,
        sourceFiles: List<File>,
    ): File {
        val classesDir = projectDir.resolve("build/compiled-classes").apply(File::mkdirs)
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("JDK compiler is required to compile generated DTO sources")
        val succeeded = compiler.getStandardFileManager(
            diagnostics,
            null,
            StandardCharsets.UTF_8,
        ).use { fileManager ->
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(classesDir))
            compiler.getTask(
                null,
                fileManager,
                diagnostics,
                listOf("-proc:none", "-classpath", runtimeClasspathText()),
                null,
                fileManager.getJavaFileObjectsFromFiles(sourceFiles),
            ).call()
        }
        assertTrue(succeeded, diagnostics.toErrorMessage())
        return classesDir
    }

    private fun compileWithK2(
        projectDir: File,
        sourceFiles: List<File>,
    ): File {
        val classesDir = projectDir.resolve("build/compiled-classes").apply(File::mkdirs)
        val messages = ByteArrayOutputStream()
        val arguments = buildList {
            add("-no-stdlib")
            add("-no-reflect")
            add("-jvm-target")
            add("17")
            add("-classpath")
            add(runtimeClasspathText())
            add("-d")
            add(classesDir.absolutePath)
            sourceFiles.mapTo(this) { file -> file.absolutePath }
        }
        val exitCode = PrintStream(messages, true, StandardCharsets.UTF_8).use { stream ->
            K2JVMCompiler().exec(stream, *arguments.toTypedArray())
        }
        assertEquals(ExitCode.OK, exitCode, messages.toString(StandardCharsets.UTF_8))
        return classesDir
    }

    private fun runtimeSnapshots(classesDir: File): List<String> {
        val urls = arrayOf(classesDir.toURI().toURL())
        return URLClassLoader(urls, javaClass.classLoader).use { classLoader ->
            val plain = newPlainArrayInput(classLoader)
            val dynamicEmpty = newDto(classLoader, "DynamicShadowInput")
            val dynamicLoadedNull = newDto(classLoader, "DynamicShadowInput").apply {
                setProperty("builder", null)
            }
            val dynamicMixed = newDto(classLoader, "DynamicShadowInput").apply {
                setMixedValues()
            }
            val fuzzyEmpty = newDto(classLoader, "FuzzyShadowInput")
            val fuzzyBuilder = newDto(classLoader, "FuzzyShadowInput").apply {
                setProperty("builder", "builder-value")
            }
            val fuzzyMixed = newDto(classLoader, "FuzzyShadowInput").apply {
                setMixedValues()
            }
            listOf(
                plain,
                dynamicEmpty,
                dynamicLoadedNull,
                dynamicMixed,
                fuzzyEmpty,
                fuzzyBuilder,
                fuzzyMixed,
            ).map { value -> value.toString().normalizeArrayIdentities() }
        }
    }

    private fun newPlainArrayInput(classLoader: ClassLoader): Any {
        val type = classLoader.loadClass("demo.dto.PlainArrayInput")
        val chars = charArrayOf('A', 'Z')
        val numbers = intArrayOf(1, 2)
        val primaryConstructor = type.constructors.singleOrNull { constructor ->
            constructor.parameterTypes.contentEquals(
                arrayOf(CharArray::class.java, IntArray::class.java, String::class.java),
            )
        }
        if (primaryConstructor != null) {
            return primaryConstructor.newInstance(chars, numbers, "keyword")
        }
        return type.getConstructor().newInstance().apply {
            setProperty("chars", chars)
            setProperty("numbers", numbers)
            setProperty("when", "keyword")
        }
    }

    private fun newDto(classLoader: ClassLoader, simpleName: String): Any {
        return classLoader
            .loadClass("demo.dto.$simpleName")
            .getConstructor()
            .newInstance()
    }

    private fun Any.setMixedValues() {
        setProperty("separator", "separator-value")
        setProperty("_sp", "sp-value")
        setProperty("when", "keyword")
        setProperty("chars", charArrayOf('A', 'Z'))
        setProperty("numbers", intArrayOf(1, 2))
    }

    private fun Any.setProperty(name: String, value: Any?) {
        val setterName = "set" + name.replaceFirstChar { character ->
            if (character.isLowerCase()) {
                character.titlecase()
            } else {
                character.toString()
            }
        }
        val setter = javaClass.methods.singleOrNull { method ->
            method.name == setterName && method.parameterCount == 1
        } ?: error("There is no unique setter '$setterName' on ${javaClass.name}")
        setter.invoke(this, value)
    }

    private fun assertGeneratedDtoFiles(
        generatedFiles: List<File>,
        extension: String,
        platform: String,
    ) {
        val actualNames = generatedFiles.map(File::getName).toSet()
        val expectedNames = DTO_SIMPLE_NAMES.mapTo(linkedSetOf()) { name -> "$name.$extension" }
        assertTrue(
            actualNames.containsAll(expectedNames),
            "$platform did not generate DTO files: ${expectedNames - actualNames}",
        )
    }

    private fun fixtureProject(prefix: String): File {
        return createTempDirectory(prefix = prefix)
            .toFile()
            .resolve("fixture")
            .apply(File::mkdirs)
    }

    private fun writeSources(
        sourceRoot: File,
        sources: Map<String, String>,
    ): List<File> {
        return sources.map { (relativePath, content) ->
            sourceRoot.resolve(relativePath).apply {
                parentFile.mkdirs()
                writeText(content)
            }
        }
    }

    private fun writeDtoSource(projectDir: File) {
        projectDir.resolve("src/main/dto/demo/Sample.dto").apply {
            parentFile.mkdirs()
            writeText(DTO_SOURCE)
        }
    }

    private fun String.normalizeArrayIdentities(): String {
        return replace(INT_ARRAY_IDENTITY_PATTERN, "<int-array>")
    }

    private fun DiagnosticCollector<JavaFileObject>.toErrorMessage(): String {
        return diagnostics.joinToString("\n") { diagnostic ->
            "${diagnostic.kind} ${diagnostic.source?.name.orEmpty()}:" +
                "${diagnostic.lineNumber}:${diagnostic.columnNumber} ${diagnostic.getMessage(null)}"
        }
    }

    private class CapturingKspLogger : KSPLogger {
        private val messages = mutableListOf<String>()

        override fun logging(message: String, symbol: KSNode?) {
            messages += message
        }

        override fun info(message: String, symbol: KSNode?) {
            messages += message
        }

        override fun warn(message: String, symbol: KSNode?) {
            messages += message
        }

        override fun error(message: String, symbol: KSNode?) {
            messages += message
        }

        override fun exception(e: Throwable) {
            messages += e.stackTraceToString()
        }

        fun text(): String = messages.joinToString("\n")
    }

    private companion object {
        val DTO_SIMPLE_NAMES = listOf(
            "PlainArrayInput",
            "DynamicShadowInput",
            "FuzzyShadowInput",
        )

        val EXPECTED_SNAPSHOTS = listOf(
            "PlainArrayInput(chars=AZ, numbers=<int-array>, when=keyword)",
            "DynamicShadowInput()",
            "DynamicShadowInput(builder=null)",
            "DynamicShadowInput(separator=separator-value, _sp=sp-value, when=keyword, chars=AZ, numbers=<int-array>)",
            "FuzzyShadowInput()",
            "FuzzyShadowInput(builder=builder-value)",
            "FuzzyShadowInput(separator=separator-value, _sp=sp-value, when=keyword, chars=AZ, numbers=<int-array>)",
        )

        val INT_ARRAY_IDENTITY_PATTERN = Regex("""\[I@[0-9a-fA-F]+""")

        val JAVA_SOURCE = """
            package demo;

            import org.babyfish.jimmer.sql.Entity;
            import org.babyfish.jimmer.sql.Id;

            @Entity
            public interface Sample {
                @Id
                long id();

                String name();

                String description();

                String note();

                String marker();

                char[] chars();

                int[] numbers();
            }
        """.trimIndent()

        val KOTLIN_SOURCE = """
            package demo

            import org.babyfish.jimmer.sql.Entity
            import org.babyfish.jimmer.sql.Id

            @Entity
            interface Sample {
                @Id
                val id: Long

                val name: String

                val description: String

                val note: String

                val marker: String

                val chars: CharArray

                val numbers: IntArray
            }
        """.trimIndent()

        val DTO_SOURCE = """
            package demo.dto

            input PlainArrayInput {
                chars
                numbers
                marker as when
            }

            dynamic input DynamicShadowInput {
                name? as builder
                description? as separator
                note? as _sp
                marker? as when
                chars?
                numbers?
            }

            fuzzy input FuzzyShadowInput {
                name? as builder
                description? as separator
                note? as _sp
                marker? as when
                chars?
                numbers?
            }
        """.trimIndent()

        fun runtimeClasspath(): List<File> {
            return System.getProperty("java.class.path")
                .split(File.pathSeparator)
                .filter(String::isNotBlank)
                .map(::File)
                .filter(File::exists)
        }

        fun runtimeClasspathText(): String = runtimeClasspath().joinToString(File.pathSeparator)
    }
}
