package org.babyfish.jimmer.compiler

import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.ArtifactKind
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.codegen.GeneratedArtifactConflictException
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiWorkspace

class CompilerSessionTest {

    @Test
    fun `round exposes frozen input resources to features`() {
        val provider = object : JimmerCompilerFeatureProvider {
            override val descriptor = JimmerCompilerFeatureDescriptor(
                id = "resource-reader",
                inputResourcePaths = setOf("META-INF/jimmer/entities"),
            )

            override fun precompile(
                context: JimmerCompilerPrecompileContext,
            ): JimmerCompilerFeaturePrecompileResult {
                return JimmerCompilerFeaturePrecompileResult(
                    state = TextState(context.round.inputResources.getValue("META-INF/jimmer/entities")),
                )
            }
        }
        val result = CompilerSession("input-resource-test", listOf(provider)).execute(
            CompilerRound(
                number = 0,
                workspace = LsiWorkspace.EMPTY,
                inputResources = mapOf("META-INF/jimmer/entities" to "demo.Book\n"),
            )
        )

        assertEquals(
            "demo.Book\n",
            result.featureResults.getValue("resource-reader").state.fingerprint,
        )
    }

    @Test
    fun `多轮会话按阶段传递依赖和上一轮快照`() {
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
                "client:1:1:immutable",
            ),
            executions,
        )
        assertEquals(2, first.fixedPointIterations)
        assertEquals(2, second.fixedPointIterations)
        assertEquals(2, session.snapshot().rounds.size)
        assertEquals(first, session.snapshot().rounds.first())
        assertEquals(second, session.snapshot().rounds.last())
    }

    @Test
    fun `预编译会执行到稳定固定点`() {
        var invocations = 0
        val provider = object : JimmerCompilerFeatureProvider {
            override val descriptor = JimmerCompilerFeatureDescriptor("immutable")

            override fun precompile(
                context: JimmerCompilerPrecompileContext,
            ): JimmerCompilerFeaturePrecompileResult {
                invocations++
                val previous = (context.previousState as? NumericState)?.value ?: -1
                return JimmerCompilerFeaturePrecompileResult(NumericState(min(previous + 1, 2)))
            }
        }

        val result = CompilerSession("fixed-point", listOf(provider))
            .execute(CompilerRound(0, LsiWorkspace.EMPTY))

        assertEquals(4, result.fixedPointIterations)
        assertEquals(4, invocations)
        assertEquals("2", result.featureResults.getValue("immutable").state.fingerprint)
    }

    @Test
    fun `跨轮完全相同的资源不重复写出`() {
        val resource = GeneratedArtifact.create(
            kind = ArtifactKind.RESOURCE,
            path = "META-INF/jimmer/client",
            content = "{}",
            aggregationMode = ArtifactAggregationMode.AGGREGATING,
        )
        val session = CompilerSession(
            "resource",
            listOf(resultFeature("client", listOf(resource))),
        )

        val first = session.execute(CompilerRound(0, LsiWorkspace.EMPTY))
        val second = session.execute(CompilerRound(1, LsiWorkspace.EMPTY, isFinal = true))

        assertEquals(listOf(resource), first.newArtifacts)
        assertTrue(second.newArtifacts.isEmpty())
        assertEquals(1, second.fixedPointIterations)
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
            originatingSymbols = setOf(sourceId),
        )
        val session = CompilerSession(
            "final-source",
            listOf(resultFeature("immutable", listOf(source))),
        )

        val exception = assertFailsWith<FinalRoundSourceGenerationException> {
            session.execute(CompilerRound(0, LsiWorkspace.EMPTY, isFinal = true))
        }

        assertEquals("immutable", exception.featureId)
        assertEquals(listOf(source), exception.artifacts)
    }

    @Test
    fun `最终轮只允许聚合资源`() {
        val sourceId = LsiSymbolId.type("example.Book")
        val resource = GeneratedArtifact.create(
            kind = ArtifactKind.RESOURCE,
            path = "META-INF/jimmer/example.Book",
            content = "example.Book",
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = setOf(sourceId),
        )
        val session = CompilerSession(
            "final-isolating",
            listOf(resultFeature("module", listOf(resource))),
        )

        val exception = assertFailsWith<FinalRoundIsolatingArtifactException> {
            session.execute(CompilerRound(0, LsiWorkspace.EMPTY, isFinal = true))
        }

        assertEquals("module", exception.featureId)
        assertEquals(listOf(resource), exception.artifacts)
    }

    @Test
    fun `轮次产物冲突时会话保持原状`() {
        val first = GeneratedArtifact.create(
            kind = ArtifactKind.RESOURCE,
            path = "META-INF/jimmer/client",
            content = "first",
            aggregationMode = ArtifactAggregationMode.AGGREGATING,
        )
        val conflict = first.copy(content = "second")
        val session = CompilerSession(
            "atomic-round",
            listOf(
                resultFeature("first", listOf(first)),
                resultFeature("second", listOf(conflict)),
            ),
        )

        assertFailsWith<GeneratedArtifactConflictException> {
            session.execute(CompilerRound(0, LsiWorkspace.EMPTY))
        }

        assertTrue(session.snapshot().rounds.isEmpty())
        assertTrue(session.artifacts().isEmpty())
    }

    @Test
    fun `固定点不收敛时直接失败`() {
        val provider = object : JimmerCompilerFeatureProvider {
            override val descriptor = JimmerCompilerFeatureDescriptor("unstable")

            override fun precompile(
                context: JimmerCompilerPrecompileContext,
            ): JimmerCompilerFeaturePrecompileResult {
                val previous = (context.previousState as? NumericState)?.value ?: 0
                return JimmerCompilerFeaturePrecompileResult(NumericState(previous + 1))
            }
        }
        val session = CompilerSession(
            id = "unstable",
            providers = listOf(provider),
            maximumFixedPointIterations = 3,
        )

        val exception = assertFailsWith<CompilerFixedPointException> {
            session.execute(CompilerRound(0, LsiWorkspace.EMPTY))
        }

        assertEquals(3, exception.maximumIterations)
        assertTrue(session.snapshot().rounds.isEmpty())
    }

    private fun recordingFeature(
        id: String,
        executions: MutableList<String>,
        vararg dependencies: String,
    ): JimmerCompilerFeatureProvider = object : JimmerCompilerFeatureProvider {
        override val descriptor = JimmerCompilerFeatureDescriptor(id, dependencies.toSet())

        override fun precompile(
            context: JimmerCompilerPrecompileContext,
        ): JimmerCompilerFeaturePrecompileResult {
            return JimmerCompilerFeaturePrecompileResult(TextState("$id:${context.round.number}"))
        }

        override fun render(context: JimmerCompilerRenderContext): JimmerCompilerFeatureRenderResult {
            executions += buildString {
                append(id)
                append(':')
                append(context.round.number)
                append(':')
                append(context.session.rounds.size)
                append(':')
                append(context.dependencyStates.keys.joinToString())
            }
            return JimmerCompilerFeatureRenderResult()
        }
    }

    private fun resultFeature(
        id: String,
        artifacts: List<GeneratedArtifact>,
    ): JimmerCompilerFeatureProvider = object : JimmerCompilerFeatureProvider {
        override val descriptor = JimmerCompilerFeatureDescriptor(id)

        override fun precompile(
            context: JimmerCompilerPrecompileContext,
        ): JimmerCompilerFeaturePrecompileResult {
            return JimmerCompilerFeaturePrecompileResult(TextState(id))
        }

        override fun render(context: JimmerCompilerRenderContext): JimmerCompilerFeatureRenderResult {
            return JimmerCompilerFeatureRenderResult(artifacts = artifacts)
        }
    }

    private data class TextState(
        override val fingerprint: String,
    ) : JimmerCompilerFeatureState

    private data class NumericState(
        val value: Int,
    ) : JimmerCompilerFeatureState {
        override val fingerprint: String = value.toString()
    }
}
