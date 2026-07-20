package org.babyfish.jimmer.compiler.lsi.apt

import java.io.File
import java.io.IOException
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
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
import org.babyfish.jimmer.compiler.lsi.resolveLsiTypeSeedFixedPoint
import org.babyfish.jimmer.compiler.input.FileSystemCompilerInputDocumentScanner
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.diagnostic.LsiDiagnosticSeverity
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.core.LsiSourceKind
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiConstructor
import site.addzero.lsi.model.LsiDeclaration
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiField
import site.addzero.lsi.model.LsiFunction
import site.addzero.lsi.model.LsiParameter
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiUnresolvedType
import site.addzero.lsi.model.LsiWorkspace

/**
 * 把单个真实 APT 轮次冻结、调度并立即写回平台输出。
 */
class AptLsiCompilerDriver(
    private val processingEnvironment: ProcessingEnvironment,
    providers: Iterable<JimmerCompilerFeatureProvider> = JimmerCompilerFeatureProviders.load(),
    sessionId: String = "apt",
) {
    var lastRoundResult: CompilerRoundResult? = null
        private set

    private val options = processingEnvironment.options.toSortedMap()

    private val frontendOptions = LsiFrontendOptions.from(options)

    private val providerList = providers.toList()

    private val inputResourcePaths = providerList
        .flatMapTo(sortedSetOf()) { provider -> provider.descriptor.inputResourcePaths }

    private val classpathTypeIds = providerList
        .flatMapTo(sortedSetOf()) { provider -> provider.descriptor.classpathTypeIds }

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

    private var availableTypeIds = emptySet<LsiSymbolId>()

    private var inputDocumentSnapshots = emptyList<CompilerInputDocumentSnapshot>()

    private var javacErrorRaised = false

    private var latestActiveRoundDeferred = false

    fun process(roundEnvironment: RoundEnvironment): CompilerRoundResult {
        val isFinal = roundEnvironment.processingOver()
        javacErrorRaised = javacErrorRaised || roundEnvironment.errorRaised()
        if (!isFinal) {
            availableTypeIds = classpathTypeIds.filterTo(sortedSetOf()) { typeId ->
                processingEnvironment.elementUtils.getTypeElement(typeId.requireTypeQualifiedName()) != null
            }
        }
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
        val documentSeeds = inputDocumentSnapshots.flatMap { snapshot -> snapshot.typeSeeds }
        val previousWorkspace = workspace
        val knownSourceRootTypes = previousWorkspace.knownSourceRootTypes()
        val roundWorkspace = if (isFinal) {
            LsiWorkspace.EMPTY
        } else {
            currentRoundSymbols.rootTypes.toLsiWorkspace(
                processingEnvironment = processingEnvironment,
                frontendOptions = frontendOptions,
                packageElements = currentRoundSymbols.packageElements,
                additionalSeeds = documentSeeds,
                sourceRootTypes = currentRoundSymbols.sourceRootTypes,
                sourcePackageElements = currentRoundSymbols.sourcePackageElements,
                knownSourceRootTypes = knownSourceRootTypes,
                fallbackSourceKind = currentRoundFallbackSourceKind(),
            )
        }
        val currentWorkspace = if (isFinal) {
            LsiWorkspace.EMPTY
        } else {
            currentRoundSymbols.rootTypes.toLsiWorkspace(
                processingEnvironment = processingEnvironment,
                frontendOptions = frontendOptions,
                packageElements = currentRoundSymbols.packageElements,
                sourceRootTypes = currentRoundSymbols.sourceRootTypes,
                sourcePackageElements = currentRoundSymbols.sourcePackageElements,
                knownSourceRootTypes = knownSourceRootTypes,
                fallbackSourceKind = currentRoundFallbackSourceKind(),
            )
        }
        var currentFrontendDeferred = if (isFinal) {
            javacErrorRaised || latestActiveRoundDeferred
        } else {
            javacErrorRaised || currentWorkspace.containsUnresolvedTypes()
        }
        inputResources = inputResources + inputResourceReader.read(inputResourcePaths)
        val currentRootTypeIds = currentRoundSymbols.rootTypes
            .mapTo(sortedSetOf()) { type -> LsiSymbolId.type(type.qualifiedName.toString()) }
        workspace = previousWorkspace.merge(roundWorkspace)
        if (!isFinal) {
            workspace = resolveLsiTypeSeedFixedPoint(
                initialWorkspace = workspace,
                requestSeeds = { candidateWorkspace ->
                    session.requestedTypeSeeds(
                        compilerRound(
                            workspace = candidateWorkspace,
                            currentWorkspace = currentWorkspace,
                            currentRootTypeIds = currentRootTypeIds,
                            frontendDeferred = currentFrontendDeferred,
                        )
                    )
                },
                freezeWorkspace = { requestedSeeds ->
                    previousWorkspace.merge(
                        currentRoundSymbols.rootTypes.toLsiWorkspace(
                            processingEnvironment = processingEnvironment,
                            frontendOptions = frontendOptions,
                            packageElements = currentRoundSymbols.packageElements,
                            additionalSeeds = documentSeeds + requestedSeeds,
                            sourceRootTypes = currentRoundSymbols.sourceRootTypes,
                            sourcePackageElements = currentRoundSymbols.sourcePackageElements,
                            knownSourceRootTypes = knownSourceRootTypes,
                            fallbackSourceKind = currentRoundFallbackSourceKind(),
                        )
                    )
                },
            ).workspace
            currentFrontendDeferred = javacErrorRaised || workspace.containsUnresolvedTypes()
            latestActiveRoundDeferred = currentFrontendDeferred
        }
        val roundResult = session.execute(
            compilerRound(
                workspace = workspace,
                currentWorkspace = currentWorkspace,
                currentRootTypeIds = currentRootTypeIds,
                isFinal = isFinal,
                frontendDeferred = currentFrontendDeferred,
            )
        )
        lastRoundResult = roundResult
        nextRoundNumber++
        pendingTypeIds = roundResult.unresolvedSymbols
            .mapNotNullTo(linkedSetOf()) { symbolId -> symbolId.rootTypeIdOrNull() }
        roundResult.diagnostics.forEach { diagnostic ->
            emitDiagnostic(diagnostic, currentRoundSymbols)
        }
        val currentRoundSources = buildMap {
            currentWorkspace.declarations.forEach { declaration ->
                declaration.origin.source?.let { source -> put(declaration.id, source) }
            }
            currentWorkspace.annotationScopes.forEach { annotationScope ->
                annotationScope.origin.source?.let { source -> put(annotationScope.id, source) }
            }
        }
        roundResult.newArtifacts.forEach { artifact ->
            writer.write(
                artifact = artifact,
                currentRoundElements = currentRoundSymbols.elementsById,
                currentRoundSources = currentRoundSources,
            )
        }
        return roundResult
    }

    private fun compilerRound(
        workspace: LsiWorkspace,
        currentWorkspace: LsiWorkspace,
        currentRootTypeIds: Set<LsiSymbolId>,
        isFinal: Boolean = false,
        frontendDeferred: Boolean = javacErrorRaised,
    ): CompilerRound {
        return CompilerRound(
            number = nextRoundNumber,
            workspace = workspace,
            currentWorkspace = currentWorkspace,
            currentRootTypeIds = currentRootTypeIds,
            platform = CompilerPlatform.APT,
            isFinal = isFinal,
            options = options,
            availableTypeIds = availableTypeIds,
            frontendDeferred = frontendDeferred,
            inputResources = inputResources,
            inputDocumentSnapshots = inputDocumentSnapshots,
        )
    }

    private fun currentRoundFallbackSourceKind(): LsiSourceKind {
        return if (nextRoundNumber == 0) {
            LsiSourceKind.SOURCE
        } else {
            LsiSourceKind.GENERATED
        }
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

private fun LsiWorkspace.knownSourceRootTypes(): Map<String, LsiSource> {
    return declarationsOfType<LsiTypeDeclaration>()
        .asSequence()
        .filter { type -> type.enclosingTypeId == null }
        .mapNotNull { type -> type.origin.source?.let { source -> type.qualifiedName to source } }
        .toMap()
}

private fun LsiWorkspace.containsUnresolvedTypes(): Boolean {
    return declarations.any { declaration ->
        when (declaration) {
            is LsiTypeDeclaration -> declaration.superTypes.any(LsiTypeRef::containsUnresolvedType) ||
                declaration.typeParameters.any { parameter ->
                    parameter.upperBounds.any(LsiTypeRef::containsUnresolvedType)
                }
            is LsiField -> declaration.type.containsUnresolvedType()
            is LsiProperty -> declaration.type.containsUnresolvedType()
            is LsiFunction -> declaration.returnType.containsUnresolvedType() ||
                declaration.receiverType?.containsUnresolvedType() == true ||
                declaration.parameters.any { parameter -> parameter.type.containsUnresolvedType() } ||
                declaration.thrownTypes.any(LsiTypeRef::containsUnresolvedType)
            is LsiConstructor -> declaration.parameters.any { parameter ->
                parameter.type.containsUnresolvedType()
            } || declaration.thrownTypes.any(LsiTypeRef::containsUnresolvedType)
            is LsiParameter -> declaration.type.containsUnresolvedType()
            else -> false
        }
    }
}

private fun LsiTypeRef.containsUnresolvedType(): Boolean {
    return when (this) {
        is LsiUnresolvedType -> true
        is LsiDeclaredType -> arguments.any { argument ->
            argument.type?.containsUnresolvedType() == true
        }
        is LsiArrayType -> elementType.containsUnresolvedType()
        is LsiPrimitiveType,
        is LsiTypeParameterRef,
        -> false
    }
}
