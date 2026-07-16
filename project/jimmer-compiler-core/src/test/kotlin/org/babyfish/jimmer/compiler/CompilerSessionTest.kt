package org.babyfish.jimmer.compiler

import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.ArtifactKind
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.codegen.GeneratedArtifactConflictException
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiWorkspace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CompilerSessionTest {

    @Test
    fun `多轮会话传递依赖和上一轮快照`() {
        val executions = mutableListOf<String>()
        val immutable = recordingFeature("immutable", executions)
        val client = recordingFeature("client", executions, "immutable")
        val session = CompilerSession("test", listOf(client, immutable))

        val first = session.execute(CompilerRound(0, LsiWorkspace.EMPTY))
        val second = session.execute(CompilerRound(1, LsiWorkspace.EMPTY, isFinal = true))

        assertEquals(
            listOf(
                "immutable:0:0:",
                "client:0:0:immutable",
                "immutable:1:1:",
                "client:1:1:immutable"
            ),
            executions
        )
        assertEquals(2, session.snapshot().rounds.size)
        assertEquals(first, session.snapshot().rounds.first())
        assertEquals(second, session.snapshot().rounds.last())
    }

    @Test
    fun `跨轮完全相同的资源不重复写出`() {
        val resource = GeneratedArtifact.create(
            kind = ArtifactKind.RESOURCE,
            path = "META-INF/jimmer/client",
            content = "{}",
            aggregationMode = ArtifactAggregationMode.AGGREGATING
        )
        val session = CompilerSession(
            "resource",
            listOf(resultFeature("client", JimmerCompilerFeatureResult(artifacts = listOf(resource))))
        )

        val first = session.execute(CompilerRound(0, LsiWorkspace.EMPTY))
        val second = session.execute(CompilerRound(1, LsiWorkspace.EMPTY, isFinal = true))

        assertEquals(listOf(resource), first.newArtifacts)
        assertTrue(second.newArtifacts.isEmpty())
        assertEquals(listOf(resource), session.artifacts())
    }

    @Test
    fun `最终轮禁止生成源码`() {
        val sourceId = LsiSymbolId.type("example.Book")
        val source = GeneratedArtifact.source(
            kind = ArtifactKind.JAVA_SOURCE,
            qualifiedName = "example.BookDraft",
            content = "package example; class BookDraft {}",
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = setOf(sourceId)
        )
        val session = CompilerSession(
            "final-source",
            listOf(resultFeature("immutable", JimmerCompilerFeatureResult(artifacts = listOf(source))))
        )

        val exception = assertFailsWith<FinalRoundSourceGenerationException> {
            session.execute(CompilerRound(0, LsiWorkspace.EMPTY, isFinal = true))
        }

        assertEquals("immutable", exception.featureId)
        assertEquals(listOf(source), exception.artifacts)
    }

    @Test
    fun `轮次产物冲突时会话保持原状`() {
        val first = GeneratedArtifact.create(
            kind = ArtifactKind.RESOURCE,
            path = "META-INF/jimmer/client",
            content = "first",
            aggregationMode = ArtifactAggregationMode.AGGREGATING
        )
        val conflict = first.copy(content = "second")
        val session = CompilerSession(
            "atomic-round",
            listOf(
                resultFeature("first", JimmerCompilerFeatureResult(artifacts = listOf(first))),
                resultFeature("second", JimmerCompilerFeatureResult(artifacts = listOf(conflict)))
            )
        )

        assertFailsWith<GeneratedArtifactConflictException> {
            session.execute(CompilerRound(0, LsiWorkspace.EMPTY))
        }

        assertTrue(session.snapshot().rounds.isEmpty())
        assertTrue(session.artifacts().isEmpty())
    }

    private fun recordingFeature(
        id: String,
        executions: MutableList<String>,
        vararg dependencies: String
    ): JimmerCompilerFeatureProvider = object : JimmerCompilerFeatureProvider {
        override val descriptor = JimmerCompilerFeatureDescriptor(id, dependencies.toSet())

        override fun compile(context: JimmerCompilerFeatureContext): JimmerCompilerFeatureResult {
            executions += buildString {
                append(id)
                append(':')
                append(context.round.number)
                append(':')
                append(context.session.rounds.size)
                append(':')
                append(context.dependencyResults.keys.joinToString())
            }
            return JimmerCompilerFeatureResult()
        }
    }

    private fun resultFeature(
        id: String,
        result: JimmerCompilerFeatureResult
    ): JimmerCompilerFeatureProvider = object : JimmerCompilerFeatureProvider {
        override val descriptor = JimmerCompilerFeatureDescriptor(id)

        override fun compile(context: JimmerCompilerFeatureContext): JimmerCompilerFeatureResult = result
    }
}
