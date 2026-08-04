package org.babyfish.jimmer.compiler.dto

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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
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
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.ImmutableTypeKind
import org.babyfish.jimmer.compiler.immutable.completeEntityProps
import site.addzero.lsi.apt.toLsiWorkspace
import site.addzero.lsi.ksp.toLsiWorkspace
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.jimmer.dto.DtoAnnotation
import site.addzero.lsi.jimmer.dto.DtoAnnotationArgument
import site.addzero.lsi.jimmer.dto.DtoAnnotationContract
import site.addzero.lsi.jimmer.dto.DtoAnnotationDeclarationKind
import site.addzero.lsi.jimmer.dto.DtoAnnotationValue
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.jimmer.dto.DtoTypeRef
import site.addzero.lsi.jimmer.dto.resolveDtoAnnotationContract

class DtoAnnotationBinaryFrontendParityTest {

    @Test
    fun `apt and ksp freeze the same compiled kotlin annotation vararg contract`() {
        val library = compileKotlinAnnotation()
        val aptWorkspace = compileAptConsumer(library)
        val kspWorkspace = compileKspConsumer(library)
        val aptDeclaration = assertIs<LsiTypeDeclaration>(aptWorkspace[TAGS_ID])
        val kspDeclaration = assertIs<LsiTypeDeclaration>(kspWorkspace[TAGS_ID])

        assertEquals(LsiOriginKind.BINARY, aptDeclaration.origin.kind)
        assertEquals(LsiLanguage.JAVA, aptDeclaration.origin.language)
        assertEquals(LsiOriginKind.BINARY, kspDeclaration.origin.kind)
        assertEquals(LsiLanguage.KOTLIN, kspDeclaration.origin.language)
        assertEquals(aptDeclaration.annotationMembers, kspDeclaration.annotationMembers)
        assertEquals(
            listOf("alpha", "value", "zebra"),
            aptDeclaration.annotationMembers.map { member -> member.name },
        )
        assertEquals(
            mapOf("alpha" to 1, "value" to 2, "zebra" to 0),
            aptDeclaration.annotationMembers.associate { member -> member.name to member.declarationIndex },
        )
        assertTrue(aptDeclaration.annotationMembers.single { member -> member.name == "value" }.vararg)

        val aptContract = freezeContract(aptWorkspace, JAVA_USE_ID)
        val kspContract = freezeContract(kspWorkspace, KOTLIN_USE_ID)
        val aptTagDeclaration = aptContract.declarationsByTypeId.getValue(TAGS_ID)
        val kspTagDeclaration = kspContract.declarationsByTypeId.getValue(TAGS_ID)
        val aptTag = aptContract.typePlans.single().applications
            .single { application -> application.annotation.type == TAGS_ID }
            .annotation
        val kspTag = kspContract.typePlans.single().applications
            .single { application -> application.annotation.type == TAGS_ID }
            .annotation

        assertEquals(aptTagDeclaration, kspTagDeclaration)
        assertEquals(aptTag, kspTag)
        assertEquals(
            LsiAnnotationValue.ArrayValue(
                listOf(
                    LsiAnnotationValue.StringValue("first"),
                    LsiAnnotationValue.StringValue("second"),
                )
            ),
            aptTag.arguments.getValue("value").value,
        )
        assertEquals(DtoAnnotationDeclarationKind.KOTLIN, aptTagDeclaration.kind)
        assertEquals(listOf("alpha", "value", "zebra"), aptTagDeclaration.argumentNames)
        assertEquals(
            listOf("zebra", "alpha", "value"),
            aptTagDeclaration.argumentNamesInDeclarationOrder,
        )
        assertEquals(
            aptTagDeclaration.argumentNamesInDeclarationOrder,
            kspTagDeclaration.argumentNamesInDeclarationOrder,
        )
        assertTrue(aptTagDeclaration.kotlinValueVararg)
    }

    @Test
    fun `ksp freezes java binary annotation members exposed as properties`() {
        val library = compileJavaAnnotation()
        val aptWorkspace = compileAptJavaAnnotationConsumer(library)
        val kspWorkspace = compileKspJavaAnnotationConsumer(library)
        val declaration = assertIs<LsiTypeDeclaration>(kspWorkspace[JAVA_ANNOTATION_ID])
        assertTrue(aptWorkspace.typeHierarchyEntry(JAVA_INTEGER_ID) == null)
        assertTrue(kspWorkspace.typeHierarchyEntry(JAVA_INTEGER_ID) == null)
        val aptContract = freezeContract(aptWorkspace, JAVA_JAVA_USE_ID)
        val kspContract = freezeContract(kspWorkspace, KOTLIN_JAVA_USE_ID)
        val aptDeclaration = aptContract.declarationsByTypeId.getValue(JAVA_ANNOTATION_ID)
        val kspDeclaration = kspContract.declarationsByTypeId.getValue(JAVA_ANNOTATION_ID)
        val aptAnnotation = aptContract.typePlans.single().applications.single().annotation
        val kspAnnotation = kspContract.typePlans.single().applications.single().annotation

        assertEquals(
            listOf("number", "pattern", "value"),
            declaration.annotationMembers.map { member -> member.name },
        )
        assertEquals(aptDeclaration, kspDeclaration)
        assertEquals(aptAnnotation, kspAnnotation)
        assertEquals(setOf("pattern"), aptAnnotation.arguments.keys)
        assertEquals(
            LsiAnnotationValue.StringValue("first"),
            aptAnnotation.arguments.getValue("pattern").value,
        )

        val dtoAnnotation = DtoAnnotation(
            typeId = JAVA_ANNOTATION_ID,
            arguments = listOf(
                DtoAnnotationArgument(
                    name = "number",
                    value = DtoAnnotationValue.TypeValue(
                        DtoTypeRef(
                            typeName = "Int",
                            arguments = emptyList(),
                            nullable = false,
                            location = DTO_LOCATION,
                        )
                    ),
                )
            ),
        )
        val aptDtoContract = freezeContract(aptWorkspace, JAVA_JAVA_USE_ID, listOf(dtoAnnotation))
        val kspDtoContract = freezeContract(kspWorkspace, KOTLIN_JAVA_USE_ID, listOf(dtoAnnotation))
        val aptNumberValue = aptDtoContract.typePlans.single().applications.single()
            .annotation.arguments.getValue("number").value
        val kspNumberValue = kspDtoContract.typePlans.single().applications.single()
            .annotation.arguments.getValue("number").value

        assertEquals(aptNumberValue, kspNumberValue)
        assertEquals(
            LsiPrimitiveKind.INT,
            assertIs<LsiPrimitiveType>(assertIs<LsiAnnotationValue.ClassValue>(aptNumberValue).type).kind,
        )
    }

    private fun compileKotlinAnnotation(): File {
        val projectDir = createTempDirectory(prefix = "jimmer-dto-kotlin-annotation-binary").toFile()
        val source = projectDir.resolve("src/main/kotlin/demo/Tags.kt").also { file ->
            file.parentFile.mkdirs()
            file.writeText(
                """
                    package demo

                    annotation class Tags(
                        val zebra: String = "",
                        val alpha: Int = 0,
                        vararg val value: String,
                    )
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
                testClasspath(),
                "-d",
                output.absolutePath,
                source.absolutePath,
            )
        }
        assertEquals(ExitCode.OK, exitCode, messages.toString(StandardCharsets.UTF_8))
        return output
    }

    private fun compileJavaAnnotation(): File {
        val projectDir = createTempDirectory(prefix = "jimmer-dto-java-annotation-binary").toFile()
        val source = projectDir.resolve("src/main/java/demo/JavaTags.java").also { file ->
            file.parentFile.mkdirs()
            file.writeText(
                """
                    package demo;

                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;

                    @Retention(RetentionPolicy.RUNTIME)
                    public @interface JavaTags {
                        Class<? extends Number> number() default Integer.class;
                        String pattern();
                        String[] value() default {};
                    }
                """.trimIndent()
            )
        }
        val output = projectDir.resolve("build/classes").apply(File::mkdirs)
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("Java binary annotation parity requires a JDK compiler")
        val success = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8).use { fileManager ->
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(output))
            val task = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                listOf("-proc:none", "-classpath", testClasspath()),
                null,
                fileManager.getJavaFileObjects(source),
            )
            task.call()
        }
        assertTrue(success, diagnostics.toErrorMessage())
        return output
    }

    private fun compileKspJavaAnnotationConsumer(library: File): LsiWorkspace {
        val projectDir = createTempDirectory(prefix = "jimmer-dto-java-annotation-ksp").toFile()
        val source = projectDir.resolve("src/main/kotlin/demo/KotlinJavaUse.kt").also { file ->
            file.parentFile.mkdirs()
            file.writeText(
                """
                    package demo

                    @JavaTags(pattern = "first")
                    interface KotlinJavaUse
                """.trimIndent()
            )
        }
        val output = projectDir.resolve("build/ksp").apply(File::mkdirs)
        val provider = CapturingKspProvider()
        val logger = CapturingKspLogger()
        val configuration = KSPJvmConfig.Builder().apply {
            moduleName = "dto-java-annotation-binary"
            sourceRoots = listOf(source)
            libraries = testClasspathFiles(listOf(library))
            projectBaseDir = projectDir
            outputBaseDir = output
            cachesDir = output.resolve("caches").apply(File::mkdirs)
            classOutputDir = output.resolve("classes").apply(File::mkdirs)
            javaOutputDir = output.resolve("java").apply(File::mkdirs)
            kotlinOutputDir = output.resolve("kotlin").apply(File::mkdirs)
            resourceOutputDir = output.resolve("resources").apply(File::mkdirs)
            languageVersion = "2.1"
            apiVersion = "2.1"
            jvmTarget = "17"
            jdkHome = File(System.getProperty("java.home"))
        }.build()
        val exitCode = KotlinSymbolProcessing(configuration, listOf(provider), logger).execute()
        assertEquals(KotlinSymbolProcessing.ExitCode.OK, exitCode, logger.messages.joinToString("\n"))
        return provider.workspaces.single { workspace -> workspace.declarations.isNotEmpty() }
    }

    private fun compileAptJavaAnnotationConsumer(library: File): LsiWorkspace {
        val projectDir = createTempDirectory(prefix = "jimmer-dto-java-annotation-apt").toFile()
        val source = projectDir.resolve("src/main/java/demo/JavaJavaUse.java").also { file ->
            file.parentFile.mkdirs()
            file.writeText(
                """
                    package demo;

                    @JavaTags(pattern = "first")
                    public interface JavaJavaUse {}
                """.trimIndent()
            )
        }
        val output = projectDir.resolve("build/classes").apply(File::mkdirs)
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val processor = CapturingAptProcessor()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("APT binary annotation parity requires a JDK compiler")
        val success = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8).use { fileManager ->
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(output))
            val task = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                listOf("-proc:only", "-classpath", testClasspath(listOf(library))),
                null,
                fileManager.getJavaFileObjects(source),
            )
            task.setProcessors(listOf(processor))
            task.call()
        }
        assertTrue(success, diagnostics.toErrorMessage())
        return processor.workspaces.single { workspace -> workspace.declarations.isNotEmpty() }
    }

    private fun compileAptConsumer(library: File): LsiWorkspace {
        val projectDir = createTempDirectory(prefix = "jimmer-dto-annotation-apt-binary").toFile()
        val source = projectDir.resolve("src/main/java/demo/JavaUse.java").also { file ->
            file.parentFile.mkdirs()
            file.writeText(
                """
                    package demo;

                    @Tags({"first", "second"})
                    public interface JavaUse {}
                """.trimIndent()
            )
        }
        val output = projectDir.resolve("build/classes").apply(File::mkdirs)
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val processor = CapturingAptProcessor()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("APT binary annotation parity requires a JDK compiler")
        val success = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8).use { fileManager ->
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(output))
            val task = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                listOf("-proc:only", "-classpath", testClasspath(listOf(library))),
                null,
                fileManager.getJavaFileObjects(source),
            )
            task.setProcessors(listOf(processor))
            task.call()
        }
        assertTrue(success, diagnostics.toErrorMessage())
        return processor.workspaces.single { workspace -> workspace.declarations.isNotEmpty() }
    }

    private fun compileKspConsumer(library: File): LsiWorkspace {
        val projectDir = createTempDirectory(prefix = "jimmer-dto-annotation-ksp-binary").toFile()
        val source = projectDir.resolve("src/main/kotlin/demo/KotlinUse.kt").also { file ->
            file.parentFile.mkdirs()
            file.writeText(
                """
                    package demo

                    @Tags(value = ["first", "second"])
                    interface KotlinUse
                """.trimIndent()
            )
        }
        val output = projectDir.resolve("build/ksp").apply(File::mkdirs)
        val provider = CapturingKspProvider()
        val logger = CapturingKspLogger()
        val configuration = KSPJvmConfig.Builder().apply {
            moduleName = "dto-annotation-binary-parity"
            sourceRoots = listOf(source)
            libraries = testClasspathFiles(listOf(library))
            projectBaseDir = projectDir
            outputBaseDir = output
            cachesDir = output.resolve("caches").apply(File::mkdirs)
            classOutputDir = output.resolve("classes").apply(File::mkdirs)
            javaOutputDir = output.resolve("java").apply(File::mkdirs)
            kotlinOutputDir = output.resolve("kotlin").apply(File::mkdirs)
            resourceOutputDir = output.resolve("resources").apply(File::mkdirs)
            languageVersion = "2.1"
            apiVersion = "2.1"
            jvmTarget = "17"
            jdkHome = File(System.getProperty("java.home"))
        }.build()
        val exitCode = KotlinSymbolProcessing(configuration, listOf(provider), logger).execute()

        assertEquals(KotlinSymbolProcessing.ExitCode.OK, exitCode, logger.messages.joinToString("\n"))
        return provider.workspaces.single { workspace -> workspace.declarations.isNotEmpty() }
    }

    private fun freezeContract(
        workspace: LsiWorkspace,
        baseTypeId: LsiSymbolId,
        dtoAnnotations: List<DtoAnnotation> = emptyList(),
    ): DtoAnnotationContract {
        val baseType = assertIs<LsiTypeDeclaration>(workspace[baseTypeId])
        val props = completeEntityProps(baseTypeId)
        val immutableSchema = ImmutableSchema(
            listOf(
                ImmutableType(
                    id = baseTypeId,
                    qualifiedName = baseType.qualifiedName,
                    kind = ImmutableTypeKind.ENTITY,
                    documentation = null,
                    annotations = baseType.annotations,
                    typeParameterIds = emptyList(),
                    superTypeIds = emptyList(),
                    props = props,
                    primarySuperTypeId = null,
                    inheritanceRootTypeId = null,
                    inheritanceStrategy = null,
                    joinedTableDissociateAction = null,
                    instantiable = true,
                    discriminatorValue = null,
                    discriminatorPropId = null,
                    idPropId = props.single().id,
                    versionPropId = null,
                    logicalDeletedPropId = null,
                    acrossMicroServices = false,
                    microServiceName = "",
                )
            )
        )
        val dtoType = DtoType(
            id = DTO_TYPE_ID,
            baseTypeId = baseTypeId,
            packageName = "demo.dto",
            name = "UseView",
            modifiers = emptySet(),
            annotations = dtoAnnotations,
            superInterfaces = emptyList(),
            documentation = null,
            location = DTO_LOCATION,
            focusedRecursion = false,
            propIds = emptyList(),
            hiddenFlatPropIds = emptyList(),
            polymorphism = null,
        )
        val graph = DtoGraph(
            source = DTO_SOURCE,
            rootTypeIds = listOf(DTO_TYPE_ID),
            types = listOf(dtoType),
            props = emptyList(),
        )
        val contract = workspace.resolveDtoAnnotationContract(graph, immutableSchema)
        assertTrue(contract.diagnostics.isEmpty(), contract.diagnostics.joinToString { diagnostic -> diagnostic.message })
        return contract
    }

    private fun testClasspath(additional: List<File> = emptyList()): String {
        return testClasspathFiles(additional).joinToString(File.pathSeparator)
    }

    private fun testClasspathFiles(additional: List<File> = emptyList()): List<File> {
        return (additional + System.getProperty("java.class.path")
            .split(File.pathSeparator)
            .map(::File))
            .filter(File::exists)
            .distinct()
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

    companion object {
        private val TAGS_ID = LsiSymbolId.type("demo.Tags")
        private val JAVA_ANNOTATION_ID = LsiSymbolId.type("demo.JavaTags")
        private val JAVA_INTEGER_ID = LsiSymbolId.type("java.lang.Integer")
        private val JAVA_USE_ID = LsiSymbolId.type("demo.JavaUse")
        private val KOTLIN_USE_ID = LsiSymbolId.type("demo.KotlinUse")
        private val JAVA_JAVA_USE_ID = LsiSymbolId.type("demo.JavaJavaUse")
        private val KOTLIN_JAVA_USE_ID = LsiSymbolId.type("demo.KotlinJavaUse")
        private val DTO_SOURCE = LsiSource.of("dto/Use.dto")
        private val DTO_LOCATION = LsiLocation(DTO_SOURCE, LsiPosition(1, 1))
        private val DTO_TYPE_ID = DtoTypeId("dto/Use.dto#root")
    }
}
