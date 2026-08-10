package org.babyfish.jimmer.compiler.dto

import site.addzero.lsi.jimmer.input.*

import site.addzero.lsi.jimmer.toJimmerLsiFrontendOptions

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
import site.addzero.lsi.compiler.CompilerInputDocument
import site.addzero.lsi.compiler.CompilerInputDocumentOrigin
import site.addzero.lsi.compiler.CompilerPlatform
import site.addzero.lsi.compiler.CompilerSourceSet
import org.babyfish.jimmer.compiler.JimmerCompilerSourceFilter
import site.addzero.lsi.jimmer.toImmutableSchema
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableView
import site.addzero.lsi.jimmer.fingerprint
import site.addzero.lsi.jimmer.normalizedSnapshot
import site.addzero.lsi.jimmer.jimmerTypeSignature
import org.babyfish.jimmer.compiler.input.CompilerInputDocumentReferenceFreezer
import site.addzero.lsi.apt.toLsiWorkspace
import site.addzero.lsi.ksp.toLsiWorkspace
import org.babyfish.jimmer.dto.compiler.DtoModifier
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.anno.LsiAnnotationValue
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.type.LsiNullability
import site.addzero.lsi.field.LsiProperty
import site.addzero.lsi.type.LsiType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.stableSignature
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoConfigContractKind
import site.addzero.lsi.jimmer.dto.DtoInterfaceContractResolution
import site.addzero.lsi.jimmer.dto.fingerprint as dtoFingerprint
import site.addzero.lsi.jimmer.dto.normalizedSnapshot as dtoNormalizedSnapshot

class JimmerDtoFrontendParityTest {

    @Test
    fun `real ksp preserves identical repeatable property annotation occurrences`() {
        val workspace = compileKsp(KSP_REPEATABLE_SOURCE)
        val modelTypeId = LsiSymbolId.type("repeatable.Model")
        val annotations = assertIs<LsiProperty>(
            workspace[LsiSymbolId.property(modelTypeId, "name")],
        )
            .annotations
            .filter { annotation -> annotation.type == LsiSymbolId.type("repeatable.Marker") }

        assertEquals(2, annotations.size)
        assertEquals(annotations[0], annotations[1])
    }

    @Test
    fun `real apt and ksp frontends produce identical dto render contracts`() {
        val apt = compileFixture(compileApt(JAVA_SOURCE), CompilerPlatform.APT)
        val ksp = compileFixture(compileKsp(KOTLIN_SOURCE), CompilerPlatform.KSP)

        assertEquals(
            apt.immutableSchema.normalizedSnapshot(),
            ksp.immutableSchema.normalizedSnapshot(),
        )
        assertEquals(apt.immutableSchema.fingerprint(), ksp.immutableSchema.fingerprint())

        val aptGraph = apt.dtoResolution.graphs.single()
        val kspGraph = ksp.dtoResolution.graphs.single()
        val aptAnnotationContract = apt.dtoResolution.annotationContractsBySource.getValue(aptGraph.source)
        val kspAnnotationContract = ksp.dtoResolution.annotationContractsBySource.getValue(kspGraph.source)
        val aptInterfaceResolution = apt.dtoResolution.interfaceContractsBySource.getValue(aptGraph.source)
        val kspInterfaceResolution = ksp.dtoResolution.interfaceContractsBySource.getValue(kspGraph.source)
        val aptConfigResolution = apt.dtoResolution.configContractsBySource.getValue(aptGraph.source)
        val kspConfigResolution = ksp.dtoResolution.configContractsBySource.getValue(kspGraph.source)
        assertEquals(aptGraph, kspGraph)
        assertEquals(
            aptAnnotationContract.typePlans,
            kspAnnotationContract.typePlans,
        )
        assertEquals(
            aptAnnotationContract.propPlans,
            kspAnnotationContract.propPlans,
        )
        assertEquals(
            aptInterfaceResolution.canonicalizeFrontendMetadata(),
            kspInterfaceResolution.canonicalizeFrontendMetadata(),
        )
        val aptConfigContract = aptConfigResolution.contracts.single()
        val kspConfigContract = kspConfigResolution.contracts.single()
        assertEquals(DtoConfigContractKind.FILTER, aptConfigContract.kind)
        assertEquals(aptConfigContract.targetEntityTypeId, kspConfigContract.targetEntityTypeId)
        assertEquals(aptConfigContract.dependencyTypeIds, kspConfigContract.dependencyTypeIds)
        assertEquals(listOf(AUTHOR_ID, FILTER_ID), aptConfigContract.dependencyTypeIds)
        val aptMarkerDeclaration = aptAnnotationContract.declarationsByTypeId.getValue(MARKER_ID)
        val kspMarkerDeclaration = kspAnnotationContract.declarationsByTypeId.getValue(MARKER_ID)
        assertEquals(LsiLanguage.JAVA, aptMarkerDeclaration.language)
        assertEquals(LsiLanguage.KOTLIN, kspMarkerDeclaration.language)
        assertEquals(
            aptMarkerDeclaration.copy(language = LsiLanguage.KOTLIN),
            kspMarkerDeclaration,
        )
        val canonicalAptResolution = apt.dtoResolution.copy(
            annotationContractsBySource = sortedMapOf(
                aptGraph.source to aptAnnotationContract.copy(
                    declarations = aptAnnotationContract.declarations.map { declaration ->
                        if (declaration.typeId == MARKER_ID) {
                            declaration.copy(language = LsiLanguage.KOTLIN)
                        } else {
                            declaration
                        }
                    },
                )
            ),
            interfaceContractsBySource = sortedMapOf(
                aptGraph.source to aptInterfaceResolution.canonicalizeFrontendMetadata()
            ),
        )
        val canonicalKspResolution = ksp.dtoResolution.copy(
            interfaceContractsBySource = sortedMapOf(
                kspGraph.source to kspInterfaceResolution.canonicalizeFrontendMetadata()
            ),
        )
        assertEquals(
            canonicalAptResolution.resolvedInputs.resolvedInputFingerprint(),
            canonicalKspResolution.resolvedInputs.resolvedInputFingerprint(),
        )
        assertEquals(
            dtoSemanticFingerprint(
                canonicalAptResolution.graphs,
                canonicalAptResolution.annotationContractsBySource,
                canonicalAptResolution.interfaceContractsBySource,
                canonicalAptResolution.configContractsBySource,
            ),
            dtoSemanticFingerprint(
                canonicalKspResolution.graphs,
                canonicalKspResolution.annotationContractsBySource,
                canonicalKspResolution.interfaceContractsBySource,
                canonicalKspResolution.configContractsBySource,
            ),
        )

        val interfaceContract = aptInterfaceResolution.contracts.single { contract ->
            VIEW_CONTRACT_ID in contract.superInterfaceTypeIds
        }
        assertEquals(VIEW_CONTRACT_ID, interfaceContract.superInterfaceTypeIds.single())
        val aptLabelProp = interfaceContract.props.single()
        val kspInterfaceContract = kspInterfaceResolution.contracts.single { contract ->
            VIEW_CONTRACT_ID in contract.superInterfaceTypeIds
        }
        val kspLabelProp = kspInterfaceContract.props.single()
        assertEquals("label", aptLabelProp.name)
        assertEquals(VIEW_CONTRACT_ID, aptLabelProp.declaringTypeId)
        assertEquals(LABEL_VALUE_ID, (aptLabelProp.type as LsiDeclaredType).declarationId)
        assertEquals(LABEL_VALUE_ID, (kspLabelProp.type as LsiDeclaredType).declarationId)
        assertEquals("type:demo.LabelValue!platform", aptLabelProp.type.stableSignature())
        assertEquals("type:demo.LabelValue!non-null", kspLabelProp.type.stableSignature())
        assertEquals(false, aptLabelProp.mutable)
        assertEquals("label", aptLabelProp.getter?.name)
        assertEquals(null, aptLabelProp.setter)
        assertEquals(LsiOriginKind.SOURCE, aptLabelProp.origin.kind)
        assertEquals(LsiLanguage.JAVA, aptLabelProp.origin.language)
        assertTrue(aptLabelProp.origin.source?.path?.endsWith("demo/AuthorFilter.java") == true)
        assertTrue(aptLabelProp.origin.originatingSymbols.isEmpty())
        assertEquals(aptLabelProp.origin, aptLabelProp.getter?.origin)
        assertEquals(LsiOriginKind.SOURCE, kspLabelProp.origin.kind)
        assertEquals(LsiLanguage.KOTLIN, kspLabelProp.origin.language)
        assertTrue(kspLabelProp.origin.source?.path?.endsWith("demo/Models.kt") == true)
        assertTrue(kspLabelProp.origin.originatingSymbols.isEmpty())
        assertEquals(kspLabelProp.origin, kspLabelProp.getter?.origin)
        val aptInterfaceSnapshot = aptInterfaceResolution.dtoNormalizedSnapshot()
        assertTrue("4:prop" in aptInterfaceSnapshot)
        assertTrue("type:demo.ViewContract/property:label" in aptInterfaceSnapshot)
        assertEquals(64, aptConfigResolution.dtoFingerprint().length)

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
        assertEquals("primitive:int:boxed?", ratingProp.type.jimmerTypeSignature())
        assertEquals(
            ImmutableView.Id(
                basePropId = storeProp.id,
                targetIdPropId = LsiSymbolId.property(STORE_ID, "id"),
            ),
            storeIdProp.view,
        )

        val dtoType = aptGraph.typesById.getValue(
            aptGraph.rootTypeIds.single(),
        )
        val dtoProps = dtoType.propIds.map { propId ->
            aptGraph.propsById.getValue(propId)
        }
        assertEquals(listOf("id", "name", "rating", "storeId", "authors"), dtoProps.map { prop -> prop.name })
        assertTrue(dtoProps.single { prop -> prop.name == "rating" }.nullable)
        assertEquals(
            LsiSymbolId.property(BOOK_ID, "storeId"),
            (dtoProps.single { prop -> prop.name == "storeId" } as DtoBaseProp)
                .baseProps
                .single()
                .propId,
        )
    }

    private fun compileFixture(
        workspace: LsiWorkspace,
        platform: CompilerPlatform,
    ): CompiledFixture {
        val immutableSchema = workspace.toImmutableSchema()
        val document = CompilerInputDocument(
            kind = DTO_INPUT_DOCUMENT_KIND,
            sourceSet = CompilerSourceSet.MAIN,
            origin = CompilerInputDocumentOrigin.Project("demo-project", "src/main/dto"),
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
        return CompiledFixture(immutableSchema, outcome)
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
                    processingEnv.options.toJimmerLsiFrontendOptions(),
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
                    workspaces += resolver.toLsiWorkspace(environment.options.toJimmerLsiFrontendOptions())
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
        val immutableSchema: ImmutableSchema,
        val dtoResolution: JimmerDtoRoundResolution,
    )

    /**
     * Java 未标注的泛型返回值是 platform 类型，Kotlin 同义声明是 non-null 类型。
     */
    private fun DtoInterfaceContractResolution.canonicalizeFrontendMetadata(): DtoInterfaceContractResolution {
        val origin = LsiOrigin(LsiOriginKind.SYNTHETIC)
        return copy(
            contracts = contracts.map { contract ->
                contract.copy(
                    props = contract.props.map { prop ->
                        prop.copy(
                            type = prop.type.canonicalizeFrontendNullability(),
                            getter = prop.getter?.copy(origin = origin),
                            setter = prop.setter?.copy(origin = origin),
                            origin = origin,
                        )
                    },
                )
            },
        )
    }

    private fun LsiType.canonicalizeFrontendNullability(): LsiType {
        return when (this) {
            is LsiDeclaredType -> copy(
                nullability = if (nullability == LsiNullability.PLATFORM) {
                    LsiNullability.NON_NULL
                } else {
                    nullability
                },
            )
            else -> this
        }
    }

    private companion object {
        val BOOK_ID: LsiSymbolId = LsiSymbolId.type("demo.Book")
        val STORE_ID: LsiSymbolId = LsiSymbolId.type("demo.Store")
        val AUTHOR_ID: LsiSymbolId = LsiSymbolId.type("demo.Author")
        val AUTHOR_TABLE_ID: LsiSymbolId = LsiSymbolId.type("demo.AuthorTable")
        val FILTER_ID: LsiSymbolId = LsiSymbolId.type("demo.AuthorFilter")
        val MARKER_ID: LsiSymbolId = LsiSymbolId.type("demo.Marker")
        val LABEL_VALUE_ID: LsiSymbolId = LsiSymbolId.type("demo.LabelValue")
        val VIEW_CONTRACT_ID: LsiSymbolId = LsiSymbolId.type("demo.ViewContract")

        val DTO_SOURCE = """
            BookView implements demo.ViewContract<demo.LabelValue> {
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

            interface LabelValue {}

            interface ViewContract<T> {
                T label();
            }

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

            interface LabelValue

            interface ViewContract<T> {
                val label: T
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

        val KSP_REPEATABLE_SOURCE = """
            package repeatable

            @Target(AnnotationTarget.PROPERTY_GETTER)
            @Retention(AnnotationRetention.RUNTIME)
            @Repeatable
            annotation class Marker(val value: String)

            interface Model {
                @get:Marker("same")
                @get:Marker("same")
                val name: String
            }
        """.trimIndent()
    }
}
