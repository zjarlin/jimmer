package org.babyfish.jimmer.compiler

import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.ArtifactEmissionMode
import site.addzero.lsi.codegen.ArtifactKind
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.codegen.GeneratedArtifactConflictException
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSourceKind
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
    fun `稳定源码连续两个有效轮相同后才写出`() {
        val source = generatedSource("stable", ArtifactEmissionMode.STABLE)
        val session = CompilerSession(
            "stable-source",
            listOf(resultFeature("immutable", listOf(source))),
        )

        val first = session.execute(emptyRound(0))
        val second = session.execute(emptyRound(1))

        assertTrue(first.newArtifacts.isEmpty())
        assertTrue(session.snapshot().rounds.first().newArtifacts.isEmpty())
        assertEquals(listOf(source), second.newArtifacts)
        assertEquals(listOf(source), session.artifacts())
    }

    @Test
    fun `稳定源码忽略轮次来源投影后在第二轮写出`() {
        val first = generatedSource("stable", ArtifactEmissionMode.STABLE).withSourceProjection(
            path = "src/main/java/example/Book.java",
            kind = LsiSourceKind.SOURCE,
        )
        val second = first.withSourceProjection(
            path = "build/generated/example/Book.java",
            kind = LsiSourceKind.GENERATED,
        )
        val session = CompilerSession(
            "stable-source-projection",
            listOf(
                roundResultFeature("immutable") { round ->
                    listOf(if (round == 0) first else second)
                }
            ),
        )

        assertTrue(session.execute(emptyRound(0)).newArtifacts.isEmpty())
        assertEquals(listOf(second), session.execute(emptyRound(1)).newArtifacts)
        assertEquals(listOf(second), session.artifacts())
    }

    @Test
    fun `稳定源码的符号依赖变化后继续等待`() {
        val subtypeId = LsiSymbolId.type("example.SpecialBook")
        val first = generatedSource("stable", ArtifactEmissionMode.STABLE)
        val second = first.copy(dependencySymbols = first.dependencySymbols + subtypeId)
        val session = CompilerSession(
            "stable-source-symbol-dependency",
            listOf(
                roundResultFeature("immutable") { round ->
                    listOf(if (round == 0) first else second)
                }
            ),
        )

        assertTrue(session.execute(emptyRound(0)).newArtifacts.isEmpty())
        assertTrue(session.execute(emptyRound(1)).newArtifacts.isEmpty())
        assertEquals(listOf(second), session.execute(emptyRound(2)).newArtifacts)
        assertEquals(listOf(second), session.artifacts())
    }

    @Test
    fun `稳定源码候选暴露下一轮需重冻的来源符号`() {
        val firstTypeId = LsiSymbolId.type("example.Book")
        val secondTypeId = LsiSymbolId.type("example.SpecialBook")
        val source = generatedSource("stable", ArtifactEmissionMode.STABLE).copy(
            originatingSymbols = setOf(firstTypeId, secondTypeId),
            dependencySymbols = setOf(firstTypeId, secondTypeId),
        )
        val session = CompilerSession(
            "stable-source-origins",
            listOf(resultFeature("immutable", listOf(source))),
        )

        session.execute(emptyRound(0))

        assertEquals(
            setOf(firstTypeId, secondTypeId),
            session.pendingStableSourceOriginatingSymbols(),
        )

        session.execute(emptyRound(1))

        assertTrue(session.pendingStableSourceOriginatingSymbols().isEmpty())
    }

    @Test
    fun `稳定源码变化后重新等待连续相同轮`() {
        val firstSource = generatedSource("first", ArtifactEmissionMode.STABLE)
        val secondSource = firstSource.copy(content = "second")
        val session = CompilerSession(
            "changing-stable-source",
            listOf(
                roundResultFeature("immutable") { round ->
                    listOf(if (round == 0) firstSource else secondSource)
                }
            ),
        )

        val first = session.execute(emptyRound(0))
        val second = session.execute(emptyRound(1))
        val third = session.execute(emptyRound(2))

        assertTrue(first.newArtifacts.isEmpty())
        assertTrue(second.newArtifacts.isEmpty())
        assertEquals(listOf(secondSource), third.newArtifacts)
        assertEquals(listOf(secondSource), session.artifacts())
    }

    @Test
    fun `稳定源码写出后变化直接冲突`() {
        val source = generatedSource("stable", ArtifactEmissionMode.STABLE)
        val changed = source.copy(content = "changed")
        val session = CompilerSession(
            "emitted-stable-source-conflict",
            listOf(
                roundResultFeature("immutable") { round ->
                    listOf(if (round < 2) source else changed)
                }
            ),
        )

        session.execute(emptyRound(0))
        session.execute(emptyRound(1))
        val exception = assertFailsWith<GeneratedArtifactConflictException> {
            session.execute(emptyRound(2))
        }

        assertEquals(source, exception.existing)
        assertEquals(changed, exception.incoming)
        assertEquals(2, session.snapshot().rounds.size)
        assertEquals(listOf(source), session.artifacts())
    }

    @Test
    fun `稳定源码写出后允许来源投影变化`() {
        val first = generatedSource("stable", ArtifactEmissionMode.STABLE).withSourceProjection(
            path = "src/main/java/example/Book.java",
            kind = LsiSourceKind.SOURCE,
        )
        val second = first.withSourceProjection(
            path = "build/generated/example/Book.java",
            kind = LsiSourceKind.GENERATED,
        )
        val third = second.withSourceProjection(
            path = "build/generated/round-2/example/Book.java",
            kind = LsiSourceKind.GENERATED,
        )
        val session = CompilerSession(
            "emitted-stable-source-projection",
            listOf(
                roundResultFeature("immutable") { round ->
                    listOf(
                        when (round) {
                            0 -> first
                            1 -> second
                            else -> third
                        }
                    )
                }
            ),
        )

        session.execute(emptyRound(0))
        assertEquals(listOf(second), session.execute(emptyRound(1)).newArtifacts)
        assertTrue(session.execute(emptyRound(2)).newArtifacts.isEmpty())
        assertEquals(listOf(second), session.artifacts())
    }

    @Test
    fun `稳定源码写出后符号依赖变化直接冲突`() {
        val source = generatedSource("stable", ArtifactEmissionMode.STABLE)
        val changed = source.copy(
            dependencySymbols = source.dependencySymbols + LsiSymbolId.type("example.SpecialBook"),
        )
        val session = CompilerSession(
            "emitted-stable-source-symbol-conflict",
            listOf(
                roundResultFeature("immutable") { round ->
                    listOf(if (round < 2) source else changed)
                }
            ),
        )

        session.execute(emptyRound(0))
        session.execute(emptyRound(1))
        val exception = assertFailsWith<GeneratedArtifactConflictException> {
            session.execute(emptyRound(2))
        }

        assertEquals(source, exception.existing)
        assertEquals(changed, exception.incoming)
        assertEquals(2, session.snapshot().rounds.size)
        assertEquals(listOf(source), session.artifacts())
    }

    @Test
    fun `最终轮仍有稳定源码候选时直接失败`() {
        val source = generatedSource("pending", ArtifactEmissionMode.STABLE)
        val session = CompilerSession(
            "pending-stable-source",
            listOf(
                roundResultFeature("immutable") { round ->
                    if (round == 0) listOf(source) else emptyList()
                }
            ),
        )

        session.execute(emptyRound(0))
        val exception = assertFailsWith<PendingStableSourceArtifactsException> {
            session.execute(emptyRound(1, isFinal = true))
        }

        assertEquals(listOf(source), exception.artifacts)
        assertEquals(1, session.snapshot().rounds.size)
        assertTrue(session.artifacts().isEmpty())
    }

    @Test
    fun `即时源码仍在首个有效轮写出且相同内容不重复`() {
        val source = generatedSource("immediate", ArtifactEmissionMode.IMMEDIATE)
        val session = CompilerSession(
            "immediate-source",
            listOf(resultFeature("immutable", listOf(source))),
        )

        val first = session.execute(emptyRound(0))
        val second = session.execute(emptyRound(1))

        assertEquals(listOf(source), first.newArtifacts)
        assertTrue(second.newArtifacts.isEmpty())
        assertEquals(listOf(source), session.artifacts())
    }

    @Test
    fun `源码静默功能等待其他功能本轮真正新增的源码`() {
        val immutableSource = generatedSource(
            content = "immutable",
            emissionMode = ArtifactEmissionMode.IMMEDIATE,
            qualifiedName = "example.BookDraft",
        )
        val dtoSource = generatedSource(
            content = "dto",
            emissionMode = ArtifactEmissionMode.IMMEDIATE,
            qualifiedName = "example.BookView",
        )
        val dtoResource = GeneratedArtifact.create(
            kind = ArtifactKind.RESOURCE,
            path = "META-INF/jimmer/dto",
            content = "dto",
            aggregationMode = ArtifactAggregationMode.AGGREGATING,
        )
        val session = CompilerSession(
            "source-quiescence",
            listOf(
                resultFeature("immutable", listOf(immutableSource)),
                resultFeature(
                    id = "dto",
                    artifacts = listOf(dtoSource, dtoResource),
                    requiresSourceQuiescence = true,
                ),
            ),
        )

        val first = session.execute(emptyRound(0))
        val second = session.execute(emptyRound(1))

        assertEquals(listOf(immutableSource, dtoResource).sortedBy(GeneratedArtifact::key), first.newArtifacts)
        assertEquals(listOf(dtoSource), second.newArtifacts)
        assertEquals(
            listOf(immutableSource, dtoSource, dtoResource).sortedBy(GeneratedArtifact::key),
            session.artifacts(),
        )
    }

    @Test
    fun `稳定源码本轮成熟时继续阻止源码静默功能`() {
        val firstSource = generatedSource(
            content = "first",
            emissionMode = ArtifactEmissionMode.IMMEDIATE,
            qualifiedName = "example.FirstDraft",
        )
        val stableSource = generatedSource(
            content = "stable",
            emissionMode = ArtifactEmissionMode.STABLE,
            qualifiedName = "example.StableDraft",
        )
        val dtoSource = generatedSource(
            content = "dto",
            emissionMode = ArtifactEmissionMode.IMMEDIATE,
            qualifiedName = "example.BookView",
        )
        val session = CompilerSession(
            "stable-source-quiescence",
            listOf(
                roundResultFeature("immutable") { round ->
                    if (round == 0) listOf(firstSource, stableSource) else listOf(stableSource)
                },
                resultFeature("dto", listOf(dtoSource), requiresSourceQuiescence = true),
            ),
        )

        assertEquals(listOf(firstSource), session.execute(emptyRound(0)).newArtifacts)
        assertEquals(listOf(stableSource), session.execute(emptyRound(1)).newArtifacts)
        assertEquals(listOf(dtoSource), session.execute(emptyRound(2)).newArtifacts)
    }

    @Test
    fun `普通稳定源码首轮候选不阻止源码静默功能`() {
        val stableSource = generatedSource(
            content = "stable",
            emissionMode = ArtifactEmissionMode.STABLE,
            qualifiedName = "example.StableDraft",
        )
        val dtoSource = generatedSource(
            content = "dto",
            emissionMode = ArtifactEmissionMode.IMMEDIATE,
            qualifiedName = "example.BookView",
        )
        val session = CompilerSession(
            "stable-candidate-source-quiescence",
            listOf(
                resultFeature("immutable", listOf(stableSource)),
                resultFeature("dto", listOf(dtoSource), requiresSourceQuiescence = true),
            ),
        )

        assertEquals(listOf(dtoSource), session.execute(emptyRound(0)).newArtifacts)
        assertEquals(listOf(stableSource), session.execute(emptyRound(1)).newArtifacts)
    }

    @Test
    fun `多个源码静默功能在同一静默轮一起写出`() {
        val dtoSource = generatedSource(
            content = "dto",
            emissionMode = ArtifactEmissionMode.IMMEDIATE,
            qualifiedName = "example.BookView",
        )
        val moduleSource = generatedSource(
            content = "module",
            emissionMode = ArtifactEmissionMode.IMMEDIATE,
            qualifiedName = "example.JimmerModule",
        )
        val session = CompilerSession(
            "shared-source-quiescence",
            listOf(
                resultFeature("dto", listOf(dtoSource), requiresSourceQuiescence = true),
                resultFeature("module", listOf(moduleSource), requiresSourceQuiescence = true),
            ),
        )

        assertEquals(
            listOf(dtoSource, moduleSource).sortedBy(GeneratedArtifact::key),
            session.execute(emptyRound(0)).newArtifacts,
        )
    }

    @Test
    fun `被阻止的成熟静默稳定源码刷新当前轮候选`() {
        val triggerSource = generatedSource(
            content = "trigger",
            emissionMode = ArtifactEmissionMode.IMMEDIATE,
            qualifiedName = "example.TriggerView",
        )
        val immutableSource = generatedSource(
            content = "immutable",
            emissionMode = ArtifactEmissionMode.IMMEDIATE,
            qualifiedName = "example.BookDraft",
        )
        val stableDtoSource = generatedSource(
            content = "dto",
            emissionMode = ArtifactEmissionMode.STABLE,
            qualifiedName = "example.BookView",
        )
        val session = CompilerSession(
            "mature-stable-quiescent-source",
            listOf(
                roundResultFeature("immutable") { round ->
                    if (round == 1) listOf(immutableSource) else emptyList()
                },
                resultFeature(
                    "dto",
                    listOf(triggerSource, stableDtoSource),
                    requiresSourceQuiescence = true,
                ),
            ),
        )

        assertEquals(listOf(triggerSource), session.execute(emptyRound(0)).newArtifacts)
        assertEquals(listOf(immutableSource), session.execute(emptyRound(1)).newArtifacts)
        assertEquals(listOf(stableDtoSource), session.execute(emptyRound(2)).newArtifacts)
    }

    @Test
    fun `最终轮禁止源码静默功能生成源码`() {
        val sourceId = LsiSymbolId.type("example.Book")
        val source = GeneratedArtifact.source(
            kind = ArtifactKind.JAVA_SOURCE,
            qualifiedName = "example.BookDraft",
            content = "package example; class BookDraft {}",
            aggregationMode = ArtifactAggregationMode.AGGREGATING,
            originatingSymbols = setOf(sourceId),
        )
        val session = CompilerSession(
            "final-source",
            listOf(resultFeature("dto", listOf(source), requiresSourceQuiescence = true)),
        )

        val exception = assertFailsWith<FinalRoundSourceGenerationException> {
            session.execute(
                emptyRound(0, isFinal = true)
            )
        }

        assertEquals("dto", exception.featureId)
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
        requiresSourceQuiescence: Boolean = false,
    ): JimmerCompilerFeatureProvider = object : JimmerCompilerFeatureProvider {
        override val descriptor = JimmerCompilerFeatureDescriptor(
            id = id,
            requiresSourceQuiescence = requiresSourceQuiescence,
        )

        override fun precompile(
            context: JimmerCompilerPrecompileContext,
        ): JimmerCompilerFeaturePrecompileResult {
            return JimmerCompilerFeaturePrecompileResult(TextState(id))
        }

        override fun render(context: JimmerCompilerRenderContext): JimmerCompilerFeatureRenderResult {
            return JimmerCompilerFeatureRenderResult(artifacts = artifacts)
        }
    }

    private fun roundResultFeature(
        id: String,
        artifacts: (Int) -> List<GeneratedArtifact>,
    ): JimmerCompilerFeatureProvider = object : JimmerCompilerFeatureProvider {
        override val descriptor = JimmerCompilerFeatureDescriptor(id)

        override fun precompile(
            context: JimmerCompilerPrecompileContext,
        ): JimmerCompilerFeaturePrecompileResult {
            return JimmerCompilerFeaturePrecompileResult(TextState(id))
        }

        override fun render(context: JimmerCompilerRenderContext): JimmerCompilerFeatureRenderResult {
            return JimmerCompilerFeatureRenderResult(artifacts = artifacts(context.round.number))
        }
    }

    private fun generatedSource(
        content: String,
        emissionMode: ArtifactEmissionMode,
        qualifiedName: String = "example.BookDraft",
    ): GeneratedArtifact {
        return GeneratedArtifact.source(
            kind = ArtifactKind.JAVA_SOURCE,
            qualifiedName = qualifiedName,
            content = content,
            aggregationMode = ArtifactAggregationMode.AGGREGATING,
            emissionMode = emissionMode,
            originatingSymbols = setOf(LsiSymbolId.type("example.Book")),
        )
    }

    private fun GeneratedArtifact.withSourceProjection(
        path: String,
        kind: LsiSourceKind,
    ): GeneratedArtifact {
        val source = LsiSource.of(path, LsiLanguage.JAVA, kind)
        return copy(
            originatingSources = setOf(source),
            dependencySources = setOf(source),
        )
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
