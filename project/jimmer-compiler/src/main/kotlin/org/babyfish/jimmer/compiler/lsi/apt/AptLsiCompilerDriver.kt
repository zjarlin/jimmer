package org.babyfish.jimmer.compiler.lsi.apt

import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.tools.Diagnostic
import org.babyfish.jimmer.compiler.CompilerRound
import org.babyfish.jimmer.compiler.CompilerRoundResult
import org.babyfish.jimmer.compiler.CompilerSession
import org.babyfish.jimmer.compiler.CompilerPlatform
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureProvider
import org.babyfish.jimmer.compiler.JimmerCompilerFeatureProviders
import org.babyfish.jimmer.compiler.lsi.LsiFrontendOptions
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.diagnostic.LsiDiagnosticSeverity
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiWorkspace

/**
 * 把单个真实 APT 轮次冻结、调度并立即写回平台输出。
 */
class AptLsiCompilerDriver(
    private val processingEnvironment: ProcessingEnvironment,
    providers: Iterable<JimmerCompilerFeatureProvider> = JimmerCompilerFeatureProviders.load(),
    sessionId: String = "apt",
) {
    private val options = processingEnvironment.options.toSortedMap()

    private val frontendOptions = LsiFrontendOptions.from(options)

    private val providerList = providers.toList()

    private val inputResourcePaths = providerList
        .flatMapTo(sortedSetOf()) { provider -> provider.descriptor.inputResourcePaths }

    private val session = CompilerSession(sessionId, providerList)

    private val writer = AptGeneratedArtifactWriter(processingEnvironment.filer)

    private val inputResourceReader = AptCompilerInputResourceReader(processingEnvironment.filer)

    private var nextRoundNumber = 0

    private var workspace = LsiWorkspace.EMPTY

    private var pendingTypeIds = emptySet<LsiSymbolId>()

    private var inputResources = emptyMap<String, String>()

    fun process(roundEnvironment: RoundEnvironment): CompilerRoundResult {
        val isFinal = roundEnvironment.processingOver()
        val currentRoundSymbols = if (isFinal) {
            AptLsiRoundSymbols.EMPTY
        } else {
            val pendingRootTypes = pendingTypeIds.mapNotNull { typeId ->
                processingEnvironment.elementUtils.getTypeElement(typeId.requireTypeQualifiedName())
            }
            roundEnvironment.toAptLsiRoundSymbols(
                processingEnvironment,
                frontendOptions,
                pendingRootTypes,
            )
        }
        val currentWorkspace = if (isFinal) {
            LsiWorkspace.EMPTY
        } else {
            currentRoundSymbols.rootTypes.toLsiWorkspace(processingEnvironment, frontendOptions)
        }
        inputResources = inputResources + inputResourceReader.read(inputResourcePaths)
        workspace = workspace.merge(currentWorkspace)
        val roundResult = session.execute(
            CompilerRound(
                number = nextRoundNumber,
                workspace = workspace,
                currentWorkspace = currentWorkspace,
                platform = CompilerPlatform.APT,
                isFinal = isFinal,
                options = options,
                inputResources = inputResources,
            ),
        )
        nextRoundNumber++
        pendingTypeIds = roundResult.unresolvedSymbols
            .mapNotNullTo(linkedSetOf()) { symbolId -> symbolId.rootTypeIdOrNull() }
        roundResult.diagnostics.forEach { diagnostic ->
            emitDiagnostic(diagnostic, currentRoundSymbols)
        }
        roundResult.newArtifacts.forEach { artifact ->
            writer.write(artifact, currentRoundSymbols.elementsById)
        }
        return roundResult
    }

    private fun LsiSymbolId.rootTypeIdOrNull(): LsiSymbolId? {
        val rootTypeId = LsiSymbolId(value.substringBefore('/'))
        return runCatching { rootTypeId.requireTypeQualifiedName() }
            .getOrNull()
            ?.let { rootTypeId }
    }

    private fun emitDiagnostic(
        diagnostic: LsiDiagnostic,
        currentRoundSymbols: AptLsiRoundSymbols,
    ) {
        val kind = when (diagnostic.severity) {
            LsiDiagnosticSeverity.INFO -> Diagnostic.Kind.NOTE
            LsiDiagnosticSeverity.WARNING -> Diagnostic.Kind.WARNING
            LsiDiagnosticSeverity.ERROR -> Diagnostic.Kind.ERROR
        }
        val message = "[${diagnostic.code}] ${diagnostic.message}"
        val element = diagnostic.symbolId?.let(currentRoundSymbols.elementsById::get)
        if (element != null) {
            processingEnvironment.messager.printMessage(kind, message, element)
        } else {
            processingEnvironment.messager.printMessage(kind, message)
        }
    }
}
