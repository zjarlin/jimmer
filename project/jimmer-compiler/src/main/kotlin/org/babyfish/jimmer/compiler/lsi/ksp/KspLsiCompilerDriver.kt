package org.babyfish.jimmer.compiler.lsi.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import java.util.Collections
import java.util.IdentityHashMap
import org.babyfish.jimmer.compiler.CompilerRound
import org.babyfish.jimmer.compiler.CompilerRoundResult
import org.babyfish.jimmer.compiler.CompilerSession
import org.babyfish.jimmer.compiler.CompilerPlatform
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureProvider
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureProviders
import org.babyfish.jimmer.compiler.lsi.LsiFrontendOptions
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.diagnostic.LsiDiagnosticSeverity
import site.addzero.lsi.model.LsiWorkspace

/**
 * 把单个真实 KSP 轮次冻结、调度并立即写回平台输出。
 */
class KspLsiCompilerDriver(
    environment: SymbolProcessorEnvironment,
    providers: Iterable<JimmerCompilerFeatureProvider> = JimmerCompilerFeatureProviders.load(),
    sessionId: String = "ksp",
) {
    var lastRoundGeneratedSources: Boolean = false
        private set

    private val options = environment.options.toSortedMap()

    private val frontendOptions = LsiFrontendOptions.from(options)

    private val providerList = providers.toList()

    private val inputResourcePaths = providerList
        .flatMapTo(sortedSetOf()) { provider -> provider.descriptor.inputResourcePaths }

    private val session = CompilerSession(sessionId, providerList)

    private val logger = environment.logger

    private val writer = KspGeneratedArtifactWriter(environment.codeGenerator)

    private val inputResourceReader = KspCompilerInputResourceReader(environment.codeGenerator)

    private var nextRoundNumber = 0

    private var workspace = LsiWorkspace.EMPTY

    private var inputResources = emptyMap<String, String>()

    fun process(resolver: Resolver): List<KSAnnotated> {
        val currentRoundSymbols = resolver.toKspLsiRoundSymbols(frontendOptions)
        workspace = currentRoundSymbols.allValidRootTypes.toLsiWorkspace(resolver, frontendOptions)
        val currentWorkspace = currentRoundSymbols.currentValidRootTypes.toLsiWorkspace(
            resolver,
            frontendOptions,
        )
        inputResources = inputResources + inputResourceReader.read(inputResourcePaths)
        val roundResult = session.execute(
            CompilerRound(
                number = nextRoundNumber,
                workspace = workspace,
                currentWorkspace = currentWorkspace,
                platform = CompilerPlatform.KSP,
                options = options,
                inputResources = inputResources,
            ),
        )
        nextRoundNumber++
        lastRoundGeneratedSources = roundResult.generatedSources
        roundResult.diagnostics.forEach { diagnostic ->
            emitDiagnostic(diagnostic, currentRoundSymbols.annotatedById)
        }
        roundResult.newArtifacts.forEach { artifact ->
            writer.write(artifact, currentRoundSymbols.filesById)
        }
        return deferredSymbols(currentRoundSymbols, roundResult)
    }

    fun finish(): CompilerRoundResult {
        inputResources = inputResources + inputResourceReader.read(inputResourcePaths)
        val roundResult = session.execute(
            CompilerRound(
                number = nextRoundNumber,
                workspace = workspace,
                currentWorkspace = LsiWorkspace.EMPTY,
                platform = CompilerPlatform.KSP,
                isFinal = true,
                options = options,
                inputResources = inputResources,
            ),
        )
        nextRoundNumber++
        lastRoundGeneratedSources = roundResult.generatedSources
        roundResult.diagnostics.forEach { diagnostic ->
            emitDiagnostic(diagnostic, emptyMap())
        }
        roundResult.newArtifacts.forEach { artifact ->
            writer.write(artifact, emptyMap())
        }
        return roundResult
    }

    private fun deferredSymbols(
        currentRoundSymbols: KspLsiRoundSymbols,
        roundResult: CompilerRoundResult,
    ): List<KSAnnotated> {
        val seen = Collections.newSetFromMap(IdentityHashMap<KSAnnotated, Boolean>())
        return buildList {
            for (invalidRoot in currentRoundSymbols.invalidRootTypes) {
                if (seen.add(invalidRoot)) {
                    add(invalidRoot)
                }
            }
            for (symbolId in roundResult.unresolvedSymbols.sorted()) {
                val symbol = currentRoundSymbols.annotatedById[symbolId] ?: continue
                if (seen.add(symbol)) {
                    add(symbol)
                }
            }
        }
    }

    private fun emitDiagnostic(
        diagnostic: LsiDiagnostic,
        currentRoundSymbols: Map<LsiSymbolId, KSAnnotated>,
    ) {
        val message = "[${diagnostic.code}] ${diagnostic.message}"
        val symbol = diagnostic.symbolId?.let(currentRoundSymbols::get)
        when (diagnostic.severity) {
            LsiDiagnosticSeverity.INFO -> logger.info(message, symbol)
            LsiDiagnosticSeverity.WARNING -> logger.warn(message, symbol)
            LsiDiagnosticSeverity.ERROR -> logger.error(message, symbol)
        }
    }
}
