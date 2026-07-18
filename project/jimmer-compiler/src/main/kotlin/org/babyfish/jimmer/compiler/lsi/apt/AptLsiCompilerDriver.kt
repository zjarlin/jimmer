package org.babyfish.jimmer.compiler.lsi.apt

import java.io.File
import java.io.IOException
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic
import javax.tools.StandardLocation
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

    private val inputDocumentKinds = providerList
        .flatMapTo(sortedSetOf()) { provider -> provider.descriptor.inputDocumentKinds }

    private val session = CompilerSession(sessionId, providerList)

    private val writer = AptGeneratedArtifactWriter(processingEnvironment.filer)

    private val inputResourceReader = AptCompilerInputResourceReader(processingEnvironment.filer)

    private val inputDocumentScanner = FileSystemCompilerInputDocumentScanner()

    private var nextRoundNumber = 0

    private var workspace = LsiWorkspace.EMPTY

    private var pendingTypeIds = emptySet<LsiSymbolId>()

    private var inputResources = emptyMap<String, String>()

    private var inputDocumentSnapshots = emptyList<CompilerInputDocumentSnapshot>()

    fun process(roundEnvironment: RoundEnvironment): CompilerRoundResult {
        val isFinal = roundEnvironment.processingOver()
        if (!isFinal && inputDocumentKinds.isNotEmpty()) {
            val marker = classOutputMarker()
            inputDocumentSnapshots = inputDocumentScanner.scan(
                startPaths = listOf(marker),
                requestedKinds = inputDocumentKinds,
                sourceSet = marker.compilerSourceSet(),
                options = options,
            )
        }
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
        val roundWorkspace = if (isFinal) {
            LsiWorkspace.EMPTY
        } else {
            currentRoundSymbols.rootTypes.toLsiWorkspace(
                processingEnvironment = processingEnvironment,
                frontendOptions = frontendOptions,
                additionalSeeds = inputDocumentSnapshots.flatMap { snapshot -> snapshot.typeSeeds },
            )
        }
        val currentWorkspace = if (isFinal) {
            LsiWorkspace.EMPTY
        } else {
            currentRoundSymbols.rootTypes.toLsiWorkspace(
                processingEnvironment = processingEnvironment,
                frontendOptions = frontendOptions,
            )
        }
        inputResources = inputResources + inputResourceReader.read(inputResourcePaths)
        workspace = workspace.merge(roundWorkspace)
        val currentRootTypeIds = if (isFinal) {
            emptySet()
        } else {
            roundEnvironment.rootElements
                .filterIsInstance<TypeElement>()
                .mapTo(sortedSetOf()) { type -> LsiSymbolId.type(type.qualifiedName.toString()) }
        }
        val roundResult = session.execute(
            CompilerRound(
                number = nextRoundNumber,
                workspace = workspace,
                currentWorkspace = currentWorkspace,
                currentRootTypeIds = currentRootTypeIds,
                platform = CompilerPlatform.APT,
                isFinal = isFinal,
                options = options,
                inputResources = inputResources,
                inputDocumentSnapshots = inputDocumentSnapshots,
            ),
        )
        nextRoundNumber++
        pendingTypeIds = roundResult.unresolvedSymbols
            .mapNotNullTo(linkedSetOf()) { symbolId -> symbolId.rootTypeIdOrNull() }
        roundResult.diagnostics.forEach { diagnostic ->
            emitDiagnostic(diagnostic, currentRoundSymbols)
        }
        val currentRoundSources = currentWorkspace.declarations.mapNotNull { declaration ->
            declaration.origin.source?.let { source -> declaration.id to source }
        }.toMap()
        roundResult.newArtifacts.forEach { artifact ->
            writer.write(
                artifact = artifact,
                currentRoundElements = currentRoundSymbols.elementsById,
                currentRoundSources = currentRoundSources,
            )
        }
        return roundResult
    }

    private fun classOutputMarker(): File {
        val uri = try {
            processingEnvironment.filer
                .getResource(StandardLocation.CLASS_OUTPUT, "", "dummy.txt")
                .toUri()
        } catch (exception: IOException) {
            throw IllegalStateException("Cannot locate compiler class output for input documents", exception)
        }
        return try {
            File(uri)
        } catch (exception: IllegalArgumentException) {
            throw IllegalStateException("Compiler class output is not a local file: '$uri'", exception)
        }
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

private fun File.compilerSourceSet(): CompilerSourceSet {
    val path = invariantSeparatorsPath
    return if (path.endsWith("/test/dummy.txt") || "/test-classes/" in path) {
        CompilerSourceSet.TEST
    } else {
        CompilerSourceSet.MAIN
    }
}
