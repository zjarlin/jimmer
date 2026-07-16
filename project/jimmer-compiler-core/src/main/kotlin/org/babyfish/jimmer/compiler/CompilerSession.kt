package org.babyfish.jimmer.compiler

import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.codegen.GeneratedArtifactSet
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.model.LsiWorkspace

data class CompilerRound(
    val number: Int,
    val workspace: LsiWorkspace,
    val isFinal: Boolean = false
) {

    init {
        require(number >= 0) { "Compiler round number cannot be negative: $number" }
    }
}

data class JimmerCompilerFeatureResult(
    val artifacts: List<GeneratedArtifact> = emptyList(),
    val diagnostics: List<LsiDiagnostic> = emptyList(),
    val processedSymbols: Set<LsiSymbolId> = emptySet(),
    val unresolvedSymbols: Set<LsiSymbolId> = emptySet()
) {

    init {
        val contradictorySymbols = processedSymbols intersect unresolvedSymbols
        require(contradictorySymbols.isEmpty()) {
            "Compiler feature cannot mark symbols as both processed and unresolved: " +
                contradictorySymbols.sorted().joinToString { symbol -> symbol.value }
        }
    }
}

data class CompilerRoundResult(
    val round: CompilerRound,
    val featureResults: Map<String, JimmerCompilerFeatureResult>,
    val newArtifacts: List<GeneratedArtifact>,
    val diagnostics: List<LsiDiagnostic>
) {
    val generatedSources: Boolean
        get() = newArtifacts.any { artifact -> artifact.kind.isSource }

    val unresolvedSymbols: Set<LsiSymbolId>
        get() = featureResults.values.flatMapTo(sortedSetOf()) { result -> result.unresolvedSymbols }
}

data class CompilerSessionSnapshot(
    val id: String,
    val rounds: List<CompilerRoundResult>
)

data class JimmerCompilerFeatureContext(
    val session: CompilerSessionSnapshot,
    val round: CompilerRound,
    val dependencyResults: Map<String, JimmerCompilerFeatureResult>
)

class CompilerSessionStateException(message: String) : IllegalStateException(message)

class FinalRoundSourceGenerationException(
    val featureId: String,
    val artifacts: List<GeneratedArtifact>
) : IllegalStateException(
    "Compiler feature '$featureId' generated source artifacts during final round: " +
        artifacts.joinToString { artifact -> artifact.path }
)

/**
 * 在真实 APT/KSP 轮次之间保存纯 LSI 结果和全局产物身份。
 */
class CompilerSession(
    val id: String,
    providers: Iterable<JimmerCompilerFeatureProvider>
) {
    private val orderedProviders = JimmerCompilerFeatureGraph.sort(providers)

    private val artifactSet = GeneratedArtifactSet()

    private val roundResults = mutableListOf<CompilerRoundResult>()

    private var finalRoundCompleted = false

    init {
        require(id.isNotBlank()) { "Compiler session id cannot be blank" }
    }

    fun execute(round: CompilerRound): CompilerRoundResult {
        validateRound(round)

        val featureResults = linkedMapOf<String, JimmerCompilerFeatureResult>()
        val newArtifacts = mutableListOf<GeneratedArtifact>()
        val diagnostics = mutableListOf<LsiDiagnostic>()
        val sessionSnapshot = snapshot()
        val stagedArtifactSet = GeneratedArtifactSet(artifactSet.snapshot())

        for (provider in orderedProviders) {
            val descriptor = provider.descriptor
            val dependencyResults = descriptor.dependsOn
                .sorted()
                .associateWith { dependencyId -> requireNotNull(featureResults[dependencyId]) }
            val context = JimmerCompilerFeatureContext(sessionSnapshot, round, dependencyResults)
            val result = provider.compile(context)
            validateFinalRoundOutput(round, descriptor.id, result)

            featureResults[descriptor.id] = result
            newArtifacts += stagedArtifactSet.registerAll(result.artifacts)
            diagnostics += result.diagnostics
        }

        val roundResult = CompilerRoundResult(
            round = round,
            featureResults = featureResults.toMap(),
            newArtifacts = newArtifacts.sortedBy(GeneratedArtifact::key),
            diagnostics = diagnostics.toList()
        )
        artifactSet.registerAll(newArtifacts)
        roundResults += roundResult
        finalRoundCompleted = round.isFinal
        return roundResult
    }

    fun snapshot(): CompilerSessionSnapshot = CompilerSessionSnapshot(id, roundResults.toList())

    fun artifacts(): List<GeneratedArtifact> = artifactSet.snapshot()

    private fun validateRound(round: CompilerRound) {
        if (finalRoundCompleted) {
            throw CompilerSessionStateException("Compiler session '$id' has already completed its final round")
        }
        if (round.number != roundResults.size) {
            throw CompilerSessionStateException(
                "Compiler session '$id' expected round ${roundResults.size}, got ${round.number}"
            )
        }
    }

    private fun validateFinalRoundOutput(
        round: CompilerRound,
        featureId: String,
        result: JimmerCompilerFeatureResult
    ) {
        if (!round.isFinal) {
            return
        }
        val sourceArtifacts = result.artifacts.filter { artifact -> artifact.kind.isSource }
        if (sourceArtifacts.isNotEmpty()) {
            throw FinalRoundSourceGenerationException(featureId, sourceArtifacts)
        }
    }
}
