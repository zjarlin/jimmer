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
import site.addzero.lsi.model.LsiTypeSeed
import site.addzero.lsi.model.LsiTypeSeedMode
import site.addzero.lsi.model.LsiWorkspace

class CompilerSessionTest {

    @Test
    fun `类型声明请求按符号合并并由完整声明优先`() {
        val alphaId = LsiSymbolId.type("example.Alpha")
        val betaId = LsiSymbolId.type("example.Beta")
        val first = object : JimmerCompilerFeatureProvider {
            override val descriptor = JimmerCompilerFeatureDescriptor("first")

            override fun requestTypeSeeds(
                context: JimmerCompilerTypeSeedContext,
            ): Collection<LsiTypeSeed> {
                return listOf(
                    LsiTypeSeed(betaId, LsiTypeSeedMode.HEADER),
                    LsiTypeSeed(alphaId, LsiTypeSeedMode.HEADER),
                )
            }
        }
        val second = object : JimmerCompilerFeatureProvider {
            override val descriptor = JimmerCompilerFeatureDescriptor("second")

            override fun requestTypeSeeds(
                context: JimmerCompilerTypeSeedContext,
            ): Collection<LsiTypeSeed> {
                return listOf(LsiTypeSeed(betaId, LsiTypeSeedMode.FULL_DECLARATION))
            }
        }
        val session = CompilerSession("type-seeds", listOf(second, first))

        assertEquals(
            listOf(
                LsiTypeSeed(alphaId, LsiTypeSeedMode.HEADER),
                LsiTypeSeed(betaId, LsiTypeSeedMode.FULL_DECLARATION),
            ),
            session.requestedTypeSeeds(emptyRound(0)),
        )
    }

    @Test
    fun `类型声明请求不会执行功能或推进会话轮次`() {
        var collects = 0
        var precompiles = 0
        var renders = 0
        val provider = object : JimmerCompilerFeatureProvider {
            override val descriptor = JimmerCompilerFeatureDescriptor("seed-only")

            override fun requestTypeSeeds(
                context: JimmerCompilerTypeSeedContext,
            ): Collection<LsiTypeSeed> {
                assertEquals(0, context.round.number)
                assertTrue(context.session.rounds.isEmpty())
                return listOf(
                    LsiTypeSeed(LsiSymbolId.type("example.Payload"), LsiTypeSeedMode.FULL_DECLARATION)
                )
            }

            override fun collect(context: JimmerCompilerCollectContext): JimmerCompilerFeatureCollection {
                collects++
                return JimmerCompilerFeatureCollection()
            }

            override fun precompile(
                context: JimmerCompilerPrecompileContext,
            ): JimmerCompilerFeaturePrecompileResult {
                precompiles++
                return JimmerCompilerFeaturePrecompileResult(TextState("seed-only"))
            }

            override fun render(context: JimmerCompilerRenderContext): JimmerCompilerFeatureRenderResult {
                renders++
                return JimmerCompilerFeatureRenderResult()
            }
        }
        val session = CompilerSession("seed-query", listOf(provider))

        session.requestedTypeSeeds(emptyRound(0))
        session.requestedTypeSeeds(emptyRound(0))

        assertEquals(0, collects)
        assertEquals(0, precompiles)
        assertEquals(0, renders)
        assertTrue(session.snapshot().rounds.isEmpty())
    }

    @Test
    fun `最终轮禁止请求额外类型声明`() {
        val session = CompilerSession("final-seed-query", emptyList())

        val exception = assertFailsWith<IllegalArgumentException> {
            session.requestedTypeSeeds(emptyRound(0, isFinal = true))
        }

        assertEquals("Final compiler round cannot request additional type declarations", exception.message)
    }

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
                currentRootTypeIds = emptySet(),
                inputResources = mapOf("META-INF/jimmer/entities" to "demo.Book\n"),
                inputDocumentSnapshots = emptyList(),
            )
        )

        assertEquals(
            "demo.Book\n",
            result.featureResults.getValue("resource-reader").state.fingerprint,
        )
    }

    @Test
    fun `round rejects current roots outside current workspace`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            CompilerRound(
                number = 0,
                workspace = LsiWorkspace.EMPTY,
                currentWorkspace = LsiWorkspace.EMPTY,
                currentRootTypeIds = setOf(LsiSymbolId.type("example.Drifting")),
                inputDocumentSnapshots = emptyList(),
            )
        }

        assertEquals(
            "Current compiler root types must exist in the current workspace",
            exception.message,
        )
    }

    @Test
    fun `final round rejects current roots`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            CompilerRound(
                number = 0,
                workspace = LsiWorkspace.EMPTY,
                currentRootTypeIds = setOf(LsiSymbolId.type("example.Final")),
                isFinal = true,
                inputDocumentSnapshots = emptyList(),
            )
        }

        assertEquals("Final compiler round cannot contain current root types", exception.message)
    }

    @Test
    fun `多轮会话按阶段传递依赖和上一轮快照`() {
        val executions = mutableListOf<String>()
        val immutable = recordingFeature("immutable", executions)
        val client = recordingFeature("client", executions, "immutable")
        val session = CompilerSession("test", listOf(client, immutable))

        val first = session.execute(emptyRound(0))
        val second = session.execute(emptyRound(1, isFinal = true))

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
            .execute(emptyRound(0))

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

        val first = session.execute(emptyRound(0))
        val second = session.execute(emptyRound(1, isFinal = true))

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
            session.execute(
                emptyRound(0, isFinal = true)
            )
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
            session.execute(
                emptyRound(0, isFinal = true)
            )
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
            session.execute(emptyRound(0))
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
            session.execute(emptyRound(0))
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

    private fun emptyRound(
        number: Int,
        isFinal: Boolean = false,
    ): CompilerRound {
        return CompilerRound(
            number = number,
            workspace = LsiWorkspace.EMPTY,
            currentRootTypeIds = emptySet(),
            isFinal = isFinal,
            inputDocumentSnapshots = emptyList(),
        )
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
