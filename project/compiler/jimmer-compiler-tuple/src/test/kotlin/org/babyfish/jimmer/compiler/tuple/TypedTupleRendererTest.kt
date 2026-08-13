package org.babyfish.jimmer.compiler.tuple

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
import site.addzero.lsi.jimmer.tuple.TypedTupleConstructorArgument
import site.addzero.lsi.jimmer.tuple.TypedTupleBaseTableProjection
import site.addzero.lsi.jimmer.tuple.TypedTupleBaseTableSelection
import site.addzero.lsi.jimmer.tuple.TypedTupleBaseTableSelectionKind
import site.addzero.lsi.jimmer.tuple.TypedTupleJavaSetterConstruction
import site.addzero.lsi.jimmer.tuple.TypedTupleKotlinConstructorConstruction
import site.addzero.lsi.jimmer.tuple.TypedTupleProperty
import site.addzero.lsi.jimmer.tuple.TypedTupleScalarCategory
import site.addzero.lsi.jimmer.tuple.TypedTupleSchema
import site.addzero.lsi.jimmer.tuple.TypedTupleSetterAssignment
import site.addzero.lsi.jimmer.tuple.TypedTupleType
import site.addzero.lsi.jimmer.tuple.dependencySymbolIds
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.type.LsiPrimitiveKind
import site.addzero.lsi.type.LsiPrimitiveType
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

class TypedTupleRendererTest {

    @Test
    fun `java renderer matches legacy golden and compiles`() {
        val fixture = fixture(LsiLanguage.JAVA)
        val artifacts = fixture.schema
            .toLsiSourceArtifacts(fixture.workspace)
            .map(LsiJavaPoetRenderer()::render)
            .associateBy { artifact -> artifact.path }
        val mapperArtifact = artifacts.getValue("demo/BookSummaryMapper.java")
        val tableArtifact = artifacts.getValue("demo/BookSummaryTable.java")

        assertEquals(ArtifactKind.JAVA_SOURCE, mapperArtifact.kind)
        assertEquals(ArtifactAggregationMode.ISOLATING, mapperArtifact.aggregationMode)
        assertEquals(setOf(TUPLE_ID), mapperArtifact.originatingSymbols)
        assertEquals(setOf(fixture.tupleSource), mapperArtifact.originatingSources)
        assertEquals(fixture.dependencySymbols, mapperArtifact.dependencySymbols)
        assertEquals(setOf(fixture.tupleSource), mapperArtifact.dependencySources)
        assertEquals(mapperArtifact.originatingSymbols, tableArtifact.originatingSymbols)
        assertEquals(mapperArtifact.dependencySymbols, tableArtifact.dependencySymbols)
        assertContentEquals(golden("BookSummaryMapper.java"), mapperArtifact.content.encodeToByteArray())
        assertContentEquals(golden("BookSummaryTable.java"), tableArtifact.content.encodeToByteArray())
        compileJava(mapperArtifact.content, tableArtifact.content)
    }

    @Test
    fun `kotlin renderer matches legacy golden and compiles`() {
        val fixture = fixture(LsiLanguage.KOTLIN)
        val artifact = fixture.schema
            .toLsiSourceArtifacts(fixture.workspace)
            .map(LsiKotlinPoetRenderer()::render)
            .single()

        assertEquals(ArtifactKind.KOTLIN_SOURCE, artifact.kind)
        assertEquals(ArtifactAggregationMode.ISOLATING, artifact.aggregationMode)
        assertEquals("demo/BookSummaryMapper.kt", artifact.path)
        assertEquals(setOf(TUPLE_ID), artifact.originatingSymbols)
        assertEquals(setOf(fixture.tupleSource), artifact.originatingSources)
        assertEquals(fixture.dependencySymbols, artifact.dependencySymbols)
        assertEquals(setOf(fixture.tupleSource), artifact.dependencySources)
        assertContentEquals(golden("BookSummaryMapper.kt"), artifact.content.encodeToByteArray())
        compileKotlin(artifact.content)
    }

    private fun fixture(language: LsiLanguage): Fixture {
        val extension = if (language == LsiLanguage.JAVA) "java" else "kt"
        val tupleSource = LsiSource.of("demo/BookSummary.$extension", language)
        val unrelatedSource = LsiSource.of("demo/Unrelated.$extension", language)
        val origin = LsiOrigin(LsiOriginKind.SOURCE, tupleSource)
        val tupleDeclaration = LsiClass(
            id = TUPLE_ID,
            name = "BookSummary",
            qualifiedName = "demo.BookSummary",
            kind = LsiTypeDeclarationKind.CLASS,
            origin = origin,
        )
        val bookViewDeclaration = LsiClass(
            id = BOOK_VIEW_ID,
            name = "BookView",
            qualifiedName = "demo.BookView",
            kind = LsiTypeDeclarationKind.CLASS,
            origin = origin,
        )
        val sourceMemberIds = if (language == LsiLanguage.JAVA) {
            listOf(
                LsiSymbolId.field(TUPLE_ID, "book"),
                LsiSymbolId.field(TUPLE_ID, "authorCount"),
            )
        } else {
            listOf(
                LsiSymbolId.property(TUPLE_ID, "book"),
                LsiSymbolId.property(TUPLE_ID, "authorCount"),
            )
        }
        val properties = listOf(
            TypedTupleProperty(
                id = LsiSymbolId.property(TUPLE_ID, "book"),
                sourceMemberId = sourceMemberIds[0],
                name = "book",
                index = 0,
                type = BOOK_VIEW_TYPE,
            ),
            TypedTupleProperty(
                id = LsiSymbolId.property(TUPLE_ID, "authorCount"),
                sourceMemberId = sourceMemberIds[1],
                name = "authorCount",
                index = 1,
                type = LONG_TYPE,
            ),
        )
        val construction = when (language) {
            LsiLanguage.JAVA -> TypedTupleJavaSetterConstruction(
                constructorId = null,
                assignments = listOf(
                    TypedTupleSetterAssignment(sourceMemberIds[0], 0, "setBook"),
                    TypedTupleSetterAssignment(sourceMemberIds[1], 1, "setAuthorCount"),
                ),
            )
            LsiLanguage.KOTLIN -> {
                val constructorId = LsiSymbolId.constructor(
                    TUPLE_ID,
                    listOf("type:demo.BookView", "primitive:long"),
                )
                TypedTupleKotlinConstructorConstruction(
                    constructorId = constructorId,
                    arguments = listOf(
                        TypedTupleConstructorArgument(
                            sourceMemberId = sourceMemberIds[0],
                            propertyIndex = 0,
                            parameterId = LsiSymbolId.parameter(constructorId, 0, "book"),
                            parameterIndex = 0,
                            parameterName = "book",
                        ),
                        TypedTupleConstructorArgument(
                            sourceMemberId = sourceMemberIds[1],
                            propertyIndex = 1,
                            parameterId = LsiSymbolId.parameter(constructorId, 1, "authorCount"),
                            parameterIndex = 1,
                            parameterName = "authorCount",
                        ),
                    ),
                )
            }
            LsiLanguage.UNKNOWN -> error("测试夹具仅支持 Java 或 Kotlin")
        }
        val schema = TypedTupleSchema(
            tuples = listOf(
                TypedTupleType(
                    id = TUPLE_ID,
                    qualifiedName = "demo.BookSummary",
                    packageName = "demo",
                    simpleName = "BookSummary",
                    sourceLanguage = language,
                    properties = properties,
                    construction = construction,
                    baseTableProjection = TypedTupleBaseTableProjection(
                        selections = listOf(
                            TypedTupleBaseTableSelection(
                                propertyIndex = 0,
                                kind = TypedTupleBaseTableSelectionKind.NON_NULL_EXPRESSION,
                                scalarCategory = TypedTupleScalarCategory.GENERIC,
                            ),
                            TypedTupleBaseTableSelection(
                                propertyIndex = 1,
                                kind = TypedTupleBaseTableSelectionKind.NON_NULL_EXPRESSION,
                                scalarCategory = TypedTupleScalarCategory.NUMERIC,
                            ),
                        ),
                    ),
                )
            ),
        )
        val workspace = LsiWorkspace(
            sources = listOf(tupleSource, unrelatedSource),
            declarations = listOf(tupleDeclaration, bookViewDeclaration),
        )
        return Fixture(
            schema = schema,
            workspace = workspace,
            tupleSource = tupleSource,
            dependencySymbols = schema.tuples.single().dependencySymbolIds.toSet(),
        )
    }

    private fun golden(name: String): ByteArray {
        return requireNotNull(javaClass.getResource("/tuple/$name")).readBytes()
    }

    private fun compileJava(
        mapperContent: String,
        tableContent: String,
    ) {
        val projectDir = createTempDirectory(prefix = "jimmer-tuple-renderer-test").toFile()
        val sourceRoot = projectDir.resolve("src/demo")
        val output = projectDir.resolve("classes")
        sourceRoot.mkdirs()
        output.mkdirs()
        val mapperSource = sourceRoot.resolve("BookSummaryMapper.java").apply { writeText(mapperContent) }
        val tableSource = sourceRoot.resolve("BookSummaryTable.java").apply { writeText(tableContent) }
        val tupleSource = sourceRoot.resolve("BookSummary.java").apply {
            writeText(
                "package demo; public class BookSummary { " +
                    "public void setBook(BookView book) {} " +
                    "public void setAuthorCount(long authorCount) {} }"
            )
        }
        val bookSource = sourceRoot.resolve("BookView.java").apply {
            writeText("package demo; public class BookView {}")
        }
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
                manager.getJavaFileObjects(mapperSource, tableSource, tupleSource, bookSource),
            ).call()
        }
        assertTrue(success, diagnostics.diagnostics.joinToString("\n"))
    }

    private fun compileKotlin(content: String) {
        val projectDir = createTempDirectory(prefix = "jimmer-tuple-kotlin-renderer-test").toFile()
        val sourceRoot = projectDir.resolve("src/demo").apply { mkdirs() }
        val output = projectDir.resolve("classes").apply { mkdirs() }
        val mapperSource = sourceRoot.resolve("BookSummaryMapper.kt").apply { writeText(content) }
        val tupleSource = sourceRoot.resolve("BookSummary.kt").apply {
            writeText(
                "package demo\n" +
                    "data class BookSummary(val book: BookView, val authorCount: Long)\n" +
                    "class BookView\n"
            )
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
                mapperSource.absolutePath,
                tupleSource.absolutePath,
            )
        }
        assertEquals(ExitCode.OK, exitCode, messages.toString(StandardCharsets.UTF_8))
    }

    private data class Fixture(
        val schema: TypedTupleSchema,
        val workspace: LsiWorkspace,
        val tupleSource: LsiSource,
        val dependencySymbols: Set<LsiSymbolId>,
    )

    private companion object {
        val TUPLE_ID = LsiSymbolId.type("demo.BookSummary")
        val BOOK_VIEW_ID = LsiSymbolId.type("demo.BookView")
        val BOOK_VIEW_TYPE = LsiDeclaredType(BOOK_VIEW_ID)
        val LONG_TYPE = LsiPrimitiveType(LsiPrimitiveKind.LONG)
    }
}
