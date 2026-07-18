package org.babyfish.jimmer.compiler.lsi.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import java.io.File
import java.util.Collections
import java.util.IdentityHashMap
import org.babyfish.jimmer.compiler.CompilerInputDocumentSnapshot
import org.babyfish.jimmer.compiler.CompilerPlatform
import org.babyfish.jimmer.compiler.CompilerRound
import org.babyfish.jimmer.compiler.CompilerRoundResult
import org.babyfish.jimmer.compiler.CompilerSession
import org.babyfish.jimmer.compiler.CompilerSourceSet
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureProvider
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureProviders
import org.babyfish.jimmer.compiler.lsi.LsiFrontendOptions
import org.babyfish.jimmer.compiler.input.FileSystemCompilerInputDocumentScanner
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

    private val classpathTypeIds = providerList
        .flatMapTo(sortedSetOf()) { provider -> provider.descriptor.classpathTypeIds }

    private val inputDocumentKinds = providerList
        .flatMapTo(sortedSetOf()) { provider -> provider.descriptor.inputDocumentKinds }

    private val session = CompilerSession(sessionId, providerList)

    private val logger = environment.logger

    private val writer = KspGeneratedArtifactWriter(environment.codeGenerator)

    private val inputResourceReader = KspCompilerInputResourceReader(environment.codeGenerator)

    private val inputDocumentScanner = FileSystemCompilerInputDocumentScanner()

    private var nextRoundNumber = 0

    private var workspace = LsiWorkspace.EMPTY

    private var inputResources = emptyMap<String, String>()

    private var availableTypeIds = emptySet<LsiSymbolId>()

    private var inputDocumentSnapshots = emptyList<CompilerInputDocumentSnapshot>()

    fun process(resolver: Resolver): List<KSAnnotated> {
        availableTypeIds = classpathTypeIds.filterTo(sortedSetOf()) { typeId ->
            val name = resolver.getKSNameFromString(typeId.requireTypeQualifiedName())
            resolver.getClassDeclarationByName(name) != null
        }
        val currentRoundSymbols = resolver.toKspLsiRoundSymbols(frontendOptions)
        inputResources = inputResources + inputResourceReader.read(inputResourcePaths)
        if (inputDocumentKinds.isNotEmpty()) {
            val sourceFiles = currentRoundSymbols.sourceFiles
                .map { file -> File(file.filePath) }
            inputDocumentSnapshots = inputDocumentScanner.scan(
                startPaths = sourceFiles,
                requestedKinds = inputDocumentKinds,
                sourceSet = sourceFiles.compilerSourceSet(),
                options = options,
            )
        }
        val documentSeeds = inputDocumentSnapshots.flatMap { snapshot -> snapshot.typeSeeds }
        workspace = currentRoundSymbols.allValidRootTypes.toLsiWorkspace(
            resolver = resolver,
            frontendOptions = frontendOptions,
            additionalSeeds = documentSeeds,
        )
        val currentWorkspace = currentRoundSymbols.currentValidRootTypes.toLsiWorkspace(
            resolver = resolver,
            frontendOptions = frontendOptions,
        )
        val currentRootTypeIds = currentRoundSymbols.currentValidRootTypes.mapTo(sortedSetOf()) { type ->
            LsiSymbolId.type(requireNotNull(type.qualifiedName?.asString()))
        }
        val roundResult = session.execute(
            CompilerRound(
                number = nextRoundNumber,
                workspace = workspace,
                currentWorkspace = currentWorkspace,
                currentRootTypeIds = currentRootTypeIds,
                platform = CompilerPlatform.KSP,
                options = options,
                availableTypeIds = availableTypeIds,
                inputResources = inputResources,
                inputDocumentSnapshots = inputDocumentSnapshots,
            ),
        )
        nextRoundNumber++
        lastRoundGeneratedSources = roundResult.generatedSources
        roundResult.diagnostics.forEach { diagnostic ->
            emitDiagnostic(diagnostic, currentRoundSymbols.annotatedById)
        }
        roundResult.newArtifacts.forEach { artifact ->
            writer.write(
                artifact = artifact,
                currentRoundFiles = currentRoundSymbols.filesById,
                currentRoundSourceFiles = currentRoundSymbols.sourceFiles,
            )
        }
        return deferredSymbols(currentRoundSymbols)
    }

    fun finish(): CompilerRoundResult {
        inputResources = inputResources + inputResourceReader.read(inputResourcePaths)
        val roundResult = session.execute(
            CompilerRound(
                number = nextRoundNumber,
                workspace = workspace,
                currentWorkspace = LsiWorkspace.EMPTY,
                currentRootTypeIds = emptySet(),
                platform = CompilerPlatform.KSP,
                isFinal = true,
                options = options,
                availableTypeIds = availableTypeIds,
                inputResources = inputResources,
                inputDocumentSnapshots = inputDocumentSnapshots,
            ),
        )
        nextRoundNumber++
        lastRoundGeneratedSources = roundResult.generatedSources
        roundResult.diagnostics.forEach { diagnostic ->
            emitDiagnostic(diagnostic, emptyMap())
        }
        roundResult.newArtifacts.forEach { artifact ->
            writer.write(
                artifact = artifact,
                currentRoundFiles = emptyMap(),
                currentRoundSourceFiles = emptyList(),
            )
        }
        return roundResult
    }

    private fun deferredSymbols(
        currentRoundSymbols: KspLsiRoundSymbols,
    ): List<KSAnnotated> {
        val seen = Collections.newSetFromMap(IdentityHashMap<KSAnnotated, Boolean>())
        return buildList {
            for (invalidRoot in currentRoundSymbols.invalidRootTypes) {
                if (seen.add(invalidRoot)) {
                    add(invalidRoot)
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

private fun List<File>.compilerSourceSet(): CompilerSourceSet {
    return if (any { file ->
        val path = file.invariantSeparatorsPath
        "/src/test/" in path || path.startsWith("src/test/")
    }) {
        CompilerSourceSet.TEST
    } else {
        CompilerSourceSet.MAIN
    }
}
