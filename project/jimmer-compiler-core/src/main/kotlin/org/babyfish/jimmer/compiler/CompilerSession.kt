package org.babyfish.jimmer.compiler

import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.codegen.GeneratedArtifactSet
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.model.LsiWorkspace

data class CompilerRound(
    val number: Int,
    val workspace: LsiWorkspace,
    val currentWorkspace: LsiWorkspace = workspace,
    val platform: CompilerPlatform = CompilerPlatform.UNKNOWN,
    val isFinal: Boolean = false,
    val options: Map<String, String> = emptyMap(),
    val inputResources: Map<String, String> = emptyMap(),
) {

    init {
        require(number >= 0) { "Compiler round number cannot be negative: $number" }
        require(options.keys.none(String::isBlank)) { "Compiler option name cannot be blank" }
        inputResources.keys.forEach(::requireCompilerResourcePath)
    }
}

enum class CompilerPlatform {
    APT,
    KSP,
    UNKNOWN,
}

interface JimmerCompilerFeatureState {
    val fingerprint: String

    companion object {
        val EMPTY: JimmerCompilerFeatureState = object : JimmerCompilerFeatureState {
            override val fingerprint: String = "empty"

            override fun toString(): String = "JimmerCompilerFeatureState.EMPTY"
        }
    }
}

data class JimmerCompilerFeatureCollection(
    val state: JimmerCompilerFeatureState = JimmerCompilerFeatureState.EMPTY,
    val diagnostics: List<LsiDiagnostic> = emptyList(),
)

data class JimmerCompilerFeaturePrecompileResult(
    val state: JimmerCompilerFeatureState,
    val diagnostics: List<LsiDiagnostic> = emptyList(),
    val processedSymbols: Set<LsiSymbolId> = emptySet(),
    val unresolvedSymbols: Set<LsiSymbolId> = emptySet(),
) {

    init {
        require(state.fingerprint.isNotBlank()) { "Compiler feature state fingerprint cannot be blank" }
        val contradictorySymbols = processedSymbols intersect unresolvedSymbols
        require(contradictorySymbols.isEmpty()) {
            "Compiler feature cannot mark symbols as both processed and unresolved: " +
                contradictorySymbols.sorted().joinToString { symbol -> symbol.value }
        }
    }
}

data class JimmerCompilerFeatureRenderResult(
    val artifacts: List<GeneratedArtifact> = emptyList(),
    val diagnostics: List<LsiDiagnostic> = emptyList(),
)

data class JimmerCompilerFeatureResult(
    val collection: JimmerCompilerFeatureCollection,
    val precompiled: JimmerCompilerFeaturePrecompileResult,
    val rendered: JimmerCompilerFeatureRenderResult,
) {
    val state: JimmerCompilerFeatureState
        get() = precompiled.state

    val artifacts: List<GeneratedArtifact>
        get() = rendered.artifacts

    val diagnostics: List<LsiDiagnostic>
        get() = collection.diagnostics + precompiled.diagnostics + rendered.diagnostics

    val processedSymbols: Set<LsiSymbolId>
        get() = precompiled.processedSymbols

    val unresolvedSymbols: Set<LsiSymbolId>
        get() = precompiled.unresolvedSymbols
}

data class CompilerRoundResult(
    val round: CompilerRound,
    val fixedPointIterations: Int,
    val featureResults: Map<String, JimmerCompilerFeatureResult>,
    val newArtifacts: List<GeneratedArtifact>,
    val diagnostics: List<LsiDiagnostic>,
) {
    val generatedSources: Boolean
        get() = newArtifacts.any { artifact -> artifact.kind.isSource }

    val unresolvedSymbols: Set<LsiSymbolId>
        get() = featureResults.values.flatMapTo(sortedSetOf()) { result -> result.unresolvedSymbols }
}

data class CompilerSessionSnapshot(
    val id: String,
    val rounds: List<CompilerRoundResult>,
)

data class JimmerCompilerCollectContext(
    val session: CompilerSessionSnapshot,
    val round: CompilerRound,
)

data class JimmerCompilerPrecompileContext(
    val session: CompilerSessionSnapshot,
    val round: CompilerRound,
    val collection: JimmerCompilerFeatureCollection,
    val previousState: JimmerCompilerFeatureState?,
    val dependencyStates: Map<String, JimmerCompilerFeatureState>,
)

data class JimmerCompilerRenderContext(
    val session: CompilerSessionSnapshot,
    val round: CompilerRound,
    val collection: JimmerCompilerFeatureCollection,
    val state: JimmerCompilerFeatureState,
    val dependencyStates: Map<String, JimmerCompilerFeatureState>,
)

class CompilerSessionStateException(message: String) : IllegalStateException(message)

class CompilerFixedPointException(
    val sessionId: String,
    val roundNumber: Int,
    val maximumIterations: Int,
) : IllegalStateException(
    "Compiler session '$sessionId' round $roundNumber did not reach a fixed point after $maximumIterations iterations",
)

class FinalRoundSourceGenerationException(
    val featureId: String,
    val artifacts: List<GeneratedArtifact>,
) : IllegalStateException(
    "Compiler feature '$featureId' generated source artifacts during final round: " +
        artifacts.joinToString { artifact -> artifact.path },
)

class FinalRoundIsolatingArtifactException(
    val featureId: String,
    val artifacts: List<GeneratedArtifact>,
) : IllegalStateException(
    "Compiler feature '$featureId' generated isolating artifacts during final round: " +
        artifacts.joinToString { artifact -> artifact.path },
)

/**
 * 在真实 APT/KSP 轮次之间保存纯 LSI 状态，并在每轮执行预编译固定点。
 */
class CompilerSession(
    val id: String,
    providers: Iterable<JimmerCompilerFeatureProvider>,
    private val maximumFixedPointIterations: Int = 64,
) {
    private val orderedProviders = JimmerCompilerFeatureGraph.sort(providers)

    private val artifactSet = GeneratedArtifactSet()

    private val roundResults = mutableListOf<CompilerRoundResult>()

    private var finalRoundCompleted = false

    init {
        require(id.isNotBlank()) { "Compiler session id cannot be blank" }
        require(maximumFixedPointIterations >= 1) {
            "Compiler maximum fixed point iterations must be positive: $maximumFixedPointIterations"
        }
    }

    fun execute(round: CompilerRound): CompilerRoundResult {
        validateRound(round)

        val sessionSnapshot = snapshot()
        val collections = collect(sessionSnapshot, round)
        val fixedPoint = precompile(sessionSnapshot, round, collections)
        val featureResults = render(sessionSnapshot, round, collections, fixedPoint.results)
        val stagedArtifactSet = GeneratedArtifactSet(artifactSet.snapshot())
        val newArtifacts = mutableListOf<GeneratedArtifact>()
        val diagnostics = mutableListOf<LsiDiagnostic>()
        for ((featureId, result) in featureResults) {
            validateFinalRoundOutput(round, featureId, result)
            newArtifacts += stagedArtifactSet.registerAll(result.artifacts)
            diagnostics += result.diagnostics
        }

        val roundResult = CompilerRoundResult(
            round = round,
            fixedPointIterations = fixedPoint.iterations,
            featureResults = featureResults.toMap(),
            newArtifacts = newArtifacts.sortedBy(GeneratedArtifact::key),
            diagnostics = diagnostics.toList(),
        )
        artifactSet.registerAll(newArtifacts)
        roundResults += roundResult
        finalRoundCompleted = round.isFinal
        return roundResult
    }

    fun snapshot(): CompilerSessionSnapshot = CompilerSessionSnapshot(id, roundResults.toList())

    fun artifacts(): List<GeneratedArtifact> = artifactSet.snapshot()

    private fun collect(
        session: CompilerSessionSnapshot,
        round: CompilerRound,
    ): Map<String, JimmerCompilerFeatureCollection> {
        return orderedProviders.associate { provider ->
            provider.descriptor.id to provider.collect(JimmerCompilerCollectContext(session, round))
        }
    }

    private fun precompile(
        session: CompilerSessionSnapshot,
        round: CompilerRound,
        collections: Map<String, JimmerCompilerFeatureCollection>,
    ): FixedPointResult {
        var previousResults = roundResults.lastOrNull()?.featureResults
            ?.mapValues { (_, result) -> result.precompiled }
            .orEmpty()
        repeat(maximumFixedPointIterations) { iteration ->
            val currentResults = linkedMapOf<String, JimmerCompilerFeaturePrecompileResult>()
            for (provider in orderedProviders) {
                val descriptor = provider.descriptor
                val dependencyStates = descriptor.dependsOn
                    .sorted()
                    .associateWith { dependencyId -> requireNotNull(currentResults[dependencyId]).state }
                val previousState = previousResults[descriptor.id]?.state
                currentResults[descriptor.id] = provider.precompile(
                    JimmerCompilerPrecompileContext(
                        session = session,
                        round = round,
                        collection = requireNotNull(collections[descriptor.id]),
                        previousState = previousState,
                        dependencyStates = dependencyStates,
                    ),
                )
            }
            val stable = orderedProviders.all { provider ->
                val id = provider.descriptor.id
                previousResults[id]?.state?.fingerprint == currentResults[id]?.state?.fingerprint
            }
            if (stable) {
                return FixedPointResult(iteration + 1, currentResults)
            }
            previousResults = currentResults
        }
        throw CompilerFixedPointException(id, round.number, maximumFixedPointIterations)
    }

    private fun render(
        session: CompilerSessionSnapshot,
        round: CompilerRound,
        collections: Map<String, JimmerCompilerFeatureCollection>,
        precompiledResults: Map<String, JimmerCompilerFeaturePrecompileResult>,
    ): Map<String, JimmerCompilerFeatureResult> {
        val results = linkedMapOf<String, JimmerCompilerFeatureResult>()
        for (provider in orderedProviders) {
            val descriptor = provider.descriptor
            val dependencyStates = descriptor.dependsOn
                .sorted()
                .associateWith { dependencyId -> requireNotNull(precompiledResults[dependencyId]).state }
            val collection = requireNotNull(collections[descriptor.id])
            val precompiled = requireNotNull(precompiledResults[descriptor.id])
            val rendered = provider.render(
                JimmerCompilerRenderContext(
                    session = session,
                    round = round,
                    collection = collection,
                    state = precompiled.state,
                    dependencyStates = dependencyStates,
                ),
            )
            results[descriptor.id] = JimmerCompilerFeatureResult(collection, precompiled, rendered)
        }
        return results
    }

    private fun validateRound(round: CompilerRound) {
        if (finalRoundCompleted) {
            throw CompilerSessionStateException("Compiler session '$id' has already completed its final round")
        }
        if (round.number != roundResults.size) {
            throw CompilerSessionStateException(
                "Compiler session '$id' expected round ${roundResults.size}, got ${round.number}",
            )
        }
    }

    private fun validateFinalRoundOutput(
        round: CompilerRound,
        featureId: String,
        result: JimmerCompilerFeatureResult,
    ) {
        if (!round.isFinal) {
            return
        }
        val sourceArtifacts = result.artifacts.filter { artifact -> artifact.kind.isSource }
        if (sourceArtifacts.isNotEmpty()) {
            throw FinalRoundSourceGenerationException(featureId, sourceArtifacts)
        }
        val isolatingArtifacts = result.artifacts.filter { artifact ->
            artifact.aggregationMode == ArtifactAggregationMode.ISOLATING
        }
        if (isolatingArtifacts.isNotEmpty()) {
            throw FinalRoundIsolatingArtifactException(featureId, isolatingArtifacts)
        }
    }

    private data class FixedPointResult(
        val iterations: Int,
        val results: Map<String, JimmerCompilerFeaturePrecompileResult>,
    )
}
