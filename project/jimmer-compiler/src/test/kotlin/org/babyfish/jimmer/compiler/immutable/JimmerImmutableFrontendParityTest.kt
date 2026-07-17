package org.babyfish.jimmer.compiler.immutable

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
import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.babyfish.jimmer.compiler.lsi.LsiFrontendOptions
import org.babyfish.jimmer.compiler.lsi.apt.toLsiWorkspace
import org.babyfish.jimmer.compiler.lsi.ksp.toLsiWorkspace
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiWorkspace

class JimmerImmutableFrontendParityTest {

    @Test
    fun `real apt and ksp frontends produce identical inheritance metadata`() {
        val apt = compileApt(VALID_JAVA_SOURCE)
        val ksp = compileKsp(VALID_KOTLIN_SOURCE)

        assertNull(apt.diagnostic)
        assertNull(ksp.diagnostic)
        val aptSchema = assertNotNull(apt.schema)
        val kspSchema = assertNotNull(ksp.schema)
        assertEquals(aptSchema.normalizedSnapshot(), kspSchema.normalizedSnapshot())
        assertEquals(aptSchema.fingerprint(), kspSchema.fingerprint())

        val root = aptSchema.types.single { type -> type.qualifiedName == "demo.Asset" }
        assertEquals(JimmerInheritanceStrategy.JOINED, root.inheritanceStrategy)
        assertEquals(JimmerJoinedTableDissociateAction.LAX, root.joinedTableDissociateAction)
        assertEquals(LsiSymbolId.type("demo.Asset"), root.inheritanceRootTypeId)
        assertFalse(root.instantiable)
        assertEquals(LsiSymbolId.property(root.id, "kind"), root.discriminatorPropId)

        val derived = aptSchema.types.single { type -> type.qualifiedName == "demo.Book" }
        assertEquals(root.id, derived.inheritanceRootTypeId)
        assertEquals("BOOK", derived.discriminatorValue)
        assertTrue(derived.instantiable)
        assertEquals(LsiSymbolId.property(derived.id, "kind"), derived.discriminatorPropId)
        assertTrue(derived.props.any { prop ->
            prop.name == "createdBy" && prop.inherited
        })
    }

    @Test
    fun `real apt and ksp frontends report identical invalid discriminator diagnostic`() {
        val apt = compileApt(INVALID_JAVA_SOURCE)
        val ksp = compileKsp(INVALID_KOTLIN_SOURCE)

        assertNull(apt.schema)
        assertNull(ksp.schema)
        assertEquals(apt.diagnostic, ksp.diagnostic)
        assertEquals(
            "Immutable property 'type:demo.ModelBase/property:kind' decorated by " +
                "@type:org.babyfish.jimmer.sql.Discriminator must be a scalar string or enum property",
            apt.diagnostic,
        )
    }

    private fun compileApt(source: String): FrontendResult {
        val projectDir = createTempDirectory(prefix = "jimmer-immutable-apt-parity").toFile()
        val sourceFile = projectDir.resolve("src/main/java/demo/Models.java").also { file ->
            file.parentFile.mkdirs()
            file.writeText(source)
        }
        val classesDir = projectDir.resolve("build/classes").apply(File::mkdirs)
        val generatedDir = projectDir.resolve("build/generated").apply(File::mkdirs)
        val capture = FrontendCapture()
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("APT frontend parity tests require a JDK compiler")
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
                    System.getProperty("java.class.path"),
                ),
                null,
                fileManager.getJavaFileObjects(sourceFile),
            )
            task.setProcessors(listOf(ImmutableSnapshotAptProcessor(capture)))
            task.call()
        }
        val frontendResult = capture.result()
        if (frontendResult.diagnostic == null) {
            assertTrue(success, diagnostics.diagnostics.joinToString("\n"))
        } else {
            assertFalse(success, diagnostics.diagnostics.joinToString("\n"))
            assertTrue(diagnostics.diagnostics.any { diagnostic ->
                diagnostic.kind == Diagnostic.Kind.ERROR &&
                    diagnostic.getMessage(null) == frontendResult.diagnostic
            })
        }
        return frontendResult
    }

    private fun compileKsp(source: String): FrontendResult {
        val projectDir = createTempDirectory(prefix = "jimmer-immutable-ksp-parity").toFile()
        val sourceFile = projectDir.resolve("src/main/kotlin/demo/Models.kt").also { file ->
            file.parentFile.mkdirs()
            file.writeText(source)
        }
        val outputDir = projectDir.resolve("build/ksp").apply(File::mkdirs)
        val capture = FrontendCapture()
        val logger = CapturingKspLogger()
        val configuration = KSPJvmConfig.Builder().apply {
            moduleName = "immutable-frontend-parity"
            sourceRoots = listOf(sourceFile)
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
        val exitCode = KotlinSymbolProcessing(
            configuration,
            listOf(ImmutableSnapshotKspProvider(capture)),
            logger,
        ).execute()
        val frontendResult = capture.result()
        if (frontendResult.diagnostic == null) {
            assertEquals(KotlinSymbolProcessing.ExitCode.OK, exitCode, logger.messages.joinToString("\n"))
        } else {
            assertEquals(
                KotlinSymbolProcessing.ExitCode.PROCESSING_ERROR,
                exitCode,
                logger.messages.joinToString("\n"),
            )
            assertTrue(logger.errors.contains(frontendResult.diagnostic))
        }
        return frontendResult
    }

    private class ImmutableSnapshotAptProcessor(
        private val capture: FrontendCapture,
    ) : AbstractProcessor() {

        override fun getSupportedAnnotationTypes(): Set<String> = setOf("*")

        override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()

        override fun process(
            annotations: Set<TypeElement>,
            roundEnvironment: RoundEnvironment,
        ): Boolean {
            if (roundEnvironment.processingOver() || capture.completed) {
                return false
            }
            val workspace = roundEnvironment.toLsiWorkspace(
                processingEnv,
                LsiFrontendOptions.from(emptyMap()),
            )
            capture.freeze(workspace)
            capture.diagnostic?.let { diagnostic ->
                processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, diagnostic)
            }
            return false
        }
    }

    private class ImmutableSnapshotKspProvider(
        private val capture: FrontendCapture,
    ) : SymbolProcessorProvider {

        override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
            return object : SymbolProcessor {
                override fun process(resolver: Resolver): List<KSAnnotated> {
                    if (capture.completed) {
                        return emptyList()
                    }
                    val workspace = resolver.toLsiWorkspace(LsiFrontendOptions.from(emptyMap()))
                    capture.freeze(workspace)
                    capture.diagnostic?.let { diagnostic ->
                        environment.logger.error(diagnostic)
                    }
                    return emptyList()
                }
            }
        }
    }

    private class FrontendCapture {
        var schema: JimmerImmutableSchema? = null
            private set

        var diagnostic: String? = null
            private set

        var completed: Boolean = false
            private set

        fun freeze(workspace: LsiWorkspace) {
            try {
                schema = JimmerImmutablePrecompiler().compile(workspace)
            } catch (exception: JimmerImmutablePrecompileException) {
                diagnostic = exception.message
            }
            completed = true
        }

        fun result(): FrontendResult {
            check(completed) { "Frontend did not freeze an LSI workspace" }
            return FrontendResult(schema, diagnostic)
        }
    }

    private class CapturingKspLogger : KSPLogger {
        val messages = mutableListOf<String>()

        val errors = mutableListOf<String>()

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
            errors += message
        }

        override fun exception(exception: Throwable) {
            throw exception
        }
    }

    private data class FrontendResult(
        val schema: JimmerImmutableSchema?,
        val diagnostic: String?,
    )

    private companion object {
        fun runtimeClasspath(): List<File> {
            return System.getProperty("java.class.path")
                .split(File.pathSeparator)
                .map(::File)
                .filter(File::exists)
        }

        val VALID_JAVA_SOURCE = """
            package demo;

            import org.babyfish.jimmer.sql.Discriminator;
            import org.babyfish.jimmer.sql.DiscriminatorValue;
            import org.babyfish.jimmer.sql.Entity;
            import org.babyfish.jimmer.sql.Id;
            import org.babyfish.jimmer.sql.Inheritance;
            import org.babyfish.jimmer.sql.InheritanceType;
            import org.babyfish.jimmer.sql.JoinedTableDissociateAction;
            import org.babyfish.jimmer.sql.MappedSuperclass;

            @MappedSuperclass
            interface ModelBase<T> {
                @Id
                String id();

                @Discriminator
                String kind();

                T createdBy();
            }

            @Entity
            @Inheritance(
                strategy = InheritanceType.JOINED,
                joinedTableDissociateAction = JoinedTableDissociateAction.LAX
            )
            interface Asset extends ModelBase<String> {
                String name();
            }

            @Entity
            @DiscriminatorValue("BOOK")
            interface Book extends Asset {
                String isbn();
            }
        """.trimIndent()

        val VALID_KOTLIN_SOURCE = """
            package demo

            import org.babyfish.jimmer.sql.Discriminator
            import org.babyfish.jimmer.sql.DiscriminatorValue
            import org.babyfish.jimmer.sql.Entity
            import org.babyfish.jimmer.sql.Id
            import org.babyfish.jimmer.sql.Inheritance
            import org.babyfish.jimmer.sql.InheritanceType
            import org.babyfish.jimmer.sql.JoinedTableDissociateAction
            import org.babyfish.jimmer.sql.MappedSuperclass

            @MappedSuperclass
            interface ModelBase<T : Any> {
                @Id
                val id: String

                @Discriminator
                val kind: String

                val createdBy: T
            }

            @Entity
            @Inheritance(
                strategy = InheritanceType.JOINED,
                joinedTableDissociateAction = JoinedTableDissociateAction.LAX,
            )
            interface Asset : ModelBase<String> {
                val name: String
            }

            @Entity
            @DiscriminatorValue("BOOK")
            interface Book : Asset {
                val isbn: String
            }
        """.trimIndent()

        val INVALID_JAVA_SOURCE = VALID_JAVA_SOURCE
            .replace("String kind();", "int kind();")

        val INVALID_KOTLIN_SOURCE = VALID_KOTLIN_SOURCE
            .replace("val kind: String", "val kind: Int")
    }
}
