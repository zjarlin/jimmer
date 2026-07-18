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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
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
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiWorkspace

class JimmerImmutableFrontendParityTest {

    @Test
    fun `binary draft documentation produces identical immutable fingerprints`() {
        val apt = compileApt(
            source = """
                package demo;

                interface Consumer {
                    demo.binary.BinaryBook book();
                }
            """.trimIndent(),
            libraries = listOf(compileJavaDocumentationLibrary()),
        )
        val ksp = compileKsp(
            source = """
                package demo

                interface Consumer {
                    val book: demo.binary.BinaryBook
                }
            """.trimIndent(),
            libraries = listOf(compileKotlinDocumentationLibrary()),
        )

        assertNull(apt.diagnostic)
        assertNull(ksp.diagnostic)
        val aptSchema = assertNotNull(apt.schema)
        val kspSchema = assertNotNull(ksp.schema)
        val aptBook = aptSchema.types.single { type -> type.qualifiedName == "demo.binary.BinaryBook" }
        val kspBook = kspSchema.types.single { type -> type.qualifiedName == "demo.binary.BinaryBook" }
        assertEquals("binary type", aptBook.documentation)
        assertEquals("binary property", aptBook.props.single { prop -> prop.name == "name" }.documentation)
        assertEquals(aptBook.documentation, kspBook.documentation)
        assertEquals(
            aptBook.props.single { prop -> prop.name == "name" }.documentation,
            kspBook.props.single { prop -> prop.name == "name" }.documentation,
        )
        assertEquals(aptSchema.normalizedSnapshot(), kspSchema.normalizedSnapshot())
        assertEquals(aptSchema.fingerprint(), kspSchema.fingerprint())
    }

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
        val catalogBook = aptSchema.types.single { type -> type.qualifiedName == "demo.CatalogBook" }
        val storeProp = catalogBook.props.single { prop -> prop.name == "store" }
        val storeIdProp = catalogBook.props.single { prop -> prop.name == "storeId" }
        assertEquals(
            JimmerImmutableView.Id(
                basePropId = storeProp.id,
                targetIdPropId = LsiSymbolId.property(LsiSymbolId.type("demo.Store"), "id"),
            ),
            storeIdProp.view,
        )
        val authorsProp = catalogBook.props.single { prop -> prop.name == "authors" }
        assertEquals(
            JimmerImmutableView.ManyToMany(
                basePropId = LsiSymbolId.property(catalogBook.id, "links"),
                deeperPropId = LsiSymbolId.property(LsiSymbolId.type("demo.BookAuthor"), "author"),
            ),
            authorsProp.view,
        )
        val authorIdsProp = catalogBook.props.single { prop -> prop.name == "authorIds" }
        assertEquals(
            JimmerImmutableView.Id(
                basePropId = authorsProp.id,
                targetIdPropId = LsiSymbolId.property(LsiSymbolId.type("demo.Author"), "id"),
            ),
            authorIdsProp.view,
        )
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

    @Test
    fun `real apt and ksp frontends report identical invalid id view diagnostic`() {
        val apt = compileApt(INVALID_VIEW_JAVA_SOURCE)
        val ksp = compileKsp(INVALID_VIEW_KOTLIN_SOURCE)

        assertNull(apt.schema)
        assertNull(ksp.schema)
        assertEquals(apt.diagnostic, ksp.diagnostic)
        assertEquals(
            "Immutable view property 'type:demo.Book/property:storeId' list category does not match " +
                "id-view base property 'store'",
            apt.diagnostic,
        )
    }

    @Test
    fun `real apt and ksp frontends report identical primary mapping conflict`() {
        val apt = compileApt(INVALID_PRIMARY_MAPPING_JAVA_SOURCE)
        val ksp = compileKsp(INVALID_PRIMARY_MAPPING_KOTLIN_SOURCE)

        assertNull(apt.schema)
        assertNull(ksp.schema)
        assertEquals(apt.diagnostic, ksp.diagnostic)
        assertEquals(
            "Immutable property 'type:demo.Book/property:storeId' cannot declare multiple primary mapping " +
                "annotations: @type:org.babyfish.jimmer.sql.IdView, " +
                "@type:org.babyfish.jimmer.sql.Transient",
            apt.diagnostic,
        )
    }

    @Test
    fun `real apt and ksp frontends report identical id view element nullability`() {
        val apt = compileApt(INVALID_VIEW_ELEMENT_NULLABILITY_JAVA_SOURCE)
        val ksp = compileKsp(INVALID_VIEW_ELEMENT_NULLABILITY_KOTLIN_SOURCE)

        assertNull(apt.schema)
        assertNull(ksp.schema)
        assertEquals(apt.diagnostic, ksp.diagnostic)
        assertEquals(
            "Immutable view property 'type:demo.Book/property:authorIds' type does not match id 'id' " +
                "of association target 'demo.Author'",
            apt.diagnostic,
        )
    }

    private fun compileApt(
        source: String,
        libraries: List<File> = emptyList(),
    ): FrontendResult {
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
                    (libraries + runtimeClasspath())
                        .distinct()
                        .joinToString(File.pathSeparator, transform = File::getAbsolutePath),
                ),
                null,
                fileManager.getJavaFileObjects(sourceFile),
            )
            task.setProcessors(listOf(ImmutableSnapshotAptProcessor(capture)))
            task.call()
        }
        check(capture.completed) {
            "APT frontend did not freeze an LSI workspace:\n" +
                diagnostics.diagnostics.joinToString("\n") { diagnostic -> diagnostic.getMessage(null) }
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

    private fun compileKsp(
        source: String,
        libraries: List<File> = emptyList(),
    ): FrontendResult {
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
            this.libraries = (libraries + runtimeClasspath()).distinct()
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

    private fun compileJavaDocumentationLibrary(): File {
        val projectDir = createTempDirectory(prefix = "jimmer-immutable-java-doc-library").toFile()
        val sourceDir = projectDir.resolve("src/main/java/demo/binary")
        val bookSource = sourceDir.resolve("BinaryBook.java").also { file ->
            file.parentFile.mkdirs()
            file.writeText(
                """
                    package demo.binary;

                    import org.babyfish.jimmer.sql.Entity;
                    import org.babyfish.jimmer.sql.Id;

                    @Entity
                    public interface BinaryBook {
                        @Id
                        long id();

                        String name();
                    }
                """.trimIndent()
            )
        }
        val draftSource = sourceDir.resolve("BinaryBookDraft.java").also { file ->
            file.writeText(
                """
                    package demo.binary;

                    import org.babyfish.jimmer.client.Description;

                    public interface BinaryBookDraft {
                        class Producer {
                            @Description("binary type")
                            public static class Impl {
                                @Description("binary property")
                                public String name() {
                                    return "";
                                }
                            }
                        }
                    }
                """.trimIndent()
            )
        }
        val output = projectDir.resolve("build/classes").apply(File::mkdirs)
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("Immutable documentation parity requires a JDK compiler")
        val success = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8).use { fileManager ->
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(output))
            compiler.getTask(
                null,
                fileManager,
                diagnostics,
                listOf(
                    "-proc:none",
                    "-classpath",
                    runtimeClasspath().joinToString(File.pathSeparator, transform = File::getAbsolutePath),
                ),
                null,
                fileManager.getJavaFileObjects(bookSource, draftSource),
            ).call()
        }
        assertTrue(
            success,
            diagnostics.diagnostics.joinToString("\n") { diagnostic -> diagnostic.getMessage(null) },
        )
        return output
    }

    private fun compileKotlinDocumentationLibrary(): File {
        val projectDir = createTempDirectory(prefix = "jimmer-immutable-kotlin-doc-library").toFile()
        val source = projectDir.resolve("src/main/kotlin/demo/binary/BinaryBook.kt").also { file ->
            file.parentFile.mkdirs()
            file.writeText(
                """
                    package demo.binary

                    import org.babyfish.jimmer.client.Description
                    import org.babyfish.jimmer.sql.Entity
                    import org.babyfish.jimmer.sql.Id

                    @Entity
                    interface BinaryBook {
                        @get:Id
                        val id: Long

                        val name: String
                    }

                    interface BinaryBookDraft {
                        class `${'$'}` {
                            @Description("binary type")
                            class Impl {
                                @Description("binary property")
                                val name: String
                                    get() = ""
                            }
                        }
                    }
                """.trimIndent()
            )
        }
        val output = projectDir.resolve("build/classes").apply(File::mkdirs)
        val messages = ByteArrayOutputStream()
        val exitCode = PrintStream(messages, true, StandardCharsets.UTF_8).use { stream ->
            K2JVMCompiler().exec(
                stream,
                "-no-stdlib",
                "-no-reflect",
                "-classpath",
                runtimeClasspath().joinToString(File.pathSeparator, transform = File::getAbsolutePath),
                "-d",
                output.absolutePath,
                source.absolutePath,
            )
        }
        assertEquals(ExitCode.OK, exitCode, messages.toString(StandardCharsets.UTF_8))
        return output
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
            import org.babyfish.jimmer.sql.IdView;
            import org.babyfish.jimmer.sql.JoinedTableDissociateAction;
            import org.babyfish.jimmer.sql.MappedSuperclass;
            import org.babyfish.jimmer.sql.ManyToOne;
            import org.babyfish.jimmer.sql.ManyToManyView;
            import org.babyfish.jimmer.sql.OneToMany;
            import java.util.List;

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

            @Entity
            interface Store {
                @Id
                long id();
            }

            @Entity
            interface Author {
                @Id
                long id();
            }

            @Entity
            interface BookAuthor {
                @Id
                long id();

                @ManyToOne
                Author author();

                @ManyToOne
                CatalogBook book();
            }

            @Entity
            interface CatalogBook {
                @Id
                long id();

                @ManyToOne
                Store store();

                @IdView
                long storeId();

                @OneToMany(mappedBy = "book")
                List<BookAuthor> links();

                @ManyToManyView(prop = "links")
                List<Author> authors();

                @IdView("authors")
                List<Long> authorIds();
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
            import org.babyfish.jimmer.sql.IdView
            import org.babyfish.jimmer.sql.JoinedTableDissociateAction
            import org.babyfish.jimmer.sql.MappedSuperclass
            import org.babyfish.jimmer.sql.ManyToOne
            import org.babyfish.jimmer.sql.ManyToManyView
            import org.babyfish.jimmer.sql.OneToMany

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

            @Entity
            interface Store {
                @Id
                val id: Long
            }

            @Entity
            interface Author {
                @Id
                val id: Long
            }

            @Entity
            interface BookAuthor {
                @Id
                val id: Long

                @ManyToOne
                val author: Author

                @ManyToOne
                val book: CatalogBook
            }

            @Entity
            interface CatalogBook {
                @Id
                val id: Long

                @ManyToOne
                val store: Store

                @IdView
                val storeId: Long

                @OneToMany(mappedBy = "book")
                val links: List<BookAuthor>

                @ManyToManyView(prop = "links")
                val authors: List<Author>

                @IdView("authors")
                val authorIds: List<Long>
            }
        """.trimIndent()

        val INVALID_JAVA_SOURCE = VALID_JAVA_SOURCE
            .replace("String kind();", "int kind();")

        val INVALID_KOTLIN_SOURCE = VALID_KOTLIN_SOURCE
            .replace("val kind: String", "val kind: Int")

        val INVALID_VIEW_JAVA_SOURCE = """
            package demo;

            import java.util.List;
            import org.babyfish.jimmer.sql.Entity;
            import org.babyfish.jimmer.sql.Id;
            import org.babyfish.jimmer.sql.IdView;
            import org.babyfish.jimmer.sql.ManyToOne;

            @Entity
            interface Store {
                @Id
                long id();
            }

            @Entity
            interface Book {
                @ManyToOne
                Store store();

                @IdView("store")
                List<Long> storeId();
            }
        """.trimIndent()

        val INVALID_VIEW_KOTLIN_SOURCE = """
            package demo

            import org.babyfish.jimmer.sql.Entity
            import org.babyfish.jimmer.sql.Id
            import org.babyfish.jimmer.sql.IdView
            import org.babyfish.jimmer.sql.ManyToOne

            @Entity
            interface Store {
                @Id
                val id: Long
            }

            @Entity
            interface Book {
                @ManyToOne
                val store: Store

                @IdView("store")
                val storeId: List<Long>
            }
        """.trimIndent()

        val INVALID_PRIMARY_MAPPING_JAVA_SOURCE = """
            package demo;

            import org.babyfish.jimmer.sql.Entity;
            import org.babyfish.jimmer.sql.Id;
            import org.babyfish.jimmer.sql.IdView;
            import org.babyfish.jimmer.sql.ManyToOne;
            import org.babyfish.jimmer.sql.Transient;

            @Entity
            interface Store {
                @Id
                long id();
            }

            @Entity
            interface Book {
                @ManyToOne
                Store store();

                @IdView
                @Transient
                long storeId();
            }
        """.trimIndent()

        val INVALID_PRIMARY_MAPPING_KOTLIN_SOURCE = """
            package demo

            import org.babyfish.jimmer.sql.Entity
            import org.babyfish.jimmer.sql.Id
            import org.babyfish.jimmer.sql.IdView
            import org.babyfish.jimmer.sql.ManyToOne
            import org.babyfish.jimmer.sql.Transient

            @Entity
            interface Store {
                @Id
                val id: Long
            }

            @Entity
            interface Book {
                @ManyToOne
                val store: Store

                @Transient
                @IdView
                val storeId: Long
            }
        """.trimIndent()

        val INVALID_VIEW_ELEMENT_NULLABILITY_JAVA_SOURCE = """
            package demo;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;
            import java.util.List;
            import org.babyfish.jimmer.sql.Entity;
            import org.babyfish.jimmer.sql.Id;
            import org.babyfish.jimmer.sql.IdView;
            import org.babyfish.jimmer.sql.ManyToMany;

            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.TYPE_USE)
            @interface Nullable {}

            @Entity
            interface Author {
                @Id
                long id();
            }

            @Entity
            interface Book {
                @ManyToMany
                List<Author> authors();

                @IdView("authors")
                List<@Nullable Long> authorIds();
            }
        """.trimIndent()

        val INVALID_VIEW_ELEMENT_NULLABILITY_KOTLIN_SOURCE = """
            package demo

            import org.babyfish.jimmer.sql.Entity
            import org.babyfish.jimmer.sql.Id
            import org.babyfish.jimmer.sql.IdView
            import org.babyfish.jimmer.sql.ManyToMany

            @Entity
            interface Author {
                @Id
                val id: Long
            }

            @Entity
            interface Book {
                @ManyToMany
                val authors: List<Author>

                @IdView("authors")
                val authorIds: List<Long?>
            }
        """.trimIndent()
    }
}
