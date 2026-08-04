package site.addzero.lsi.ksp

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
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiFunction
import site.addzero.lsi.model.LsiConstructor
import site.addzero.lsi.model.LsiFrontendOptions
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiWorkspace

class KspJavaDeclarationFactsTest {

    @Test
    fun `real java source freezes member class record and concrete sealed facts`() {
        val projectDir = createTempDirectory(prefix = "lsi-ksp-java-facts").toFile()
        val kotlinSource = projectDir.resolve("src/main/kotlin/demo/Anchor.kt").also { file ->
            file.parentFile.mkdirs()
            file.writeText("package demo\nfun anchor() = Unit")
        }
        val javaSource = projectDir.resolve("src/main/java/demo/JavaTypes.java").also { file ->
            file.parentFile.mkdirs()
            file.writeText(JAVA_SOURCE)
        }
        val outputDir = projectDir.resolve("build/ksp").apply(File::mkdirs)
        val provider = CapturingProvider()
        val logger = CapturingLogger()
        val configuration = KSPJvmConfig.Builder().apply {
            moduleName = "java-declaration-facts"
            sourceRoots = listOf(kotlinSource)
            javaSourceRoots = listOf(javaSource)
            libraries = runtimeClasspath()
            projectBaseDir = projectDir
            outputBaseDir = outputDir
            cachesDir = outputDir.resolve("caches").apply(File::mkdirs)
            classOutputDir = outputDir.resolve("classes").apply(File::mkdirs)
            javaOutputDir = outputDir.resolve("java").apply(File::mkdirs)
            kotlinOutputDir = outputDir.resolve("kotlin").apply(File::mkdirs)
            resourceOutputDir = outputDir.resolve("resources").apply(File::mkdirs)
            languageVersion = "2.1"
            apiVersion = "2.1"
            jvmTarget = "17"
            jdkHome = File(System.getProperty("java.home"))
        }.build()

        val exitCode = KotlinSymbolProcessing(configuration, listOf(provider), logger).execute()

        assertEquals(KotlinSymbolProcessing.ExitCode.OK, exitCode, logger.messages.joinToString("\n"))
        val workspace = provider.workspaces.single { candidate -> candidate.declarations.isNotEmpty() }
        assertTrue(workspace.type("demo.JavaTypes.Inner").requiresEnclosingInstance)
        assertFalse(workspace.type("demo.JavaTypes.StaticNested").requiresEnclosingInstance)
        assertFalse(workspace.type("demo.JavaTypes.NestedRecord").requiresEnclosingInstance)
        assertEquals(LsiTypeDeclarationKind.RECORD, workspace.type("demo.JavaTypes.NestedRecord").kind)
        assertTrue(workspace.type("demo.JavaEnum.Inner").requiresEnclosingInstance)
        assertFalse(workspace.type("demo.JavaInterface.Nested").requiresEnclosingInstance)
        assertFalse(workspace.type("demo.JavaTypes").abstractDeclaration)
        val ownerId = LsiSymbolId.type("demo.JavaTypes")
        assertEquals(
            LsiSymbolId.constructor(
                ownerId,
                listOf("parameter:method:<init>:0:type:java.lang.CharSequence"),
            ),
            workspace.declarationsOfType<LsiConstructor>()
                .single { constructor ->
                    constructor.ownerId == ownerId && constructor.parameters.isNotEmpty()
                }
                .id,
        )
        val functionIds = workspace.declarationsOfType<LsiFunction>()
            .filter { function -> function.ownerId == ownerId }
            .mapTo(linkedSetOf(), LsiFunction::id)
        assertEquals(
            setOf(
                LsiSymbolId.function(ownerId, "raw", listOf("primitive:int")),
                LsiSymbolId.function(ownerId, "boxed", listOf("type:java.lang.Integer")),
                LsiSymbolId.function(ownerId, "primitiveArray", listOf("array:primitive:int")),
                LsiSymbolId.function(ownerId, "boxedArray", listOf("array:type:java.lang.Integer")),
                LsiSymbolId.function(
                    ownerId,
                    "generic",
                    listOf("type:java.util.List<type:java.lang.Integer>"),
                ),
                LsiSymbolId.function(
                    ownerId,
                    "vararg",
                    listOf("array:type:java.lang.Integer"),
                ),
            ),
            functionIds,
        )
        val vararg = workspace.declarationsOfType<LsiFunction>()
            .single { function -> function.name == "vararg" }
            .parameters
            .single()
        assertTrue(vararg.vararg)
        val varargElementType = assertIs<LsiPrimitiveType>(vararg.type)
        assertEquals(LsiPrimitiveKind.INT, varargElementType.kind)
        assertTrue(varargElementType.boxed)
    }

    private class CapturingProvider : SymbolProcessorProvider {
        val workspaces = mutableListOf<LsiWorkspace>()

        override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
            return object : SymbolProcessor {
                override fun process(resolver: Resolver): List<KSAnnotated> {
                    workspaces += resolver.toLsiWorkspace(LsiFrontendOptions())
                    return emptyList()
                }
            }
        }
    }

    private class CapturingLogger : KSPLogger {
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

    private fun LsiWorkspace.type(qualifiedName: String): LsiTypeDeclaration {
        return assertIs(this[LsiSymbolId.type(qualifiedName)])
    }

    private companion object {
        val JAVA_SOURCE = """
            package demo;

            import java.util.List;

            public sealed class JavaTypes permits JavaTypes.Child {
                JavaTypes() {}
                <T extends CharSequence> JavaTypes(T value) {}
                void raw(int value) {}
                void boxed(Integer value) {}
                void primitiveArray(int[] values) {}
                void boxedArray(Integer[] values) {}
                void generic(List<Integer> values) {}
                void vararg(Integer... values) {}
                public final class Inner {}
                public static final class StaticNested {}
                public record NestedRecord(String value) {}
                public static final class Child extends JavaTypes {}
            }

            enum JavaEnum {
                VALUE;
                final class Inner {}
            }

            interface JavaInterface {
                class Nested {}
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
