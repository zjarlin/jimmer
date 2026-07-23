package org.babyfish.jimmer.compiler

import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.ArtifactEmissionMode
import site.addzero.lsi.codegen.ArtifactRegistration
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.codegen.GeneratedArtifactConflictException
import site.addzero.lsi.codegen.GeneratedArtifactKey
import site.addzero.lsi.codegen.GeneratedArtifactSet
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.model.LsiTypeSeed
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.mergeLsiTypeSeeds

data class CompilerRound(
    val number: Int,
    val workspace: LsiWorkspace,
    val currentWorkspace: LsiWorkspace = workspace,
    val currentRootTypeIds: Set<LsiSymbolId>,
    val platform: CompilerPlatform = CompilerPlatform.UNKNOWN,
    val isFinal: Boolean = false,
    val options: Map<String, String> = emptyMap(),
    val availableTypeIds: Set<LsiSymbolId> = emptySet(),
    val frontendDeferred: Boolean = false,
    val inputDocumentDiscoveryComplete: Boolean = true,
    val inputResources: Map<String, String> = emptyMap(),
    val inputDocumentSnapshots: List<CompilerInputDocumentSnapshot>,
) {

    init {
        require(number >= 0) { "Compiler round number cannot be negative: $number" }
        currentRootTypeIds.forEach(LsiSymbolId::requireTypeQualifiedName)
        require(!isFinal || currentRootTypeIds.isEmpty()) {
            "Final compiler round cannot contain current root types"
        }
        require(currentRootTypeIds.all(currentWorkspace::contains)) {
            "Current compiler root types must exist in the current workspace"
        }
        require(options.keys.none(String::isBlank)) { "Compiler option name cannot be blank" }
        availableTypeIds.forEach(LsiSymbolId::requireTypeQualifiedName)
        inputResources.keys.forEach(::requireCompilerResourcePath)
        require(inputDocumentSnapshots == inputDocumentSnapshots.sorted()) {
            "Compiler input document snapshots must use stable source order"
        }
        require(
            inputDocumentSnapshots
                .distinctBy { snapshot -> snapshot.document.kind to snapshot.document.source.path }
                .size == inputDocumentSnapshots.size
        ) {
            "Compiler round cannot contain duplicate input document snapshots"
        }
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

data class JimmerCompilerTypeSeedContext(
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

class PendingStableSourceArtifactsException(
    val artifacts: List<GeneratedArtifact>,
) : IllegalStateException(
    "Compiler session reached the final round before stable source artifacts converged: " +
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

    private val classpathTypeIds = orderedProviders
        .flatMapTo(sortedSetOf()) { provider -> provider.descriptor.classpathTypeIds }

    private val artifactSet = GeneratedArtifactSet()

    private val stableArtifactCandidates = linkedMapOf<GeneratedArtifactKey, StableArtifactCandidate>()

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
        val roundArtifactSet = GeneratedArtifactSet()
        val stagedArtifactSet = GeneratedArtifactSet(artifactSet.snapshot())
        val stagedStableCandidates = LinkedHashMap(stableArtifactCandidates)
        val newArtifacts = mutableListOf<GeneratedArtifact>()
        val diagnostics = mutableListOf<LsiDiagnostic>()
        for ((featureId, result) in featureResults) {
            validateFinalRoundOutput(round, featureId, result)
            roundArtifactSet.registerAll(result.artifacts)
            diagnostics += result.diagnostics
        }
        if (round.isFinal && stagedStableCandidates.isNotEmpty()) {
            throw PendingStableSourceArtifactsException(
                stagedStableCandidates.values
                    .map(StableArtifactCandidate::artifact)
                    .sortedBy(GeneratedArtifact::key),
            )
        }
        val currentStableKeys = mutableSetOf<GeneratedArtifactKey>()
        for (artifact in roundArtifactSet.snapshot()) {
            when (artifact.emissionMode) {
                ArtifactEmissionMode.IMMEDIATE -> {
                    if (stagedArtifactSet.register(artifact) == ArtifactRegistration.ADDED) {
                        newArtifacts += artifact
                    }
                }
                ArtifactEmissionMode.STABLE -> {
                    currentStableKeys += artifact.key
                    val emitted = stagedArtifactSet[artifact.key]
                    if (emitted != null) {
                        if (emitted != artifact) {
                            throw GeneratedArtifactConflictException(emitted, artifact)
                        }
                        stagedStableCandidates.remove(artifact.key)
                        continue
                    }
                    val candidate = stagedStableCandidates[artifact.key]
                    if (
                        candidate != null &&
                        candidate.roundNumber == round.number - 1 &&
                        candidate.artifact == artifact
                    ) {
                        stagedArtifactSet.register(artifact)
                        stagedStableCandidates.remove(artifact.key)
                        newArtifacts += artifact
                    } else {
                        stagedStableCandidates[artifact.key] = StableArtifactCandidate(artifact, round.number)
                    }
                }
            }
        }
        if (!round.isFinal) {
            stagedStableCandidates.keys.retainAll(currentStableKeys)
        }

        val roundResult = CompilerRoundResult(
            round = round,
            fixedPointIterations = fixedPoint.iterations,
            featureResults = featureResults.toMap(),
            newArtifacts = newArtifacts.sortedBy(GeneratedArtifact::key),
            diagnostics = diagnostics.toList(),
        )
        artifactSet.registerAll(newArtifacts)
        stableArtifactCandidates.clear()
        stableArtifactCandidates.putAll(stagedStableCandidates)
        roundResults += roundResult
        finalRoundCompleted = round.isFinal
        return roundResult
    }

    /**
     * 在正式执行当前轮之前查询功能所需的额外类型声明，不推进会话状态。
     */
    fun requestedTypeSeeds(round: CompilerRound): List<LsiTypeSeed> {
        validateRound(round)
        require(!round.isFinal) { "Final compiler round cannot request additional type declarations" }
        val context = JimmerCompilerTypeSeedContext(snapshot(), round)
        return orderedProviders
            .flatMap { provider -> provider.requestTypeSeeds(context) }
            .mergeLsiTypeSeeds()
    }

    fun snapshot(): CompilerSessionSnapshot = CompilerSessionSnapshot(id, roundResults.toList())

    fun artifacts(): List<GeneratedArtifact> = artifactSet.snapshot()

    fun pendingStableSourceOriginatingSymbols(): Set<LsiSymbolId> {
        return stableArtifactCandidates.values
            .flatMapTo(sortedSetOf()) { candidate -> candidate.artifact.originatingSymbols }
    }

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
        require(round.availableTypeIds.all(classpathTypeIds::contains)) {
            "Available compiler types must be declared by a compiler feature"
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

    private data class StableArtifactCandidate(
        val artifact: GeneratedArtifact,
        val roundNumber: Int,
    )
}
