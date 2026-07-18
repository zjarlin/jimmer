package org.babyfish.jimmer.compiler.lsi.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.FileLocation
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Origin
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.KotlinVersion
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.babyfish.jimmer.compiler.JimmerCompilerCollectContext
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureCollection
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureDescriptor
import org.babyfish.jimmer.compiler.JimmerCompilerFeaturePrecompileResult
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureProvider
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureRenderResult
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureState
import org.babyfish.jimmer.compiler.JimmerCompilerPrecompileContext
import org.babyfish.jimmer.compiler.JimmerCompilerRenderContext
import org.babyfish.jimmer.compiler.CompilerInputDocumentKind
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.ArtifactKind
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.diagnostic.LsiDiagnosticSeverity

class KspLsiCompilerDriverTest {

    @Test
    fun `freezes dto documents from the current ksp project`() {
        val projectDirectory = createTempDirectory(prefix = "compiler-ksp-input-documents").toFile()
        val sourcePath = projectDirectory.resolve("src/main/kotlin/demo/Model.kt").also { file ->
            file.parentFile.mkdirs()
            file.writeText("interface Model")
        }
        projectDirectory.resolve("src/main/dto/Model.dto").also { file ->
            file.parentFile.mkdirs()
            file.writeText("export Model")
        }
        lateinit var sourceFile: KSFile
        val root = classDeclaration(
            qualifiedName = "demo.Valid",
            file = { sourceFile },
            valid = true,
        )
        sourceFile = file(listOf(root), sourcePath.absolutePath)
        val provider = InputDocumentFeatureProvider()
        val driver = KspLsiCompilerDriver(
            environment = SymbolProcessorEnvironment(
                emptyMap(),
                KotlinVersion.CURRENT,
                CapturingCodeGenerator(),
                CapturingLogger(),
            ),
            providers = listOf(provider),
            sessionId = "ksp-input-document-test",
        )

        driver.process(resolver(sourceFile))

        val document = provider.rounds.single().round.inputDocumentSnapshots.single().document
        assertEquals("export Model", document.content)
        assertEquals("src/main/dto", document.sourceRoot)
        assertEquals("Model.dto", document.relativePath)
    }

    @Test
    fun `finds dto documents when ksp source file has no root class`() {
        val projectDirectory = createTempDirectory(prefix = "compiler-ksp-input-documents").toFile()
        val sourcePath = projectDirectory.resolve("src/main/kotlin/demo/Functions.kt").also { file ->
            file.parentFile.mkdirs()
            file.writeText("fun execute() = Unit")
        }
        projectDirectory.resolve("src/main/dto/Model.dto").also { file ->
            file.parentFile.mkdirs()
            file.writeText("export Model")
        }
        val sourceFile = file(emptyList(), sourcePath.absolutePath)
        val provider = InputDocumentFeatureProvider()
        val driver = KspLsiCompilerDriver(
            environment = SymbolProcessorEnvironment(
                emptyMap(),
                KotlinVersion.CURRENT,
                CapturingCodeGenerator(),
                CapturingLogger(),
            ),
            providers = listOf(provider),
            sessionId = "ksp-input-document-no-root-test",
        )

        driver.process(resolver(sourceFile))

        assertEquals(
            "export Model",
            provider.rounds.single().round.inputDocumentSnapshots.single().document.content,
        )
    }

    @Test
    fun `freezes dto-only seeds with full and header modes`() {
        val projectDirectory = createTempDirectory(prefix = "compiler-ksp-document-seeds").toFile()
        val sourcePath = projectDirectory.resolve("src/main/kotlin/demo/Model.kt").also { file ->
            file.parentFile.mkdirs()
            file.writeText("interface Model")
        }
        projectDirectory.resolve("src/main/dto/Model.dto").also { file ->
            file.parentFile.mkdirs()
            file.writeText(
                """
                    export demo.Model
                    import demo.{Tag, Marker, Mode, Payload, SpecialModel}
                    @Tag
                    ModelView implements Marker {
                        mode: Mode
                        payload: Payload
                        #types {
                            SpecialModel { }
                        }
                    }
                """.trimIndent(),
            )
        }
        lateinit var sourceFile: KSFile
        val model = classDeclaration(
            qualifiedName = "demo.Model",
            file = { sourceFile },
            valid = true,
        )
        sourceFile = file(listOf(model), sourcePath.absolutePath)
        var tagDeclarationsRead = false
        var markerDeclarationsRead = false
        var modeDeclarationsRead = false
        var payloadDeclarationsRead = false
        var specialModelDeclarationsRead = false
        lateinit var externalFile: KSFile
        val tag = classDeclaration(
            qualifiedName = "demo.Tag",
            file = { externalFile },
            valid = true,
            onDeclarationsRead = { tagDeclarationsRead = true },
        )
        val marker = classDeclaration(
            qualifiedName = "demo.Marker",
            file = { externalFile },
            valid = true,
            onDeclarationsRead = { markerDeclarationsRead = true },
        )
        val active = classDeclaration(
            qualifiedName = "demo.Mode.ACTIVE",
            file = { externalFile },
            valid = true,
            classKind = ClassKind.ENUM_ENTRY,
            origin = Origin.KOTLIN_LIB,
        )
        val inactive = classDeclaration(
            qualifiedName = "demo.Mode.INACTIVE",
            file = { externalFile },
            valid = true,
            classKind = ClassKind.ENUM_ENTRY,
            origin = Origin.KOTLIN_LIB,
        )
        val mode = classDeclaration(
            qualifiedName = "demo.Mode",
            file = { externalFile },
            valid = true,
            onDeclarationsRead = { modeDeclarationsRead = true },
            classKind = ClassKind.ENUM_CLASS,
            origin = Origin.KOTLIN_LIB,
            declarations = { listOf(active, inactive) },
        )
        val payload = classDeclaration(
            qualifiedName = "demo.Payload",
            file = { externalFile },
            valid = true,
            onDeclarationsRead = { payloadDeclarationsRead = true },
        )
        val specialModel = classDeclaration(
            qualifiedName = "demo.SpecialModel",
            file = { externalFile },
            valid = true,
            onDeclarationsRead = { specialModelDeclarationsRead = true },
        )
        externalFile = file(
            declarations = listOf(tag, marker, mode, payload, specialModel),
            path = projectDirectory.resolve("dependencies/demo/Types.kt").absolutePath,
        )
        val provider = InputDocumentFeatureProvider()
        val driver = KspLsiCompilerDriver(
            environment = SymbolProcessorEnvironment(
                emptyMap(),
                KotlinVersion.CURRENT,
                CapturingCodeGenerator(),
                CapturingLogger(),
            ),
            providers = listOf(provider),
            sessionId = "ksp-document-seeds-test",
        )

        driver.process(
            resolver(
                allFiles = listOf(sourceFile),
                newFiles = listOf(sourceFile),
                knownTypes = listOf(tag, marker, mode, payload, specialModel).associateBy { declaration ->
                    requireNotNull(declaration.qualifiedName?.asString())
                },
            ),
        )

        val workspace = provider.rounds.single().round.workspace
        assertTrue(workspace.contains(LsiSymbolId.type("demo.Tag")))
        assertTrue(workspace.contains(LsiSymbolId.type("demo.Marker")))
        assertTrue(workspace.contains(LsiSymbolId.type("demo.Mode")))
        assertTrue(workspace.contains(LsiSymbolId.type("demo.Payload")))
        assertTrue(workspace.contains(LsiSymbolId.type("demo.SpecialModel")))
        assertTrue(tagDeclarationsRead)
        assertTrue(markerDeclarationsRead)
        assertTrue(modeDeclarationsRead)
        assertFalse(payloadDeclarationsRead)
        assertTrue(specialModelDeclarationsRead)
        assertEquals(
            listOf("ACTIVE", "INACTIVE"),
            assertIs<site.addzero.lsi.model.LsiTypeDeclaration>(
                workspace[LsiSymbolId.type("demo.Mode")],
            ).enumEntries.map { entry -> entry.name },
        )
    }

    @Test
    fun `uses all visible roots for workspace and new roots for current workspace`() {
        lateinit var existingFile: KSFile
        val existingRoot = classDeclaration(
            qualifiedName = "demo.Existing",
            file = { existingFile },
            valid = true,
        )
        existingFile = file(listOf(existingRoot))
        lateinit var currentFile: KSFile
        val currentRoot = classDeclaration(
            qualifiedName = "demo.Valid",
            file = { currentFile },
            valid = true,
        )
        currentFile = file(listOf(currentRoot))
        val provider = DriverFeatureProvider()
        val driver = KspLsiCompilerDriver(
            environment = SymbolProcessorEnvironment(
                emptyMap(),
                KotlinVersion.CURRENT,
                CapturingCodeGenerator(),
                CapturingLogger(),
            ),
            providers = listOf(provider),
            sessionId = "ksp-current-roots-test",
        )

        driver.process(
            resolver(
                allFiles = listOf(existingFile, currentFile),
                newFiles = listOf(currentFile),
            ),
        )

        val round = provider.rounds.single().round
        assertTrue(round.workspace.contains(LsiSymbolId.type("demo.Existing")))
        assertTrue(round.workspace.contains(LsiSymbolId.type("demo.Valid")))
        assertFalse(round.currentWorkspace.contains(LsiSymbolId.type("demo.Existing")))
        assertTrue(round.currentWorkspace.contains(LsiSymbolId.type("demo.Valid")))
        assertEquals(setOf(LsiSymbolId.type("demo.Valid")), round.currentRootTypeIds)
    }

    @Test
    fun `defers only invalid symbols then finishes without native symbols`() {
        var invalidDeclarationsRead = false
        lateinit var sourceFile: KSFile
        val validRoot = classDeclaration(
            qualifiedName = "demo.Valid",
            file = { sourceFile },
            valid = true,
        )
        val invalidRoot = classDeclaration(
            qualifiedName = "demo.Invalid",
            file = { sourceFile },
            valid = false,
            onDeclarationsRead = { invalidDeclarationsRead = true },
        )
        sourceFile = file(listOf(validRoot, invalidRoot))
        val resolver = resolver(
            sourceFile,
            knownTypes = mapOf(VALID_ID.requireTypeQualifiedName() to validRoot),
        )
        val codeGenerator = CapturingCodeGenerator()
        val logger = CapturingLogger()
        val provider = DriverFeatureProvider()
        val environment = SymbolProcessorEnvironment(
            emptyMap(),
            KotlinVersion.CURRENT,
            codeGenerator,
            logger,
        )
        val driver = KspLsiCompilerDriver(
            environment = environment,
            providers = listOf(provider),
            sessionId = "ksp-driver-test",
        )

        val deferred = driver.process(resolver)

        assertEquals(1, deferred.size)
        assertSame(invalidRoot, deferred.single())
        assertFalse(invalidDeclarationsRead)
        val sourceCall = codeGenerator.calls.single()
        assertEquals("demo/ValidGenerated", sourceCall.path)
        assertEquals("kt", sourceCall.extension)
        assertFalse(sourceCall.dependencies.aggregating)
        assertSame(sourceFile, sourceCall.dependencies.originatingFiles.single())
        val warning = logger.calls.single { call -> call.severity == LsiDiagnosticSeverity.WARNING }
        assertTrue(warning.message.contains("[driver.warning]"))
        assertSame(validRoot, warning.symbol)

        val finalRound = driver.finish()

        assertTrue(finalRound.round.isFinal)
        assertTrue(finalRound.round.workspace.contains(LsiSymbolId.type("demo.Valid")))
        assertTrue(finalRound.round.currentWorkspace.declarations.isEmpty())
        assertTrue(finalRound.round.currentRootTypeIds.isEmpty())
        assertEquals(setOf(VALID_ID), provider.rounds.first().round.availableTypeIds)
        assertEquals(setOf(VALID_ID), finalRound.round.availableTypeIds)
        val resourceCall = codeGenerator.calls.last()
        assertEquals("META-INF/jimmer/driver-final", resourceCall.path)
        assertEquals("", resourceCall.extension)
        assertTrue(resourceCall.dependencies.aggregating)
        assertTrue(resourceCall.dependencies.originatingFiles.isEmpty())
        assertTrue(provider.rounds.last().round.isFinal)
        assertTrue(provider.rounds.last().round.workspace.contains(LsiSymbolId.type("demo.Valid")))
        assertTrue(
            driver.javaClass.declaredFields.none { field ->
                Resolver::class.java.isAssignableFrom(field.type) ||
                    KSAnnotated::class.java.isAssignableFrom(field.type) ||
                    KSFile::class.java.isAssignableFrom(field.type)
            },
        )
    }

    private class DriverFeatureProvider : JimmerCompilerFeatureProvider {
        override val descriptor = JimmerCompilerFeatureDescriptor(
            id = "ksp-driver-test",
            classpathTypeIds = setOf(VALID_ID, MISSING_TYPE_ID),
            inputDocumentKinds = setOf(CompilerInputDocumentKind.DTO),
        )

        val rounds = mutableListOf<JimmerCompilerCollectContext>()

        override fun collect(context: JimmerCompilerCollectContext): JimmerCompilerFeatureCollection {
            rounds += context
            return JimmerCompilerFeatureCollection()
        }

        override fun precompile(
            context: JimmerCompilerPrecompileContext,
        ): JimmerCompilerFeaturePrecompileResult {
            return JimmerCompilerFeaturePrecompileResult(
                state = DriverFeatureState("${context.round.number}:${context.round.isFinal}"),
                unresolvedSymbols = if (context.round.isFinal) emptySet() else setOf(VALID_ID),
            )
        }

        override fun render(
            context: JimmerCompilerRenderContext,
        ): JimmerCompilerFeatureRenderResult {
            if (context.round.isFinal) {
                return JimmerCompilerFeatureRenderResult(
                    artifacts = listOf(
                        GeneratedArtifact.create(
                            kind = ArtifactKind.RESOURCE,
                            path = "META-INF/jimmer/driver-final",
                            content = "final",
                            aggregationMode = ArtifactAggregationMode.AGGREGATING,
                        ),
                    ),
                )
            }
            return JimmerCompilerFeatureRenderResult(
                artifacts = listOf(
                    GeneratedArtifact.source(
                        kind = ArtifactKind.KOTLIN_SOURCE,
                        qualifiedName = "demo.ValidGenerated",
                        content = "package demo\ninterface ValidGenerated",
                        aggregationMode = ArtifactAggregationMode.ISOLATING,
                        originatingSymbols = setOf(VALID_ID),
                    ),
                ),
                diagnostics = listOf(
                    LsiDiagnostic(
                        code = "driver.warning",
                        severity = LsiDiagnosticSeverity.WARNING,
                        message = "valid root deferred",
                        symbolId = VALID_ID,
                    ),
                ),
            )
        }
    }

    private class InputDocumentFeatureProvider : JimmerCompilerFeatureProvider {
        override val descriptor = JimmerCompilerFeatureDescriptor(
            id = "ksp-input-document-test",
            inputDocumentKinds = setOf(CompilerInputDocumentKind.DTO),
        )

        val rounds = mutableListOf<JimmerCompilerCollectContext>()

        override fun collect(context: JimmerCompilerCollectContext): JimmerCompilerFeatureCollection {
            rounds += context
            return JimmerCompilerFeatureCollection()
        }

        override fun precompile(
            context: JimmerCompilerPrecompileContext,
        ): JimmerCompilerFeaturePrecompileResult {
            return JimmerCompilerFeaturePrecompileResult(
                state = DriverFeatureState("${context.round.number}:${context.round.isFinal}"),
            )
        }
    }

    private class CapturingLogger : KSPLogger {
        val calls = mutableListOf<LogCall>()

        override fun logging(message: String, symbol: KSNode?) = Unit

        override fun info(message: String, symbol: KSNode?) {
            calls += LogCall(LsiDiagnosticSeverity.INFO, message, symbol)
        }

        override fun warn(message: String, symbol: KSNode?) {
            calls += LogCall(LsiDiagnosticSeverity.WARNING, message, symbol)
        }

        override fun error(message: String, symbol: KSNode?) {
            calls += LogCall(LsiDiagnosticSeverity.ERROR, message, symbol)
        }

        override fun exception(e: Throwable) {
            throw e
        }
    }

    private class CapturingCodeGenerator : CodeGenerator {
        val calls = mutableListOf<WriteCall>()

        override fun createNewFile(
            dependencies: Dependencies,
            packageName: String,
            fileName: String,
            extensionName: String,
        ): OutputStream = error("Package-based output is not supported by this test generator")

        override fun createNewFileByPath(
            dependencies: Dependencies,
            path: String,
            extensionName: String,
        ): OutputStream {
            val output = ByteArrayOutputStream()
            calls += WriteCall(dependencies, path, extensionName, output)
            return output
        }

        override fun associate(
            sources: List<KSFile>,
            packageName: String,
            fileName: String,
            extensionName: String,
        ) = Unit

        override fun associateByPath(
            sources: List<KSFile>,
            path: String,
            extensionName: String,
        ) = Unit

        override fun associateWithClasses(
            classes: List<KSClassDeclaration>,
            packageName: String,
            fileName: String,
            extensionName: String,
        ) = Unit

        override val generatedFile: Collection<File> = emptyList()
    }

    private data class DriverFeatureState(
        override val fingerprint: String,
    ) : JimmerCompilerFeatureState

    private data class LogCall(
        val severity: LsiDiagnosticSeverity,
        val message: String,
        val symbol: KSNode?,
    )

    private data class WriteCall(
        val dependencies: Dependencies,
        val path: String,
        val extension: String,
        val output: ByteArrayOutputStream,
    )

    private fun resolver(
        file: KSFile,
        knownTypes: Map<String, KSClassDeclaration> = emptyMap(),
    ): Resolver {
        return resolver(listOf(file), listOf(file), knownTypes)
    }

    private fun resolver(
        allFiles: List<KSFile>,
        newFiles: List<KSFile>,
        knownTypes: Map<String, KSClassDeclaration> = emptyMap(),
    ): Resolver {
        return proxy(Resolver::class.java, "Resolver") { method, arguments ->
            when (method.name) {
                "getAllFiles" -> allFiles.asSequence()
                "getNewFiles" -> newFiles.asSequence()
                "getKSNameFromString" -> name(arguments.first() as String)
                "getClassDeclarationByName" -> {
                    val qualifiedName = (arguments.first() as KSName).asString()
                    knownTypes[qualifiedName]
                }
                "getJvmCheckedException" -> emptySequence<KSType>()
                "overrides" -> false
                else -> UNHANDLED
            }
        }
    }

    private fun file(
        declarations: List<KSClassDeclaration>,
        path: String = "/workspace/src/main/kotlin/demo/Models.kt",
    ): KSFile {
        return proxy(KSFile::class.java, "KSFile($path)") { method, _ ->
            when (method.name) {
                "getPackageName" -> name("demo")
                "getFileName" -> "Models.kt"
                "getFilePath" -> path
                "getDeclarations" -> declarations.asSequence()
                "getAnnotations" -> emptySequence<KSAnnotation>()
                "getOrigin" -> Origin.KOTLIN
                "getLocation" -> FileLocation(path, 1)
                "getParent" -> null
                "accept" -> true
                else -> UNHANDLED
            }
        }
    }

    private fun classDeclaration(
        qualifiedName: String,
        file: () -> KSFile,
        valid: Boolean,
        onDeclarationsRead: () -> Unit = {},
        classKind: ClassKind = ClassKind.INTERFACE,
        origin: Origin = Origin.KOTLIN,
        declarations: () -> List<KSClassDeclaration> = { emptyList() },
    ): KSClassDeclaration {
        return proxy(KSClassDeclaration::class.java, "KSClassDeclaration($qualifiedName)") { method, _ ->
            when (method.name) {
                "getSimpleName" -> name(qualifiedName.substringAfterLast('.'))
                "getQualifiedName" -> name(qualifiedName)
                "getPackageName" -> name(qualifiedName.substringBeforeLast('.', ""))
                "getClassKind" -> classKind
                "getOrigin" -> origin
                "getContainingFile" -> file()
                "getParentDeclaration" -> null
                "getParent" -> file()
                "getTypeParameters" -> emptyList<com.google.devtools.ksp.symbol.KSTypeParameter>()
                "getDeclarations" -> {
                    onDeclarationsRead()
                    declarations().asSequence()
                }
                "getSuperTypes" -> emptySequence<KSTypeReference>()
                "getAnnotations" -> emptySequence<KSAnnotation>()
                "getModifiers" -> emptySet<Modifier>()
                "getDocString" -> null
                "getLocation" -> FileLocation(file().filePath, 1)
                "isCompanionObject" -> false
                "getPrimaryConstructor" -> null
                "getSealedSubclasses" -> emptySequence<KSClassDeclaration>()
                "accept" -> valid
                else -> UNHANDLED
            }
        }
    }

    private fun name(value: String): KSName {
        return proxy(KSName::class.java, "KSName($value)") { method, _ ->
            when (method.name) {
                "asString" -> value
                "getQualifier" -> value.substringBeforeLast('.', "")
                "getShortName" -> value.substringAfterLast('.')
                else -> UNHANDLED
            }
        }
    }

    private fun <T : Any> proxy(
        type: Class<T>,
        label: String,
        handler: (Method, Array<out Any?>) -> Any?,
    ): T {
        lateinit var instance: Any
        instance = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, arguments ->
            when (method.name) {
                "equals" -> instance === arguments?.firstOrNull()
                "hashCode" -> System.identityHashCode(instance)
                "toString" -> label
                else -> handler(method, arguments ?: emptyArray()).let { result ->
                    if (result === UNHANDLED) defaultValue(method.returnType) else result
                }
            }
        }
        return type.cast(instance)
    }

    private fun defaultValue(type: Class<*>): Any? {
        return when {
            type == Boolean::class.javaPrimitiveType -> false
            type == Byte::class.javaPrimitiveType -> 0.toByte()
            type == Short::class.javaPrimitiveType -> 0.toShort()
            type == Int::class.javaPrimitiveType -> 0
            type == Long::class.javaPrimitiveType -> 0L
            type == Float::class.javaPrimitiveType -> 0F
            type == Double::class.javaPrimitiveType -> 0.0
            type == Char::class.javaPrimitiveType -> '\u0000'
            Sequence::class.java.isAssignableFrom(type) -> emptySequence<Any>()
            List::class.java.isAssignableFrom(type) -> emptyList<Any>()
            Set::class.java.isAssignableFrom(type) -> emptySet<Any>()
            Map::class.java.isAssignableFrom(type) -> emptyMap<Any, Any>()
            else -> null
        }
    }

    companion object {
        private val VALID_ID = LsiSymbolId.type("demo.Valid")

        private val MISSING_TYPE_ID = LsiSymbolId.type("missing.NotThere")

        private val UNHANDLED = Any()
    }
}
