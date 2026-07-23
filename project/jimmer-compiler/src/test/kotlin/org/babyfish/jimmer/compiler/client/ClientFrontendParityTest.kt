package org.babyfish.jimmer.compiler.client

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
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.babyfish.jimmer.compiler.lsi.LsiFrontendOptions
import org.babyfish.jimmer.compiler.lsi.apt.toLsiWorkspace
import org.babyfish.jimmer.compiler.lsi.ksp.toLsiWorkspace
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.client.ClientOperation
import site.addzero.lsi.jimmer.client.ClientSchemaDependencies
import site.addzero.lsi.jimmer.client.normalizedSnapshot
import site.addzero.lsi.jimmer.client.toClientSchema
import site.addzero.lsi.jimmer.error.ErrorSchema
import site.addzero.lsi.model.LsiFunction
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.toSemanticSnapshot

private fun emptyClientDependencies(): ClientSchemaDependencies {
    return ClientSchemaDependencies(
        immutableSchema = ImmutableSchema(emptyList()),
        errorSchema = ErrorSchema(emptyList()),
        definitionDocumentationByTypeId = emptyMap(),
    )
}

class ClientFrontendParityTest {

    @Test
    fun `java throws and kotlin Throws produce identical client exception schema`() {
        val aptWorkspace = compileJava(JAVA_SOURCES)
        val kspWorkspace = compileKotlin(KOTLIN_SOURCES)
        val aptSchema = aptWorkspace.toClientSchema(emptyClientDependencies())
        val kspSchema = kspWorkspace.toClientSchema(emptyClientDependencies())

        val serviceId = LsiSymbolId.type("demo.ErrorService")
        val aptCallableIds = aptWorkspace.declarationsOfType<LsiFunction>()
            .filter { function -> function.ownerId == serviceId }
            .mapTo(linkedSetOf(), LsiFunction::id)
        val kspCallableIds = kspWorkspace.declarationsOfType<LsiFunction>()
            .filter { function -> function.ownerId == serviceId }
            .mapTo(linkedSetOf(), LsiFunction::id)
        assertEquals(aptCallableIds, kspCallableIds)
        assertEquals(aptSchema.normalizedSnapshot(), kspSchema.normalizedSnapshot())

        val identityOwnerId = LsiSymbolId.type("demo.CallableIdentity")
        val aptIdentityFunctions = aptWorkspace.declarationsOfType<LsiFunction>()
            .filter { function -> function.ownerId == identityOwnerId }
        val kspIdentityFunctions = kspWorkspace.declarationsOfType<LsiFunction>()
            .filter { function -> function.ownerId == identityOwnerId }
        val expectedIdentityIds = setOf(
            LsiSymbolId.function(identityOwnerId, "unit", listOf("type:kotlin.Unit")),
            LsiSymbolId.function(identityOwnerId, "voidValue", listOf("type:java.lang.Void")),
            LsiSymbolId.function(identityOwnerId, "unitArray", listOf("array:type:kotlin.Unit")),
            LsiSymbolId.function(identityOwnerId, "voidArray", listOf("array:type:java.lang.Void")),
            LsiSymbolId.function(
                identityOwnerId,
                "unitGeneric",
                listOf("type:java.util.List<type:kotlin.Unit>"),
            ),
            LsiSymbolId.function(
                identityOwnerId,
                "voidGeneric",
                listOf("type:java.util.List<type:java.lang.Void>"),
            ),
            LsiSymbolId.function(identityOwnerId, "unitVararg", listOf("array:type:kotlin.Unit")),
            LsiSymbolId.function(identityOwnerId, "receiver", listOf("type:kotlin.Unit")),
            LsiSymbolId.function(identityOwnerId, "unitReturn"),
            LsiSymbolId.function(identityOwnerId, "boxedVoidReturn", listOf("primitive:int")),
            LsiSymbolId.function(
                identityOwnerId,
                "bounded",
                listOf("parameter:method:bounded:0:type:java.lang.Integer"),
            ),
            LsiSymbolId.function(
                identityOwnerId,
                "comparable",
                listOf("parameter:method:comparable:0:type:java.lang.Comparable"),
            ),
            LsiSymbolId.function(
                identityOwnerId,
                "ordered",
                listOf("parameter:method:ordered:0:type:java.lang.Object"),
            ),
            LsiSymbolId.function(
                identityOwnerId,
                "ordered",
                listOf("parameter:method:ordered:0:type:java.io.Serializable"),
            ),
        )
        assertEquals(expectedIdentityIds, aptIdentityFunctions.mapTo(linkedSetOf(), LsiFunction::id))
        assertEquals(expectedIdentityIds, kspIdentityFunctions.mapTo(linkedSetOf(), LsiFunction::id))
        assertEquals(
            aptWorkspace.identitySemanticSnapshot(),
            kspWorkspace.identitySemanticSnapshot(),
        )
        assertTrue(
            assertIs<LsiPrimitiveType>(
                kspIdentityFunctions.single { function -> function.name == "bounded" }
                    .typeParameters
                    .single()
                    .upperBounds
                    .single()
            ).boxed
        )
        assertTrue(
            assertIs<LsiPrimitiveType>(
                kspIdentityFunctions.single { function -> function.name == "receiver" }.receiverType
            ).boxed
        )

        val operations = aptSchema.services.single().operations.associateBy(ClientOperation::name)
        val byRoot = requireNotNull(operations["byRoot"])
        val byAlpha = requireNotNull(operations["byAlpha"])
        val count = requireNotNull(operations["count"])
        assertEquals(
            listOf(BETA_EXCEPTION_ID, GAMMA_EXCEPTION_ID, ALPHA_EXCEPTION_ID),
            byRoot.exceptionTypeIds,
        )
        assertEquals(listOf(ALPHA_EXCEPTION_ID), byAlpha.exceptionTypeIds)
        assertEquals(
            listOf(BETA_EXCEPTION_ID, BRANCH_EXCEPTION_ID),
            byRoot.exceptionMetadata.single { metadata -> metadata.typeId == ROOT_EXCEPTION_ID }.subTypeIds,
        )
        assertEquals(
            listOf(GAMMA_EXCEPTION_ID, ALPHA_EXCEPTION_ID),
            byRoot.exceptionMetadata.single { metadata -> metadata.typeId == BRANCH_EXCEPTION_ID }.subTypeIds,
        )
        assertEquals(byRoot.exceptionMetadata, byAlpha.exceptionMetadata)
        assertEquals(1, count.parameters.size)
    }

    private fun compileJava(sources: Map<String, String>): LsiWorkspace {
        val projectDir = createTempDirectory(prefix = "jimmer-client-apt-parity").toFile()
        val sourceDir = projectDir.resolve("src/main/java")
        val classesDir = projectDir.resolve("build/classes")
        sources.forEach { (path, content) ->
            val sourceFile = sourceDir.resolve(path)
            sourceFile.parentFile.mkdirs()
            sourceFile.writeText(content)
        }
        classesDir.mkdirs()
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("APT parity tests require a JDK compiler")
        val processor = CapturingAptProcessor()
        val sourceFiles = sourceDir.walkTopDown()
            .filter { file -> file.isFile && file.extension == "java" }
            .toList()
        val success = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8).use { fileManager ->
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(classesDir))
            val task = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                listOf("-proc:only", "-classpath", testClasspath()),
                null,
                fileManager.getJavaFileObjectsFromFiles(sourceFiles),
            )
            task.setProcessors(listOf(processor))
            task.call()
        }
        assertTrue(success, diagnostics.toErrorMessage())
        return processor.workspaces.single { workspace -> workspace.declarations.isNotEmpty() }
    }

    private fun LsiWorkspace.identitySemanticSnapshot(): List<String> {
        return toSemanticSnapshot()
            .lineSequence()
            .filter { line -> line.startsWith("function|") }
            .filter { line -> "|type:demo.CallableIdentity|" in line }
            .filterNot { line -> "/function:receiver(" in line }
            .sorted()
            .toList()
    }

    private fun compileKotlin(sources: Map<String, String>): LsiWorkspace {
        val projectDir = createTempDirectory(prefix = "jimmer-client-ksp-parity").toFile()
        val sourceDir = projectDir.resolve("src/main/kotlin")
        val sourceFiles = sources.map { (path, content) ->
            sourceDir.resolve(path).also { sourceFile ->
                sourceFile.parentFile.mkdirs()
                sourceFile.writeText(content)
            }
        }
        val outputDir = projectDir.resolve("build/ksp").apply(File::mkdirs)
        val provider = CapturingKspProvider()
        val logger = CollectingKspLogger()
        val config = KSPJvmConfig.Builder().apply {
            moduleName = "client-frontend-parity"
            sourceRoots = sourceFiles
            libraries = testClasspathFiles()
            projectBaseDir = projectDir
            outputBaseDir = outputDir
            cachesDir = outputDir.resolve("caches").apply(File::mkdirs)
            classOutputDir = outputDir.resolve("classes").apply(File::mkdirs)
            kotlinOutputDir = outputDir.resolve("kotlin").apply(File::mkdirs)
            javaOutputDir = outputDir.resolve("java").apply(File::mkdirs)
            resourceOutputDir = outputDir.resolve("resources").apply(File::mkdirs)
            jdkHome = File(System.getProperty("java.home"))
            jvmTarget = "17"
            languageVersion = "2.1"
            apiVersion = "2.1"
        }.build()
        val exitCode = KotlinSymbolProcessing(config, listOf(provider), logger).execute()

        assertEquals(KotlinSymbolProcessing.ExitCode.OK, exitCode, logger.messages.joinToString("\n"))
        return provider.workspaces.single { workspace -> workspace.declarations.isNotEmpty() }
    }

    private fun testClasspath(): String = testClasspathFiles().joinToString(File.pathSeparator)

    private fun testClasspathFiles(): List<File> {
        return System.getProperty("java.class.path")
            .split(File.pathSeparator)
            .map(::File)
            .filter(File::exists)
    }

    private fun DiagnosticCollector<JavaFileObject>.toErrorMessage(): String {
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

    private class CollectingKspLogger : KSPLogger {
        val messages = mutableListOf<String>()

        override fun logging(message: String, symbol: KSNode?) {
            messages += "LOG: $message"
        }

        override fun info(message: String, symbol: KSNode?) {
            messages += "INFO: $message"
        }

        override fun warn(message: String, symbol: KSNode?) {
            messages += "WARN: $message"
        }

        override fun error(message: String, symbol: KSNode?) {
            messages += "ERROR: $message"
        }

        override fun exception(exception: Throwable) {
            throw exception
        }
    }

    private companion object {
        val ROOT_EXCEPTION_ID = LsiSymbolId.type("demo.RootException")
        val BRANCH_EXCEPTION_ID = LsiSymbolId.type("demo.BranchException")
        val ALPHA_EXCEPTION_ID = LsiSymbolId.type("demo.AlphaException")
        val BETA_EXCEPTION_ID = LsiSymbolId.type("demo.BetaException")
        val GAMMA_EXCEPTION_ID = LsiSymbolId.type("demo.GammaException")

        val JAVA_SOURCES = mapOf(
            "demo/RootException.java" to """
                package demo;

                import org.babyfish.jimmer.ClientException;
                import org.babyfish.jimmer.error.CodeBasedException;

                @ClientException(
                    family = "DEMO",
                    subTypes = {BetaException.class, BranchException.class}
                )
                public abstract class RootException extends CodeBasedException {}
            """.trimIndent(),
            "demo/BranchException.java" to """
                package demo;

                import org.babyfish.jimmer.ClientException;

                @ClientException(subTypes = {GammaException.class, AlphaException.class})
                public abstract class BranchException extends RootException {}
            """.trimIndent(),
            "demo/AlphaException.java" to """
                package demo;

                import org.babyfish.jimmer.ClientException;

                @ClientException(code = "ALPHA")
                public final class AlphaException extends BranchException {}
            """.trimIndent(),
            "demo/BetaException.java" to """
                package demo;

                import org.babyfish.jimmer.ClientException;

                @ClientException(code = "BETA")
                public final class BetaException extends RootException {}
            """.trimIndent(),
            "demo/GammaException.java" to """
                package demo;

                import org.babyfish.jimmer.ClientException;

                @ClientException(code = "GAMMA")
                public final class GammaException extends BranchException {}
            """.trimIndent(),
            "demo/ErrorService.java" to """
                package demo;

                import java.io.Serializable;
                import java.util.List;
                import org.babyfish.jimmer.client.meta.Api;

                @Api
                public interface ErrorService {

                    @Api
                    int byRoot() throws RootException;

                    @Api
                    int byAlpha() throws AlphaException;

                    @Api
                    int count(List<String> values);

                    @Api
                    int raw(int value);

                    @Api
                    int boxed(Integer value);

                    @Api
                    int primitiveArray(int[] values);

                    @Api
                    int boxedArray(Integer[] values);

                    @Api
                    int generic(List<Integer> values);
                }
            """.trimIndent(),
            "demo/CallableIdentity.java" to """
                package demo;

                import java.io.Serializable;
                import java.util.List;

                public interface CallableIdentity {
                    void unit(kotlin.Unit value);
                    void voidValue(Void value);
                    void unitArray(kotlin.Unit[] values);
                    void voidArray(Void[] values);
                    void unitGeneric(List<kotlin.Unit> values);
                    void voidGeneric(List<Void> values);
                    void unitVararg(kotlin.Unit... values);
                    void receiver(kotlin.Unit receiver);
                    void unitReturn();
                    Void boxedVoidReturn(int marker);
                    <T extends Integer> void bounded(T value);
                    <T extends Comparable<T>> void comparable(T value);
                    <T extends Object & Serializable> void ordered(T value);
                    <T extends Serializable> void ordered(T value);
                }
            """.trimIndent(),
        )

        val KOTLIN_SOURCES = mapOf(
            "demo/Exceptions.kt" to """
                package demo

                import org.babyfish.jimmer.ClientException
                import org.babyfish.jimmer.error.CodeBasedException

                @ClientException(
                    family = "DEMO",
                    subTypes = [BetaException::class, BranchException::class],
                )
                abstract class RootException : CodeBasedException()

                @ClientException(subTypes = [GammaException::class, AlphaException::class])
                abstract class BranchException : RootException()

                @ClientException(code = "ALPHA")
                class AlphaException : BranchException()

                @ClientException(code = "BETA")
                class BetaException : RootException()

                @ClientException(code = "GAMMA")
                class GammaException : BranchException()
            """.trimIndent(),
            "demo/ErrorService.kt" to """
                package demo

                import org.babyfish.jimmer.client.meta.Api

                @Api
                interface ErrorService {

                    @Api
                    @Throws(RootException::class)
                    fun byRoot(): Int

                    @Api
                    @Throws(AlphaException::class)
                    fun byAlpha(): Int

                    @Api
                    fun count(values: MutableList<String>): Int

                    @Api
                    fun raw(value: Int): Int

                    @Api
                    fun boxed(value: Int?): Int

                    @Api
                    fun primitiveArray(values: IntArray): Int

                    @Api
                    fun boxedArray(values: Array<Int>): Int

                    @Api
                    fun generic(values: List<Int>): Int
                }
            """.trimIndent(),
            "demo/CallableIdentity.kt" to """
                package demo

                interface CallableIdentity {
                    fun unit(value: Unit)
                    fun voidValue(value: java.lang.Void)
                    fun unitArray(values: Array<Unit>)
                    fun voidArray(values: Array<java.lang.Void>)
                    fun unitGeneric(values: List<Unit>)
                    fun voidGeneric(values: List<java.lang.Void>)
                    fun unitVararg(vararg values: Unit)
                    fun Unit.receiver()
                    fun unitReturn(): Unit
                    fun boxedVoidReturn(marker: Int): java.lang.Void
                    fun <T : Int> bounded(value: T)
                    fun <T : Comparable<T>> comparable(value: T)
                    fun <T> ordered(value: T) where T : Any, T : java.io.Serializable
                    fun <T : java.io.Serializable> ordered(value: T)
                }
            """.trimIndent(),
        )
    }
}
