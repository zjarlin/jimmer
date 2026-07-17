package org.babyfish.jimmer.compiler.module

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableSchema
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableType
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableTypeKind
import org.babyfish.jimmer.compiler.module.apt.JimmerModuleJavaRenderer
import org.babyfish.jimmer.compiler.module.ksp.JimmerModuleKotlinRenderer
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.ArtifactKind
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiWorkspace

class JimmerModuleRendererTest {

    @Test
    fun `apt summaries match collision goldens and compile`() {
        val fixture = fixture(JimmerModulePlatform.APT)
        val artifacts = JimmerModuleJavaRenderer().render(fixture.schema, fixture.workspace)

        assertEquals(
            listOf("Immutables.java", "Tables.java", "TableExes.java", "Fetchers.java"),
            artifacts.map { artifact -> artifact.path.substringAfterLast('/') },
        )
        artifacts.forEach { artifact ->
            assertEquals(ArtifactKind.JAVA_SOURCE, artifact.kind)
            assertEquals(ArtifactAggregationMode.AGGREGATING, artifact.aggregationMode)
            assertEquals(ENTITY_IDS.toSet(), artifact.originatingSymbols)
            assertEquals(fixture.sources.toSet(), artifact.originatingSources)
            assertEquals(golden("apt/${artifact.path.substringAfterLast('/')}"), artifact.content)
        }
        compileJava(artifacts)
    }

    @Test
    fun `ksp module matches golden and compiles`() {
        val fixture = fixture(JimmerModulePlatform.KSP)
        val artifact = JimmerModuleKotlinRenderer().render(fixture.schema, fixture.workspace).single()

        assertEquals(ArtifactKind.KOTLIN_SOURCE, artifact.kind)
        assertEquals(ArtifactAggregationMode.AGGREGATING, artifact.aggregationMode)
        assertEquals("demo/JimmerModule.kt", artifact.path)
        assertEquals(ENTITY_IDS.toSet(), artifact.originatingSymbols)
        assertEquals(fixture.sources.toSet(), artifact.originatingSources)
        assertEquals(golden("ksp/JimmerModule.kt"), artifact.content)
        compileKotlin(artifact)
    }

    @Test
    fun `shared resources match stable golden and schema origins`() {
        val aptFixture = fixture(JimmerModulePlatform.APT)
        val aptArtifacts = JimmerModuleResourceRenderer().render(aptFixture.schema, aptFixture.workspace)
        val kspFixture = fixture(JimmerModulePlatform.KSP)
        val kspArtifact = JimmerModuleResourceRenderer().render(kspFixture.schema, kspFixture.workspace).single()

        assertEquals(
            listOf("META-INF/jimmer/entities", "META-INF/jimmer/immutables"),
            aptArtifacts.map(GeneratedArtifact::path),
        )
        assertEquals(golden("resources/entities.txt"), aptArtifacts[0].content)
        assertEquals(ENTITY_IDS.toSet(), aptArtifacts[0].originatingSymbols)
        assertEquals(aptFixture.sources.toSet(), aptArtifacts[0].originatingSources)
        assertEquals("", aptArtifacts[1].content)
        assertTrue(aptArtifacts[1].originatingSymbols.isEmpty())
        assertTrue(aptArtifacts[1].originatingSources.isEmpty())
        assertEquals("META-INF/jimmer/entities", kspArtifact.path)
        assertEquals(golden("resources/entities.txt"), kspArtifact.content)
        assertEquals(ENTITY_IDS.toSet(), kspArtifact.originatingSymbols)
        assertEquals(kspFixture.sources.toSet(), kspArtifact.originatingSources)
        assertTrue((aptArtifacts + kspArtifact).all { artifact ->
            artifact.kind == ArtifactKind.RESOURCE &&
                artifact.aggregationMode == ArtifactAggregationMode.AGGREGATING
        })
    }

    private fun fixture(platform: JimmerModulePlatform): Fixture {
        val language = when (platform) {
            JimmerModulePlatform.APT -> LsiLanguage.JAVA
            JimmerModulePlatform.KSP -> LsiLanguage.KOTLIN
        }
        val extension = when (language) {
            LsiLanguage.JAVA -> "java"
            LsiLanguage.KOTLIN -> "kt"
            else -> error("Unexpected module fixture language: $language")
        }
        val sources = ENTITY_NAMES.map { qualifiedName ->
            LsiSource.of(qualifiedName.replace('.', '/') + ".$extension", language)
        }
        val declarations = ENTITY_NAMES.mapIndexed { index, qualifiedName ->
            LsiTypeDeclaration(
                id = ENTITY_IDS[index],
                name = qualifiedName.substringAfterLast('.'),
                qualifiedName = qualifiedName,
                kind = LsiTypeDeclarationKind.INTERFACE,
                origin = LsiOrigin(LsiOriginKind.SOURCE, sources[index]),
            )
        }
        val immutableSchema = JimmerImmutableSchema(
            types = ENTITY_NAMES.mapIndexed { index, qualifiedName ->
                JimmerImmutableType(
                    id = ENTITY_IDS[index],
                    qualifiedName = qualifiedName,
                    kind = JimmerImmutableTypeKind.ENTITY,
                    typeParameterIds = emptyList(),
                    superTypeIds = emptyList(),
                    props = emptyList(),
                )
            },
        )
        val schema = JimmerModulePrecompiler(
            JimmerModulePrecompileOptions(
                platform = platform,
                moduleRequired = true,
            )
        ).compile(
            immutableSchema = immutableSchema,
            compilationScope = JimmerModuleCompilationScope(
                currentImmutableTypeIds = ENTITY_IDS,
                compilationSourceTypeIds = ENTITY_IDS,
            ),
        )
        return Fixture(
            schema = schema,
            workspace = LsiWorkspace(sources, declarations),
            sources = sources,
        )
    }

    private fun golden(path: String): String {
        return requireNotNull(javaClass.getResource("/module/$path")).readText()
    }

    private fun compileJava(artifacts: List<GeneratedArtifact>) {
        val projectDir = createTempDirectory(prefix = "jimmer-module-java-renderer-test").toFile()
        val sourceRoot = projectDir.resolve("src")
        val output = projectDir.resolve("classes")
        sourceRoot.mkdirs()
        output.mkdirs()
        artifacts.forEach { artifact ->
            sourceRoot.resolve(artifact.path).apply {
                parentFile.mkdirs()
                writeText(artifact.content)
            }
        }
        listOf("demo.alpha", "demo.beta").forEach { packageName ->
            writeJavaStubs(sourceRoot, packageName)
        }
        val sourceFiles = sourceRoot.walkTopDown()
            .filter { file -> file.isFile && file.extension == "java" }
            .toList()
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
                manager.getJavaFileObjectsFromFiles(sourceFiles),
            ).call()
        }
        assertTrue(success, diagnostics.diagnostics.joinToString("\n"))
    }

    private fun writeJavaStubs(sourceRoot: java.io.File, packageName: String) {
        val packageDir = sourceRoot.resolve(packageName.replace('.', '/')).apply { mkdirs() }
        packageDir.resolve("Book.java").writeText(
            "package $packageName; public interface Book {}"
        )
        packageDir.resolve("BookDraft.java").writeText(
            """
            package $packageName;
            import org.babyfish.jimmer.DraftConsumer;
            public final class BookDraft {
                public static final Producer $ = new Producer();
                public static final class Producer {
                    public Book produce(DraftConsumer<BookDraft> block) { return null; }
                    public Book produce(Book base, DraftConsumer<BookDraft> block) { return null; }
                }
            }
            """.trimIndent()
        )
        listOf("Table", "TableEx", "Fetcher").forEach { suffix ->
            val simpleName = "Book$suffix"
            packageDir.resolve("$simpleName.java").writeText(
                "package $packageName; public final class $simpleName { " +
                    "public static final $simpleName $ = new $simpleName(); }"
            )
        }
    }

    private fun compileKotlin(artifact: GeneratedArtifact) {
        val projectDir = createTempDirectory(prefix = "jimmer-module-kotlin-renderer-test").toFile()
        val source = projectDir.resolve(artifact.path).apply {
            parentFile.mkdirs()
            writeText(artifact.content)
        }
        val output = projectDir.resolve("classes").apply { mkdirs() }
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
                source.absolutePath,
            )
        }
        assertEquals(ExitCode.OK, exitCode, messages.toString(StandardCharsets.UTF_8))
    }

    private data class Fixture(
        val schema: JimmerModuleSchema,
        val workspace: LsiWorkspace,
        val sources: List<LsiSource>,
    )

    private companion object {
        val ENTITY_NAMES = listOf("demo.alpha.Book", "demo.beta.Book")
        val ENTITY_IDS = ENTITY_NAMES.map(LsiSymbolId::type)
    }
}
