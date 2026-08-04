package org.babyfish.jimmer.compiler.transactional

import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.KSPJvmConfig
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSNode
import java.io.File
import java.nio.charset.StandardCharsets
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.babyfish.jimmer.compiler.apt.JimmerProcessor
import org.babyfish.jimmer.compiler.ksp.JimmerProcessorProvider
import org.babyfish.jimmer.compiler.lsi.LsiFrontendOptions
import org.babyfish.jimmer.compiler.lsi.apt.toLsiWorkspace
import org.babyfish.jimmer.compiler.lsi.ksp.toLsiWorkspace
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.transactional.normalizedSnapshot
import site.addzero.lsi.jimmer.transactional.toTransactionalSchema
import site.addzero.lsi.model.LsiWorkspace

class TransactionalCompilerParityTest {

    @Test
    fun `real apt and ksp processors produce equivalent transactional semantics and sources`() {
        val apt = compileApt(JAVA_SOURCE)
        val ksp = compileKsp(KOTLIN_SOURCE)

        assertTrue(apt.success, apt.diagnostics.errorMessage())
        assertEquals(KotlinSymbolProcessing.ExitCode.OK, ksp.exitCode, ksp.logger.text())

        val aptSchema = apt.sourceWorkspace().toTransactionalSchema()
        val kspSchema = ksp.sourceWorkspace().toTransactionalSchema()
        assertEquals(aptSchema.normalizedSnapshot(), kspSchema.normalizedSnapshot())

        assertContentEquals(golden("parity/ServiceATx.java"), apt.generatedSource())
        assertContentEquals(golden("parity/ServiceATx.kt"), ksp.generatedSource())
    }

    private fun compileApt(source: String): AptCompilationResult {
        val projectDir = createTempDirectory(prefix = "jimmer-transactional-apt-parity").toFile()
        val sourceFile = projectDir.resolve("src/main/java/demo/ServiceA.java").also { file ->
            file.parentFile.mkdirs()
            file.writeText(source)
        }
        val classesDir = projectDir.resolve("build/classes").apply(File::mkdirs)
        val generatedDir = projectDir.resolve("build/generated").apply(File::mkdirs)
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val capture = CapturingAptProcessor()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("Transactional parity tests require a JDK compiler")
        val success = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8).use { fileManager ->
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(classesDir))
            fileManager.setLocation(StandardLocation.SOURCE_OUTPUT, listOf(generatedDir))
            val task = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                listOf(
                    "-proc:only",
                    "-classpath",
                    runtimeClasspath().joinToString(File.pathSeparator),
                ),
                null,
                fileManager.getJavaFileObjects(sourceFile),
            )
            task.setProcessors(listOf(capture, JimmerProcessor()))
            task.call()
        }
        return AptCompilationResult(
            success = success,
            diagnostics = diagnostics,
            workspaces = capture.workspaces,
            generatedDir = generatedDir,
        )
    }

    private fun compileKsp(source: String): KspCompilationResult {
        val projectDir = createTempDirectory(prefix = "jimmer-transactional-ksp-parity").toFile()
        val sourceFile = projectDir.resolve("src/main/kotlin/demo/ServiceA.kt").also { file ->
            file.parentFile.mkdirs()
            file.writeText(source)
        }
        val outputDir = projectDir.resolve("build/ksp").apply(File::mkdirs)
        val kotlinOutputDir = outputDir.resolve("kotlin").apply(File::mkdirs)
        val capture = CapturingKspProvider()
        val logger = CapturingKspLogger()
        val configuration = KSPJvmConfig.Builder().apply {
            moduleName = "jimmer-transactional-parity"
            sourceRoots = listOf(sourceFile)
            libraries = runtimeClasspath()
            projectBaseDir = projectDir
            outputBaseDir = outputDir
            cachesDir = outputDir.resolve("caches").apply(File::mkdirs)
            classOutputDir = outputDir.resolve("classes").apply(File::mkdirs)
            javaOutputDir = outputDir.resolve("java").apply(File::mkdirs)
            this.kotlinOutputDir = kotlinOutputDir
            resourceOutputDir = outputDir.resolve("resources").apply(File::mkdirs)
            languageVersion = "2.1"
            apiVersion = "2.1"
            jvmTarget = "17"
            jdkHome = File(System.getProperty("java.home"))
        }.build()
        val exitCode = KotlinSymbolProcessing(
            configuration,
            listOf(JimmerProcessorProvider(), capture),
            logger,
        ).execute()
        return KspCompilationResult(
            exitCode = exitCode,
            logger = logger,
            workspaces = capture.workspaces,
            kotlinOutputDir = kotlinOutputDir,
        )
    }

    private class CapturingAptProcessor : AbstractProcessor() {
        val workspaces = mutableListOf<LsiWorkspace>()

        override fun getSupportedAnnotationTypes(): Set<String> = setOf("*")

        override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()

        override fun process(
            annotations: Set<TypeElement>,
            roundEnvironment: RoundEnvironment,
        ): Boolean {
            if (!roundEnvironment.processingOver()) {
                workspaces += roundEnvironment.toLsiWorkspace(
                    processingEnv,
                    LsiFrontendOptions.from(processingEnv.options),
                )
            }
            return false
        }
    }

    private class CapturingKspProvider : SymbolProcessorProvider {
        val workspaces = mutableListOf<LsiWorkspace>()

        override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
            return object : SymbolProcessor {
                override fun process(resolver: Resolver): List<KSAnnotated> {
                    workspaces += resolver.toLsiWorkspace(LsiFrontendOptions.from(environment.options))
                    return emptyList()
                }
            }
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
            throw e
        }

        fun text(): String = messages.joinToString("\n")
    }

    private data class AptCompilationResult(
        val success: Boolean,
        val diagnostics: DiagnosticCollector<JavaFileObject>,
        val workspaces: List<LsiWorkspace>,
        val generatedDir: File,
    ) {
        fun sourceWorkspace(): LsiWorkspace {
            return workspaces.first { workspace -> workspace[SERVICE_ID] != null }
        }

        fun generatedSource(): ByteArray {
            val file = generatedDir.resolve(JAVA_GENERATED_PATH)
            assertTrue(file.isFile, "Missing generated Transactional source: ${file.absolutePath}")
            return file.readBytes()
        }
    }

    private data class KspCompilationResult(
        val exitCode: KotlinSymbolProcessing.ExitCode,
        val logger: CapturingKspLogger,
        val workspaces: List<LsiWorkspace>,
        val kotlinOutputDir: File,
    ) {
        fun sourceWorkspace(): LsiWorkspace {
            return workspaces.first { workspace -> workspace[SERVICE_ID] != null }
        }

        fun generatedSource(): ByteArray {
            val file = kotlinOutputDir.resolve(KOTLIN_GENERATED_PATH)
            assertTrue(file.isFile, "Missing generated Transactional source: ${file.absolutePath}\n${logger.text()}")
            return file.readBytes()
        }
    }

    private fun golden(path: String): ByteArray {
        return requireNotNull(javaClass.getResource("/transactional/$path")).readBytes()
    }

    private fun DiagnosticCollector<JavaFileObject>.errorMessage(): String {
        return diagnostics.joinToString(separator = "\n") { diagnostic ->
            val source = diagnostic.source?.name.orEmpty()
            val position = if (diagnostic.lineNumber > 0) {
                "${diagnostic.lineNumber}:${diagnostic.columnNumber}"
            } else {
                "?:?"
            }
            "${diagnostic.kind} $source:$position ${diagnostic.getMessage(null)}"
        }
    }

    private companion object {
        val SERVICE_ID = LsiSymbolId.type("demo.ServiceA")
        const val JAVA_GENERATED_PATH = "demo/ServiceATx.java"
        const val KOTLIN_GENERATED_PATH = "demo/ServiceATx.kt"

        val JAVA_SOURCE = """
            package demo;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;
            import org.babyfish.jimmer.sql.JSqlClient;
            import org.babyfish.jimmer.sql.transaction.Propagation;
            import org.babyfish.jimmer.sql.transaction.TargetAnnotation;
            import org.babyfish.jimmer.sql.transaction.Tx;

            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.TYPE)
            @interface Component {}

            @TargetAnnotation(Component.class)
            @Tx(Propagation.MANDATORY)
            public class ServiceA {

                protected JSqlClient sqlClient;

                public int a() {
                    return 1;
                }

                @Tx(Propagation.REQUIRES_NEW)
                protected int b() {
                    return 2;
                }
            }
        """.trimIndent()

        val KOTLIN_SOURCE = """
            package demo

            import org.babyfish.jimmer.sql.kt.KSqlClient
            import org.babyfish.jimmer.sql.transaction.Propagation
            import org.babyfish.jimmer.sql.transaction.TargetAnnotation
            import org.babyfish.jimmer.sql.transaction.Tx

            @Target(AnnotationTarget.CLASS)
            @Retention(AnnotationRetention.RUNTIME)
            annotation class Component

            @TargetAnnotation(Component::class)
            @Tx(Propagation.MANDATORY)
            open class ServiceA {

                protected lateinit var sqlClient: KSqlClient

                open fun a(): Int = 1

                @Tx(Propagation.REQUIRES_NEW)
                protected open fun b(): Int = 2
            }
        """.trimIndent()

        fun runtimeClasspath(): List<File> {
            return System.getProperty("java.class.path")
                .split(File.pathSeparator)
                .map(::File)
                .filter(File::exists)
        }
    }
}
