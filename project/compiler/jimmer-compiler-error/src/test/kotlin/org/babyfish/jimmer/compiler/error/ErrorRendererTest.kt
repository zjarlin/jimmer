package org.babyfish.jimmer.compiler.error

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.ArtifactKind
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.error.ErrorCode
import site.addzero.lsi.jimmer.error.ErrorFamily
import site.addzero.lsi.jimmer.error.ErrorField
import site.addzero.lsi.jimmer.error.ErrorSchema
import site.addzero.lsi.type.LsiArrayType
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.type.LsiPrimitiveKind
import site.addzero.lsi.type.LsiPrimitiveType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

class ErrorRendererTest {

    @Test
    fun `java renderer matches frozen baseline and compiles`() {
        val (schema, workspace) = fixture(LsiLanguage.JAVA)
        val artifact = LsiJavaPoetRenderer().render(
            schema.toLsiSourceArtifacts(workspace).single()
        )

        assertEquals(ArtifactKind.JAVA_SOURCE, artifact.kind)
        assertEquals(ArtifactAggregationMode.ISOLATING, artifact.aggregationMode)
        assertEquals("demo/BookException.java", artifact.path)
        assertEquals(setOf(FAMILY_ID), artifact.originatingSymbols)
        assertEquals(workspace.sources.toSet(), artifact.originatingSources)
        assertDependencyContract(schema, artifact.dependencySymbols)
        assertEquals(workspace.sources.toSet(), artifact.dependencySources)
        assertContentEquals(golden("apt/BookException.java"), artifact.content.encodeToByteArray())
        compileJava(artifact.content)
    }

    @Test
    fun `kotlin renderer matches frozen baseline and compiles`() {
        val (schema, workspace) = fixture(LsiLanguage.KOTLIN)
        val artifact = LsiKotlinPoetRenderer().render(
            schema.toLsiSourceArtifacts(workspace).single()
        )

        assertEquals(ArtifactKind.KOTLIN_SOURCE, artifact.kind)
        assertEquals(ArtifactAggregationMode.ISOLATING, artifact.aggregationMode)
        assertEquals("demo/BookException.kt", artifact.path)
        assertEquals(setOf(FAMILY_ID), artifact.originatingSymbols)
        assertEquals(workspace.sources.toSet(), artifact.originatingSources)
        assertDependencyContract(schema, artifact.dependencySymbols)
        assertEquals(workspace.sources.toSet(), artifact.dependencySources)
        assertContentEquals(golden("ksp/BookException.kt"), artifact.content.encodeToByteArray())
        compileKotlin(artifact.content)
    }

    @Test
    fun `cross source field dependency makes artifact aggregating`() {
        val (schema, workspace) = fixture(LsiLanguage.JAVA)
        val dependencyId = LsiSymbolId.type("demo.Timestamp")
        val dependencySource = LsiSource.of("demo/Timestamp.java", LsiLanguage.JAVA)
        val dependencyWorkspace = LsiWorkspace(
            sources = workspace.sources + dependencySource,
            declarations = workspace.declarations + site.addzero.lsi.model.LsiTypeDeclaration(
                id = dependencyId,
                name = "Timestamp",
                qualifiedName = "demo.Timestamp",
                kind = site.addzero.lsi.model.LsiTypeDeclarationKind.CLASS,
                origin = LsiOrigin(LsiOriginKind.SOURCE, dependencySource),
            ),
        )
        val family = schema.families.single()
        val sharedField = family.declaredFields.single().copy(type = LsiDeclaredType(dependencyId))
        val dependentSchema = ErrorSchema(
            listOf(
                family.copy(
                    declaredFields = listOf(sharedField),
                    codes = family.codes,
                )
            )
        )

        val artifact = dependentSchema.toLsiSourceArtifacts(dependencyWorkspace).single()

        assertEquals(ArtifactAggregationMode.AGGREGATING, artifact.aggregationMode)
        assertTrue(dependencyId in artifact.dependencySymbols)
        assertEquals(dependencyWorkspace.sources.toSet(), artifact.dependencySources)
    }

    private fun fixture(language: LsiLanguage): Pair<ErrorSchema, LsiWorkspace> {
        val source = LsiSource.of(
            "demo/BookErrorCode.${if (language == LsiLanguage.JAVA) "java" else "kt"}",
            language,
        )
        val workspace = LsiWorkspace(
            sources = listOf(source),
            declarations = listOf(
                site.addzero.lsi.model.LsiTypeDeclaration(
                    id = FAMILY_ID,
                    name = "BookErrorCode",
                    qualifiedName = "demo.BookErrorCode",
                    kind = site.addzero.lsi.model.LsiTypeDeclarationKind.ENUM,
                    origin = LsiOrigin(LsiOriginKind.SOURCE, source),
                )
            ),
        )
        val shared = ErrorField(
            name = "timestamp",
            type = LsiDeclaredType(LsiSymbolId.type("java.time.LocalDateTime")),
            list = false,
            nullable = false,
            documentation = "Created time",
            declaredBy = FAMILY_ID,
        )
        val min = ErrorField(
            name = "min",
            type = LsiPrimitiveType(LsiPrimitiveKind.INT),
            list = false,
            nullable = false,
            documentation = null,
            declaredBy = CODE_ID,
        )
        val label = ErrorField(
            name = "label",
            type = LsiDeclaredType(LsiSymbolId.type("java.lang.String")),
            list = false,
            nullable = false,
            documentation = null,
            declaredBy = CODE_ID,
        )
        val primitiveValues = ErrorField(
            name = "primitiveValues",
            type = LsiArrayType(LsiPrimitiveType(LsiPrimitiveKind.INT)),
            list = false,
            nullable = false,
            documentation = null,
            declaredBy = CODE_ID,
        )
        val boxedValues = ErrorField(
            name = "boxedValues",
            type = LsiArrayType(LsiPrimitiveType(LsiPrimitiveKind.INT, boxed = true)),
            list = false,
            nullable = false,
            documentation = null,
            declaredBy = CODE_ID,
        )
        val code = ErrorCode(
            id = CODE_ID,
            enumEntryName = "OUT_OF_RANGE",
            code = "OUT_OF_RANGE",
            creatorName = "outOfRange",
            exceptionTypeId = LsiSymbolId.type("demo.BookException.OutOfRange"),
            exceptionSimpleName = "OutOfRange",
            documentation = "Out of range.",
            declaredFields = listOf(min, label, primitiveValues, boxedValues),
        )
        return ErrorSchema(
            families = listOf(
                ErrorFamily(
                    id = FAMILY_ID,
                    qualifiedName = "demo.BookErrorCode",
                    packageName = "demo",
                    family = "BOOK",
                    exceptionTypeId = LsiSymbolId.type("demo.BookException"),
                    exceptionSimpleName = "BookException",
                    checkedException = false,
                    documentation = "Book errors.",
                    declaredFields = listOf(shared),
                    codes = listOf(code),
                )
            )
        ) to workspace
    }

    private fun compileJava(content: String) {
        val projectDir = createTempDirectory(prefix = "jimmer-error-renderer-test").toFile()
        val source = projectDir.resolve("src/demo/BookException.java")
        val enumSource = projectDir.resolve("src/demo/BookErrorCode.java")
        val output = projectDir.resolve("classes")
        source.parentFile.mkdirs()
        source.writeText(content)
        enumSource.writeText("package demo; public enum BookErrorCode { OUT_OF_RANGE }")
        output.mkdirs()
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val compiler = ToolProvider.getSystemJavaCompiler() ?: error("Renderer test requires a JDK compiler")
        val success = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8).use { manager ->
            manager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(output))
            compiler.getTask(
                null,
                manager,
                diagnostics,
                listOf("-classpath", System.getProperty("java.class.path")),
                null,
                manager.getJavaFileObjects(source, enumSource),
            ).call()
        }
        assertTrue(success, diagnostics.diagnostics.joinToString("\n"))
    }

    private fun compileKotlin(content: String) {
        val projectDir = createTempDirectory(prefix = "jimmer-error-kotlin-renderer-test").toFile()
        val sourceRoot = projectDir.resolve("src/demo").apply { mkdirs() }
        val output = projectDir.resolve("classes").apply { mkdirs() }
        val generatedSource = sourceRoot.resolve("BookException.kt").apply { writeText(content) }
        val enumSource = sourceRoot.resolve("BookErrorCode.kt").apply {
            writeText("package demo\nenum class BookErrorCode { OUT_OF_RANGE }")
        }
        val messages = ByteArrayOutputStream()
        val exitCode = PrintStream(messages, true, StandardCharsets.UTF_8).use { stream ->
            K2JVMCompiler().exec(
                stream,
                "-no-stdlib",
                "-no-reflect",
                "-classpath",
                System.getProperty("java.class.path"),
                "-d",
                output.absolutePath,
                generatedSource.absolutePath,
                enumSource.absolutePath,
            )
        }
        assertEquals(ExitCode.OK, exitCode, messages.toString(StandardCharsets.UTF_8))
    }

    private fun golden(path: String): ByteArray {
        return requireNotNull(javaClass.getResourceAsStream("/error/$path")).use { input ->
            input.readBytes()
        }
    }

    private fun assertDependencyContract(
        schema: ErrorSchema,
        dependencySymbols: Set<LsiSymbolId>,
    ) {
        val family = schema.families.single()
        val code = family.codes.single()
        val expected = buildSet {
            add(family.id)
            add(code.id)
            add(LsiSymbolId.type("org.babyfish.jimmer.ClientException"))
            add(LsiSymbolId.type("org.babyfish.jimmer.internal.GeneratedBy"))
            add(LsiSymbolId.type("com.fasterxml.jackson.annotation.JsonIgnore"))
            family.declaredFields.mapTo(this, ErrorField::declaredBy)
            code.declaredFields.mapTo(this, ErrorField::declaredBy)
        }
        assertTrue(dependencySymbols.containsAll(expected), dependencySymbols.toString())
        assertFalse(family.exceptionTypeId in dependencySymbols, dependencySymbols.toString())
        assertFalse(code.exceptionTypeId in dependencySymbols, dependencySymbols.toString())
    }

    private companion object {
        val FAMILY_ID = LsiSymbolId.type("demo.BookErrorCode")
        val CODE_ID = LsiSymbolId.enumEntry(FAMILY_ID, "OUT_OF_RANGE")
    }
}
