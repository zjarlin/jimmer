package org.babyfish.jimmer.compiler.frontend

import site.addzero.lsi.jimmer.toJimmerLsiFrontendOptions

import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.KSPJvmConfig
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
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
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import site.addzero.lsi.apt.toLsiWorkspace
import site.addzero.lsi.ksp.toLsiWorkspace
import site.addzero.lsi.ksp.toKspLsiFileScopePlan
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.client.ClientDeclaredTypeRef
import site.addzero.lsi.jimmer.client.ClientOperation
import site.addzero.lsi.jimmer.client.ClientSchemaDependencies
import site.addzero.lsi.jimmer.client.toClientSchema
import site.addzero.lsi.jimmer.error.ErrorSchema
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.type.LsiArrayType
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.field.LsiField
import site.addzero.lsi.model.LsiFunction
import site.addzero.lsi.type.LsiNullability
import site.addzero.lsi.type.LsiPrimitiveType
import site.addzero.lsi.field.LsiProperty
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.type.LsiType
import site.addzero.lsi.model.LsiTypeSeed
import site.addzero.lsi.model.LsiTypeSeedMode
import site.addzero.lsi.type.LsiVariance
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.stableSignature
import site.addzero.lsi.model.toSemanticSnapshot

class LsiTypeUseAnnotationFrontendParityTest {

    @Test
    fun `real apt and ksp freeze nested FetchBy annotations identically`() {
        val aptWorkspace = compileJava()
        val kspWorkspace = compileKotlin()
        val aptFunction = aptWorkspace.findBooksFunction()
        val kspFunction = kspWorkspace.findBooksFunction()
        val aptElementType = aptFunction.returnElementType()
        val kspElementType = kspFunction.returnElementType()
        val aptAnnotation = aptElementType.annotations.single()
        val kspAnnotation = kspElementType.annotations.single()

        assertEquals(FETCH_BY, aptAnnotation.type)
        assertEquals(FETCH_BY, kspAnnotation.type)
        assertEquals(
            "DETAIL",
            assertIs<LsiAnnotationValue.StringValue>(
                aptAnnotation.arguments.getValue("value").value,
            ).value,
        )
        assertEquals(
            true,
            assertIs<LsiAnnotationValue.BooleanValue>(
                aptAnnotation.arguments.getValue("nullable").value,
            ).value,
        )
        assertEquals(
            true,
            assertIs<LsiAnnotationValue.BooleanValue>(
                kspAnnotation.arguments.getValue("nullable").value,
            ).value,
        )
        assertTrue(aptWorkspace.clientFetchByElementType("findBooks").nullable)
        assertFalse(kspWorkspace.clientFetchByElementType("findBooks").nullable)
        assertTrue(kspWorkspace.clientFetchByElementType("findNullableBooks").nullable)
        assertTrue(aptWorkspace.clientFetchByParameterType("save").nullable)
        assertFalse(kspWorkspace.clientFetchByParameterType("save").nullable)
        assertTrue(kspWorkspace.clientFetchByParameterType("saveNullable").nullable)
        assertEquals(
            aptElementType.copy(annotations = emptyList()).stableSignature(),
            aptElementType.stableSignature(),
        )
        assertEquals(
            kspElementType.copy(annotations = emptyList()).stableSignature(),
            kspElementType.stableSignature(),
        )

        val aptSnapshot = aptFunction.returnType.typeSnapshot()
        val kspSnapshot = kspFunction.returnType.typeSnapshot()
        assertEquals(
            aptSnapshot,
            kspSnapshot,
            "APT snapshot:\n$aptSnapshot\nKSP snapshot:\n$kspSnapshot",
        )
        assertContains(
            aptSnapshot,
            "type:org.babyfish.jimmer.client.FetchBy",
        )
        val aptType = aptWorkspace.findProjectedFoosFunction().returnType
        val kspType = kspWorkspace.findProjectedFoosFunction().returnType
        val aptArgument = assertIs<LsiDeclaredType>(aptType).arguments.single()
        val kspArgument = assertIs<LsiDeclaredType>(kspType).arguments.single()
        assertEquals(LsiVariance.OUT, aptArgument.variance)
        assertEquals(LsiVariance.OUT, kspArgument.variance)
        val aptBound = assertIs<LsiDeclaredType>(requireNotNull(aptArgument.type))
        val kspBound = assertIs<LsiDeclaredType>(requireNotNull(kspArgument.type))
        assertEquals(listOf(TYPE_MARKER), aptBound.annotations.map { annotation -> annotation.type })
        assertEquals(listOf(TYPE_MARKER), kspBound.annotations.map { annotation -> annotation.type })

        val projectionAptSnapshot = aptType.typeSnapshot()
        val projectionKspSnapshot = kspType.typeSnapshot()
        assertEquals(
            projectionAptSnapshot,
            projectionKspSnapshot,
            "APT snapshot:\n$projectionAptSnapshot\nKSP snapshot:\n$projectionKspSnapshot",
        )
        assertContains(projectionAptSnapshot, "type:demo.TypeMarker")
        assertJavaProjectionRules(aptWorkspace)
        assertJavaProjectionRules(kspWorkspace)
        assertPrimitiveRepresentations(aptWorkspace)
        assertPrimitiveRepresentations(kspWorkspace)
        assertEquals(executableProjection(aptWorkspace), executableProjection(kspWorkspace))
    }

    private fun compileJava(): LsiWorkspace {
        val projectDirectory = createTempDirectory(prefix = "lsi-type-use-apt-parity").toFile()
        val sourceDirectory = projectDirectory.resolve("src/main/java/demo")
        val classesDirectory = projectDirectory.resolve("build/classes")
        val sourceFile = sourceDirectory.resolve("BookService.java")
        val projectionSourceFile = sourceDirectory.resolve("ProjectionCases.java")
        val validationSourceFile = projectDirectory.resolve(
            "src/main/java/jakarta/validation/constraints/NotNull.java",
        )
        sourceDirectory.mkdirs()
        validationSourceFile.parentFile.mkdirs()
        classesDirectory.mkdirs()
        sourceFile.writeText(
            """
                package demo;

                import java.util.List;
                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;
                import org.babyfish.jimmer.client.FetchBy;
                import org.babyfish.jimmer.client.meta.Api;
                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.fetcher.Fetcher;

                @Entity
                interface Book {}

                interface Box<T> {}

                interface Foo {}

                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.TYPE_USE)
                @interface TypeMarker {}

                @Api
                public interface BookService {
                    Fetcher<Book> DETAIL = null;

                    @Api
                    List<@FetchBy(value = "DETAIL", nullable = true) Book> findBooks(int page);

                    @Api
                    List<@FetchBy(value = "DETAIL", nullable = true) Book> findNullableBooks(int page);

                    @Api
                    int save(@FetchBy(value = "DETAIL", nullable = true) Book book);

                    @Api
                    int saveNullable(@FetchBy(value = "DETAIL", nullable = true) Book book);
                }

                interface ProjectionService {
                    Box<@TypeMarker ? extends Foo> findProjectedFoos(int page);
                }
            """.trimIndent(),
        )
        projectionSourceFile.writeText(PROJECTION_CASES_SOURCE)
        validationSourceFile.writeText(VALIDATION_NOT_NULL_SOURCE)
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("APT parity tests require a JDK compiler")
        val processor = CapturingAptProcessor()
        val success = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8).use { fileManager ->
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(classesDirectory))
            val task = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                listOf("-proc:only", "-classpath", testClasspath()),
                null,
                fileManager.getJavaFileObjects(sourceFile, projectionSourceFile, validationSourceFile),
            )
            task.setProcessors(listOf(processor))
            task.call()
        }

        assertTrue(success, diagnostics.toErrorMessage())
        return processor.workspaces.single { workspace -> workspace.declarations.isNotEmpty() }
    }

    private fun compileKotlin(): LsiWorkspace {
        val projectDirectory = createTempDirectory(prefix = "lsi-type-use-ksp-parity").toFile()
        val sourceDirectory = projectDirectory.resolve("src/main/kotlin/demo")
        val sourceFile = sourceDirectory.resolve("BookService.kt")
        val projectionSourceFile = projectDirectory.resolve("src/main/java/demo/ProjectionCases.java")
        val validationSourceFile = projectDirectory.resolve(
            "src/main/java/jakarta/validation/constraints/NotNull.java",
        )
        sourceDirectory.mkdirs()
        projectionSourceFile.parentFile.mkdirs()
        validationSourceFile.parentFile.mkdirs()
        sourceFile.writeText(
            """
                package demo

                import org.babyfish.jimmer.client.FetchBy
                import org.babyfish.jimmer.client.meta.Api
                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.fetcher.Fetcher

                @Entity
                interface Book

                interface Box<T>

                interface Foo

                @Target(AnnotationTarget.TYPE)
                @Retention(AnnotationRetention.RUNTIME)
                annotation class TypeMarker

                @Api
                interface BookService {
                    @Api
                    fun findBooks(page: Int): List<@FetchBy(value = "DETAIL", nullable = true) Book>

                    @Api
                    fun findNullableBooks(page: Int): List<@FetchBy(value = "DETAIL", nullable = true) Book?>

                    @Api
                    fun save(book: @FetchBy(value = "DETAIL", nullable = true) Book): Int

                    @Api
                    fun saveNullable(book: @FetchBy(value = "DETAIL", nullable = true) Book?): Int

                    companion object {
                        val DETAIL: Fetcher<Book>
                            get() = error("unused")
                    }
                }

                interface ProjectionService {
                    fun findProjectedFoos(page: Int): Box<out @TypeMarker Foo>
                }
            """.trimIndent(),
        )
        projectionSourceFile.writeText(PROJECTION_CASES_SOURCE)
        validationSourceFile.writeText(VALIDATION_NOT_NULL_SOURCE)
        val outputDirectory = projectDirectory.resolve("build/ksp").apply(File::mkdirs)
        val provider = CapturingKspProvider()
        val logger = CollectingKspLogger()
        val config = KSPJvmConfig.Builder().apply {
            moduleName = "lsi-type-use-parity"
            sourceRoots = listOf(sourceFile)
            javaSourceRoots = listOf(projectionSourceFile, validationSourceFile)
            libraries = testClasspathFiles()
            projectBaseDir = projectDirectory
            outputBaseDir = outputDirectory
            cachesDir = outputDirectory.resolve("caches").apply(File::mkdirs)
            classOutputDir = outputDirectory.resolve("classes").apply(File::mkdirs)
            kotlinOutputDir = outputDirectory.resolve("kotlin").apply(File::mkdirs)
            javaOutputDir = outputDirectory.resolve("java").apply(File::mkdirs)
            resourceOutputDir = outputDirectory.resolve("resources").apply(File::mkdirs)
            jdkHome = File(System.getProperty("java.home"))
            jvmTarget = "17"
            languageVersion = "2.1"
            apiVersion = "2.1"
        }.build()
        val exitCode = KotlinSymbolProcessing(config, listOf(provider), logger).execute()

        assertEquals(KotlinSymbolProcessing.ExitCode.OK, exitCode, logger.messages.joinToString("\n"))
        return provider.workspaces.single { workspace -> workspace.declarations.isNotEmpty() }
    }

    private fun LsiWorkspace.findBooksFunction(): LsiFunction {
        return declarationsOfType<LsiFunction>().single { function ->
            function.ownerId == BOOK_SERVICE && function.name == "findBooks"
        }
    }

    private fun LsiWorkspace.findProjectedFoosFunction(): LsiFunction {
        return declarationsOfType<LsiFunction>().single { function ->
            function.ownerId == PROJECTION_SERVICE && function.name == "findProjectedFoos"
        }
    }

    private fun LsiWorkspace.clientFetchByElementType(functionName: String): ClientDeclaredTypeRef {
        val listType = assertIs<ClientDeclaredTypeRef>(clientOperation(functionName).returnType)
        return assertIs<ClientDeclaredTypeRef>(requireNotNull(listType.arguments.single().type))
    }

    private fun LsiWorkspace.clientFetchByParameterType(functionName: String): ClientDeclaredTypeRef {
        return assertIs<ClientDeclaredTypeRef>(clientOperation(functionName).parameters.single().type)
    }

    private fun LsiWorkspace.clientOperation(functionName: String): ClientOperation {
        val schema = toClientSchema(
            ClientSchemaDependencies(
                immutableSchema = ImmutableSchema(emptyList()),
                errorSchema = ErrorSchema(emptyList()),
                definitionDocumentationByTypeId = emptyMap(),
            ),
        )
        val operation = schema.services
            .single { service -> service.id == BOOK_SERVICE }
            .operations
            .single { operation -> operation.name == functionName }
        return operation
    }

    private fun assertJavaProjectionRules(workspace: LsiWorkspace) {
        assertIs<LsiFunction>(workspace[CALCULATE_FUNCTION])
        assertEquals(null, workspace[CALCULATE_PROPERTY])
        assertIs<LsiProperty>(workspace[NAME_PROPERTY])
        assertIs<LsiProperty>(workspace[CONTRACT_VALUE_PROPERTY])
        assertIs<LsiFunction>(workspace[CONTRACT_HELPER_FUNCTION])
        assertEquals(null, workspace[CONTRACT_HELPER_PROPERTY])
        assertIs<LsiProperty>(workspace[RECORD_VALUE_PROPERTY])
    }

    private fun assertPrimitiveRepresentations(workspace: LsiWorkspace) {
        val raw = assertIs<LsiPrimitiveType>(assertIs<LsiProperty>(workspace[RAW_COUNT_PROPERTY]).type)
        val boxed = assertIs<LsiPrimitiveType>(assertIs<LsiProperty>(workspace[BOXED_COUNT_PROPERTY]).type)
        val validated = assertIs<LsiPrimitiveType>(
            assertIs<LsiProperty>(workspace[VALIDATED_COUNT_PROPERTY]).type,
        )

        assertFalse(raw.boxed)
        assertEquals(LsiNullability.NON_NULL, raw.nullability)
        assertTrue(boxed.boxed)
        assertEquals(LsiNullability.PLATFORM, boxed.nullability)
        assertTrue(validated.boxed)
        assertEquals(LsiNullability.PLATFORM, validated.nullability)
    }

    private fun executableProjection(workspace: LsiWorkspace): ExecutableProjection {
        val executable = assertIs<LsiClass>(workspace[EXECUTABLE_TYPE])
        assertEquals(executable.memberIds.distinct(), executable.memberIds)
        assertEquals(1, executable.memberIds.count { memberId -> memberId == DECLARED_ANNOTATIONS_FIELD })
        assertEquals(1, executable.memberIds.count { memberId -> memberId == DECLARED_ANNOTATIONS_PROPERTY })

        val field = assertIs<LsiField>(workspace[DECLARED_ANNOTATIONS_FIELD])
        val fieldType = assertIs<LsiDeclaredType>(field.type)
        assertEquals(MAP_TYPE, fieldType.declarationId)

        val property = assertIs<LsiProperty>(workspace[DECLARED_ANNOTATIONS_PROPERTY])
        val propertyType = assertIs<LsiArrayType>(property.type)
        val elementType = assertIs<LsiDeclaredType>(propertyType.elementType)
        assertEquals(ANNOTATION_TYPE, elementType.declarationId)
        assertEquals("getDeclaredAnnotations", property.getterName)

        val helper = assertIs<LsiFunction>(workspace[DECLARED_ANNOTATIONS_HELPER])
        val helperType = assertIs<LsiDeclaredType>(helper.returnType)
        assertEquals(MAP_TYPE, helperType.declarationId)

        return ExecutableProjection(
            fieldType = fieldType.declarationId,
            fieldMutable = field.mutable,
            fieldLanguage = field.origin.language,
            propertyElementType = elementType.declarationId,
            propertyGetterName = property.getterName,
            propertyLanguage = property.origin.language,
            helperReturnType = helperType.declarationId,
            helperLanguage = helper.origin.language,
        )
    }

    private fun LsiFunction.returnElementType(): LsiDeclaredType {
        val listType = assertIs<LsiDeclaredType>(returnType)
        assertEquals(LIST_TYPE, listType.declarationId)
        return assertIs<LsiDeclaredType>(requireNotNull(listType.arguments.single().type))
    }

    private fun LsiType.typeSnapshot(): String {
        val ownerId = LsiSymbolId.type("snapshot.Owner")
        val propertyId = LsiSymbolId.property(ownerId, "value")
        val workspace = LsiWorkspace(
            declarations = listOf(
                LsiClass(
                    id = ownerId,
                    name = "Owner",
                    qualifiedName = "snapshot.Owner",
                    kind = LsiTypeDeclarationKind.INTERFACE,
                    memberIds = listOf(propertyId),
                    origin = SYNTHETIC_ORIGIN,
                ),
                LsiProperty(
                    id = propertyId,
                    name = "value",
                    ownerId = ownerId,
                    type = this,
                    origin = SYNTHETIC_ORIGIN,
                ),
            ),
        )
        return workspace.toSemanticSnapshot().lineSequence().single { line ->
            line.startsWith("property|${propertyId.value}|")
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

    private class CapturingAptProcessor : AbstractProcessor() {
        val workspaces = mutableListOf<LsiWorkspace>()

        override fun getSupportedAnnotationTypes(): Set<String> = setOf("*")

        override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()

        override fun process(
            annotations: Set<TypeElement>,
            roundEnvironment: RoundEnvironment,
        ): Boolean {
            if (!roundEnvironment.processingOver()) {
                val rootTypes = roundEnvironment.rootElements.filterIsInstance<TypeElement>()
                workspaces += rootTypes.toLsiWorkspace(
                    processingEnvironment = processingEnv,
                    frontendOptions = processingEnv.options.toJimmerLsiFrontendOptions(),
                    packageElements = rootTypes.map(processingEnv.elementUtils::getPackageOf),
                    additionalSeeds = listOf(EXECUTABLE_SEED),
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
                    val sourceFiles = resolver.getAllFiles().toList()
                    val rootTypes = sourceFiles.asSequence()
                        .flatMap { file -> file.declarations.filterIsInstance<KSClassDeclaration>() }
                        .toList()
                    workspaces += rootTypes.toLsiWorkspace(
                        resolver = resolver,
                        frontendOptions = environment.options.toJimmerLsiFrontendOptions(),
                        fileScopes = sourceFiles.toKspLsiFileScopePlan().validScopes,
                        additionalSeeds = listOf(EXECUTABLE_SEED),
                    )
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

        override fun exception(e: Throwable) {
            throw e
        }
    }

    private data class ExecutableProjection(
        val fieldType: LsiSymbolId,
        val fieldMutable: Boolean,
        val fieldLanguage: LsiLanguage,
        val propertyElementType: LsiSymbolId,
        val propertyGetterName: String,
        val propertyLanguage: LsiLanguage,
        val helperReturnType: LsiSymbolId,
        val helperLanguage: LsiLanguage,
    )

    private companion object {
        val BOOK_SERVICE = LsiSymbolId.type("demo.BookService")
        val LIST_TYPE = LsiSymbolId.type("java.util.List")
        val FETCH_BY = LsiSymbolId.type("org.babyfish.jimmer.client.FetchBy")
        val PROJECTION_SERVICE = LsiSymbolId.type("demo.ProjectionService")
        val TYPE_MARKER = LsiSymbolId.type("demo.TypeMarker")
        val EXECUTABLE_TYPE = LsiSymbolId.type("java.lang.reflect.Executable")
        val DECLARED_ANNOTATIONS_FIELD = LsiSymbolId.field(EXECUTABLE_TYPE, "declaredAnnotations")
        val DECLARED_ANNOTATIONS_PROPERTY = LsiSymbolId.property(EXECUTABLE_TYPE, "declaredAnnotations")
        val DECLARED_ANNOTATIONS_HELPER = LsiSymbolId.function(EXECUTABLE_TYPE, "declaredAnnotations", emptyList())
        val MAP_TYPE = LsiSymbolId.type("java.util.Map")
        val ANNOTATION_TYPE = LsiSymbolId.type("java.lang.annotation.Annotation")
        val EXECUTABLE_SEED = LsiTypeSeed(EXECUTABLE_TYPE, LsiTypeSeedMode.FULL_DECLARATION)
        val PROJECTION_CASES_TYPE = LsiSymbolId.type("demo.ProjectionCases")
        val CONTRACT_TYPE = LsiSymbolId.type("demo.ProjectionCases.Contract")
        val RECORD_TYPE = LsiSymbolId.type("demo.ProjectionCases.Row")
        val CALCULATE_FUNCTION = LsiSymbolId.function(PROJECTION_CASES_TYPE, "calculate", emptyList())
        val CALCULATE_PROPERTY = LsiSymbolId.property(PROJECTION_CASES_TYPE, "calculate")
        val NAME_PROPERTY = LsiSymbolId.property(PROJECTION_CASES_TYPE, "name")
        val RAW_COUNT_PROPERTY = LsiSymbolId.property(PROJECTION_CASES_TYPE, "rawCount")
        val BOXED_COUNT_PROPERTY = LsiSymbolId.property(PROJECTION_CASES_TYPE, "boxedCount")
        val VALIDATED_COUNT_PROPERTY = LsiSymbolId.property(PROJECTION_CASES_TYPE, "validatedCount")
        val CONTRACT_VALUE_PROPERTY = LsiSymbolId.property(CONTRACT_TYPE, "value")
        val CONTRACT_HELPER_FUNCTION = LsiSymbolId.function(CONTRACT_TYPE, "helper", emptyList())
        val CONTRACT_HELPER_PROPERTY = LsiSymbolId.property(CONTRACT_TYPE, "helper")
        val RECORD_VALUE_PROPERTY = LsiSymbolId.property(RECORD_TYPE, "value")
        val SYNTHETIC_ORIGIN = LsiOrigin(LsiOriginKind.SYNTHETIC)
        val PROJECTION_CASES_SOURCE = """
            package demo;

            public class ProjectionCases {
                public String calculate() {
                    return "";
                }

                public String getName() {
                    return "";
                }

                public int getRawCount() {
                    return 0;
                }

                public Integer getBoxedCount() {
                    return null;
                }

                @jakarta.validation.constraints.NotNull
                public Integer getValidatedCount() {
                    return null;
                }

                public interface Contract {
                    String value();

                    private String helper() {
                        return "";
                    }
                }

                public record Row(String value) {}
            }
        """.trimIndent()
        val VALIDATION_NOT_NULL_SOURCE = """
            package jakarta.validation.constraints;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            @Retention(RetentionPolicy.RUNTIME)
            @Target({
                ElementType.METHOD,
                ElementType.FIELD,
                ElementType.ANNOTATION_TYPE,
                ElementType.CONSTRUCTOR,
                ElementType.PARAMETER,
                ElementType.TYPE_USE
            })
            public @interface NotNull {}
        """.trimIndent()
    }
}
