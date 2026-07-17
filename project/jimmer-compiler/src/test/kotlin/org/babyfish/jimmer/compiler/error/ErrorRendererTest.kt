package org.babyfish.jimmer.compiler.error

import java.nio.charset.StandardCharsets
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.babyfish.jimmer.compiler.error.apt.ErrorJavaRenderer
import org.babyfish.jimmer.compiler.error.ksp.ErrorKotlinRenderer
import site.addzero.lsi.codegen.ArtifactKind
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiWorkspace

class ErrorRendererTest {

    @Test
    fun `renders compiling java error source and isolating origin`() {
        val (schema, workspace) = fixture(LsiLanguage.JAVA)
        val artifact = ErrorJavaRenderer().render(schema, workspace).single()

        assertEquals(ArtifactKind.JAVA_SOURCE, artifact.kind)
        assertEquals("demo/BookException.java", artifact.path)
        assertEquals(setOf(FAMILY_ID), artifact.originatingSymbols)
        assertEquals(workspace.sources.toSet(), artifact.originatingSources)
        assertContains(artifact.content, "abstract class BookException")
        assertContains(artifact.content, "class OutOfRange extends BookException")
        assertContains(artifact.content, "@ClientException")
        assertContains(artifact.content, "return BookErrorCode.OUT_OF_RANGE")
        compileJava(artifact.content)
    }

    @Test
    fun `renders kotlin error source with equivalent metadata`() {
        val (schema, workspace) = fixture(LsiLanguage.KOTLIN)
        val artifact = ErrorKotlinRenderer().render(schema, workspace).single()

        assertEquals(ArtifactKind.KOTLIN_SOURCE, artifact.kind)
        assertEquals("demo/BookException.kt", artifact.path)
        assertEquals(setOf(FAMILY_ID), artifact.originatingSymbols)
        assertContains(artifact.content, "abstract class BookException")
        assertContains(artifact.content, "class OutOfRange")
        assertContains(artifact.content, "family = \"BOOK\"")
        assertContains(artifact.content, "code = \"OUT_OF_RANGE\"")
        assertContains(artifact.content, "BookErrorCode.OUT_OF_RANGE")
        assertContains(artifact.content, ") : BookException(message, cause, timestamp)")
        assertContains(artifact.content, "\"timestamp\" to timestamp")
        assertContains(artifact.content, "\"min\" to min")
    }

    private fun fixture(language: LsiLanguage): Pair<ErrorPrecompiledSchema, LsiWorkspace> {
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
        val shared = ErrorFieldModel(
            name = "timestamp",
            type = LsiDeclaredType(LsiSymbolId.type("java.time.LocalDateTime")),
            list = false,
            nullable = false,
            documentation = "Created time",
            declaredBy = FAMILY_ID,
        )
        val min = ErrorFieldModel(
            name = "min",
            type = LsiPrimitiveType(LsiPrimitiveKind.INT),
            list = false,
            nullable = false,
            documentation = null,
            declaredBy = CODE_ID,
        )
        val code = ErrorCodeModel(
            id = CODE_ID,
            enumEntryName = "OUT_OF_RANGE",
            code = "OUT_OF_RANGE",
            creatorName = "outOfRange",
            exceptionTypeId = LsiSymbolId.type("demo.BookException.OutOfRange"),
            exceptionSimpleName = "OutOfRange",
            documentation = "Out of range.",
            declaredFields = listOf(min),
            fields = listOf(shared, min),
        )
        return ErrorPrecompiledSchema(
            families = listOf(
                ErrorFamilyModel(
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

    private companion object {
        val FAMILY_ID = LsiSymbolId.type("demo.BookErrorCode")
        val CODE_ID = LsiSymbolId.enumEntry(FAMILY_ID, "OUT_OF_RANGE")
    }
}
