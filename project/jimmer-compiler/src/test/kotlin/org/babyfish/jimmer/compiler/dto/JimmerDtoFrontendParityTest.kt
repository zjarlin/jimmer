package org.babyfish.jimmer.compiler.dto

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
import kotlin.test.assertTrue
import org.babyfish.jimmer.compiler.CompilerInputDocument
import org.babyfish.jimmer.compiler.CompilerInputDocumentKind
import org.babyfish.jimmer.compiler.CompilerPlatform
import org.babyfish.jimmer.compiler.CompilerSourceSet
import org.babyfish.jimmer.compiler.JimmerCompilerSourceFilter
import org.babyfish.jimmer.compiler.immutable.JimmerImmutablePrecompiler
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableSchema
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableView
import org.babyfish.jimmer.compiler.immutable.fingerprint
import org.babyfish.jimmer.compiler.immutable.normalizedSnapshot
import org.babyfish.jimmer.compiler.immutable.normalizedTypeSignature
import org.babyfish.jimmer.compiler.input.CompilerInputDocumentReferenceFreezer
import org.babyfish.jimmer.compiler.lsi.LsiFrontendOptions
import org.babyfish.jimmer.compiler.lsi.apt.toLsiWorkspace
import org.babyfish.jimmer.compiler.lsi.ksp.toLsiWorkspace
import org.babyfish.jimmer.dto.compiler.DtoModifier
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiWorkspace

class JimmerDtoFrontendParityTest {

    @Test
    fun `real apt and ksp frontends produce identical dto render contracts`() {
        val apt = compileFixture(compileApt(JAVA_SOURCE), CompilerPlatform.APT)
        val ksp = compileFixture(compileKsp(KOTLIN_SOURCE), CompilerPlatform.KSP)

        assertEquals(
            apt.immutableSchema.normalizedSnapshot(),
            ksp.immutableSchema.normalizedSnapshot(),
        )
        assertEquals(apt.immutableSchema.fingerprint(), ksp.immutableSchema.fingerprint())

        val aptDocument = apt.dtoSchema.documents.single()
        val kspDocument = ksp.dtoSchema.documents.single()
        assertEquals(aptDocument.renderGraph, kspDocument.renderGraph)
        assertEquals(
            aptDocument.annotationContract.typePlans,
            kspDocument.annotationContract.typePlans,
        )
        assertEquals(
            aptDocument.annotationContract.propPlans,
            kspDocument.annotationContract.propPlans,
        )
        assertEquals(
            aptDocument.interfaceContractResolution,
            kspDocument.interfaceContractResolution,
        )
        val aptConfigContract = aptDocument.configContractResolution.contracts.single()
        val kspConfigContract = kspDocument.configContractResolution.contracts.single()
        assertEquals(DtoConfigContractKind.FILTER, aptConfigContract.kind)
        assertEquals(AUTHOR_TABLE_ID, aptConfigContract.contractArgumentTypeId)
        assertEquals(AUTHOR_ID, kspConfigContract.contractArgumentTypeId)
        assertEquals(aptConfigContract.targetEntityTypeId, kspConfigContract.targetEntityTypeId)
        assertEquals(aptConfigContract.dependencyTypeIds, kspConfigContract.dependencyTypeIds)
        assertEquals(listOf(AUTHOR_ID, FILTER_ID), aptConfigContract.dependencyTypeIds)
        val aptMarkerDeclaration = aptDocument.annotationContract.declarationsByTypeId.getValue(MARKER_ID)
        val kspMarkerDeclaration = kspDocument.annotationContract.declarationsByTypeId.getValue(MARKER_ID)
        assertEquals(JimmerDtoAnnotationDeclarationKind.JAVA, aptMarkerDeclaration.kind)
        assertEquals(JimmerDtoAnnotationDeclarationKind.KOTLIN, kspMarkerDeclaration.kind)
        assertEquals(
            aptMarkerDeclaration.copy(kind = JimmerDtoAnnotationDeclarationKind.KOTLIN),
            kspMarkerDeclaration,
        )
        val canonicalAptSchema = JimmerDtoPrecompiledSchema(
            documents = listOf(
                aptDocument.copy(
                    annotationContract = aptDocument.annotationContract.copy(
                        declarations = aptDocument.annotationContract.declarations.map { declaration ->
                            if (declaration.typeId == MARKER_ID) {
                                declaration.copy(kind = JimmerDtoAnnotationDeclarationKind.KOTLIN)
                            } else {
                                declaration
                            }
                        },
                    ),
                ),
            ),
        )
        assertEquals(canonicalAptSchema.normalizedSnapshot(), ksp.dtoSchema.normalizedSnapshot())
        assertEquals(canonicalAptSchema.fingerprint(), ksp.dtoSchema.fingerprint())

        val bookType = apt.immutableSchema.typesById.getValue(BOOK_ID)
        val nameProp = bookType.props.single { prop -> prop.name == "name" }
        val ratingProp = bookType.props.single { prop -> prop.name == "rating" }
        val storeProp = bookType.props.single { prop -> prop.name == "store" }
        val storeIdProp = bookType.props.single { prop -> prop.name == "storeId" }
        assertTrue(nameProp.inherited)
        assertEquals("显示名称。", nameProp.documentation)
        val marker = nameProp.annotations.single { annotation -> annotation.type == MARKER_ID }
        assertEquals(
            LsiAnnotationValue.StringValue("base-name"),
            marker.arguments.getValue("value").value,
        )
        assertTrue(ratingProp.nullable)
        assertEquals("primitive:int?", ratingProp.type.normalizedTypeSignature())
        assertEquals(
            JimmerImmutableView.Id(
                basePropId = storeProp.id,
                targetIdPropId = LsiSymbolId.property(STORE_ID, "id"),
            ),
            storeIdProp.view,
        )

        val dtoType = aptDocument.renderGraph.typesById.getValue(
            aptDocument.renderGraph.rootTypeIds.single(),
        )
        val dtoProps = dtoType.propIds.map { propId ->
            aptDocument.renderGraph.propsById.getValue(propId)
        }
        assertEquals(listOf("id", "name", "rating", "storeId", "authors"), dtoProps.map { prop -> prop.name })
        assertTrue(dtoProps.single { prop -> prop.name == "rating" }.nullable)
        assertEquals(
            LsiSymbolId.property(BOOK_ID, "storeId"),
            (dtoProps.single { prop -> prop.name == "storeId" } as JimmerDtoBaseProp)
                .baseProps
                .single()
                .propId,
        )
    }

    private fun compileFixture(
        workspace: LsiWorkspace,
        platform: CompilerPlatform,
    ): CompiledFixture {
        val immutableSchema = JimmerImmutablePrecompiler().compile(workspace)
        val document = CompilerInputDocument(
            kind = CompilerInputDocumentKind.DTO,
            sourceSet = CompilerSourceSet.MAIN,
            projectName = "demo-project",
            sourceRoot = "src/main/dto",
            relativePath = "demo/Book.dto",
            content = DTO_SOURCE,
        )
        val inputSnapshot = CompilerInputDocumentReferenceFreezer().freeze(document)
        val outcome = JimmerDtoPrecompiler().compile(
            inputDocumentSnapshots = listOf(inputSnapshot),
            immutableSchema = immutableSchema,
            immutableSemanticRootTypeIds = setOf(BOOK_ID),
            workspace = workspace,
            sourceFilter = JimmerCompilerSourceFilter(),
            defaultNullableInputModifier = DtoModifier.STATIC,
            platform = platform,
        )
        assertTrue(outcome.unresolvedDocuments.isEmpty(), outcome.unresolvedDocuments.joinToString("\n"))
        assertTrue(outcome.failures.isEmpty(), outcome.failures.joinToString("\n"))
        return CompiledFixture(immutableSchema, outcome.schema)
    }

    private fun compileApt(source: String): LsiWorkspace {
        val projectDir = createTempDirectory(prefix = "jimmer-dto-apt-parity").toFile()
        val sourceFile = projectDir.resolve("src/main/java/demo/AuthorFilter.java").also { file ->
            file.parentFile.mkdirs()
            file.writeText(source)
        }
        val classesDir = projectDir.resolve("build/classes").apply(File::mkdirs)
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("DTO frontend parity tests require a JDK compiler")
        val processor = CapturingAptProcessor()
        val success = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8).use { fileManager ->
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(classesDir))
            val task = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                listOf(
                    "-proc:only",
                    "-classpath",
                    testClasspath(),
                ),
                null,
                fileManager.getJavaFileObjects(sourceFile),
            )
            task.setProcessors(listOf(processor))
            task.call()
        }
        assertTrue(success, diagnostics.toErrorMessage())
        return processor.workspaces.single { workspace -> workspace.declarations.isNotEmpty() }
    }

    private fun compileKsp(source: String): LsiWorkspace {
        val projectDir = createTempDirectory(prefix = "jimmer-dto-ksp-parity").toFile()
        val sourceFile = projectDir.resolve("src/main/kotlin/demo/Models.kt").also { file ->
            file.parentFile.mkdirs()
            file.writeText(source)
        }
        val outputDir = projectDir.resolve("build/ksp").apply(File::mkdirs)
        val provider = CapturingKspProvider()
        val logger = CapturingKspLogger()
        val configuration = KSPJvmConfig.Builder().apply {
            moduleName = "dto-frontend-parity"
            sourceRoots = listOf(sourceFile)
            libraries = testClasspathFiles()
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
            listOf(provider),
            logger,
        ).execute()
        assertEquals(KotlinSymbolProcessing.ExitCode.OK, exitCode, logger.messages.joinToString("\n"))
        return provider.workspaces.single { workspace -> workspace.declarations.isNotEmpty() }
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

    private data class CompiledFixture(
        val immutableSchema: JimmerImmutableSchema,
        val dtoSchema: JimmerDtoPrecompiledSchema,
    )

    private companion object {
        val BOOK_ID: LsiSymbolId = LsiSymbolId.type("demo.Book")
        val STORE_ID: LsiSymbolId = LsiSymbolId.type("demo.Store")
        val AUTHOR_ID: LsiSymbolId = LsiSymbolId.type("demo.Author")
        val AUTHOR_TABLE_ID: LsiSymbolId = LsiSymbolId.type("demo.AuthorTable")
        val FILTER_ID: LsiSymbolId = LsiSymbolId.type("demo.AuthorFilter")
        val MARKER_ID: LsiSymbolId = LsiSymbolId.type("demo.Marker")

        val DTO_SOURCE = """
            BookView {
                id
                name
                rating
                storeId
                !filter(demo.AuthorFilter)
                authors {
                    id
                }
            }
        """.trimIndent()

        val JAVA_SOURCE = """
            package demo;

            import java.util.List;
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;
            import org.babyfish.jimmer.sql.Entity;
            import org.babyfish.jimmer.sql.Id;
            import org.babyfish.jimmer.sql.IdView;
            import org.babyfish.jimmer.sql.ManyToOne;
            import org.babyfish.jimmer.sql.ManyToMany;
            import org.babyfish.jimmer.sql.MappedSuperclass;
            import org.babyfish.jimmer.sql.ast.table.Table;
            import org.babyfish.jimmer.sql.fetcher.FieldFilter;
            import org.babyfish.jimmer.sql.fetcher.FieldFilterArgs;
            import org.jetbrains.annotations.Nullable;

            @Retention(RetentionPolicy.RUNTIME)
            @Target({ElementType.TYPE, ElementType.METHOD})
            @interface Marker {
                String value();
            }

            /**
             * 基础模型。
             */
            @MappedSuperclass
            @Marker("base-type")
            interface BaseModel {
                @Id
                long id();

                /**
                 * 显示名称。
                 */
                @Marker("base-name")
                String name();
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

            interface AuthorTable extends Table<Author> {}

            public class AuthorFilter implements FieldFilter<AuthorTable> {
                public AuthorFilter() {}

                @Override
                public void apply(FieldFilterArgs<AuthorTable> args) {}
            }

            /**
             * 图书模型。
             */
            @Entity
            interface Book extends BaseModel {
                @Nullable
                Integer rating();

                @ManyToOne
                Store store();

                @IdView
                long storeId();

                @ManyToMany
                List<Author> authors();
            }
        """.trimIndent()

        val KOTLIN_SOURCE = """
            package demo

            import org.babyfish.jimmer.sql.Entity
            import org.babyfish.jimmer.sql.Id
            import org.babyfish.jimmer.sql.IdView
            import org.babyfish.jimmer.sql.ManyToOne
            import org.babyfish.jimmer.sql.ManyToMany
            import org.babyfish.jimmer.sql.MappedSuperclass
            import org.babyfish.jimmer.sql.kt.fetcher.KFieldFilter
            import org.babyfish.jimmer.sql.kt.fetcher.KFieldFilterDsl

            @Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY_GETTER)
            @Retention(AnnotationRetention.RUNTIME)
            annotation class Marker(val value: String)

            /**
             * 基础模型。
             */
            @MappedSuperclass
            @Marker("base-type")
            interface BaseModel {
                @Id
                val id: Long

                /**
                 * 显示名称。
                 */
                @get:Marker("base-name")
                val name: String
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

            class AuthorFilter() : KFieldFilter<Author> {
                override fun KFieldFilterDsl<Author>.applyTo() = Unit
            }

            /**
             * 图书模型。
             */
            @Entity
            interface Book : BaseModel {
                val rating: Int?

                @ManyToOne
                val store: Store

                @IdView
                val storeId: Long

                @ManyToMany
                val authors: List<Author>
            }
        """.trimIndent()
    }
}
