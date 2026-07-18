package org.babyfish.jimmer.compiler.dto

import org.babyfish.jimmer.compiler.CompilerInputDocument
import org.babyfish.jimmer.compiler.CompilerInputDocumentKind
import org.babyfish.jimmer.compiler.CompilerInputDocumentSnapshot
import org.babyfish.jimmer.compiler.CompilerPlatform
import org.babyfish.jimmer.compiler.JimmerCompilerSourceFilter
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableSchema
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableTypeKind
import org.babyfish.jimmer.compiler.immutable.hasImmutableMarker
import org.babyfish.jimmer.dto.compiler.DtoAstException
import org.babyfish.jimmer.dto.compiler.DtoFile
import org.babyfish.jimmer.dto.compiler.DtoModifier
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.diagnostic.LsiDiagnosticSeverity
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiWorkspace

internal class JimmerDtoPrecompiler {
    fun compile(
        inputDocumentSnapshots: Collection<CompilerInputDocumentSnapshot>,
        immutableSchema: JimmerImmutableSchema,
        immutableSemanticRootTypeIds: Set<LsiSymbolId>,
        workspace: LsiWorkspace,
        sourceFilter: JimmerCompilerSourceFilter,
        defaultNullableInputModifier: DtoModifier,
        platform: CompilerPlatform,
    ): JimmerDtoPrecompileOutcome {
        require(defaultNullableInputModifier.isInputStrategy) {
            "Default nullable input modifier must be an input strategy"
        }
        require(platform != CompilerPlatform.UNKNOWN) {
            "DTO precompilation requires APT or KSP platform"
        }
        val registry = LsiDtoTypeRegistry(immutableSchema, workspace)
        val documents = mutableListOf<JimmerDtoPrecompiledDocument>()
        val unresolvedDocuments = mutableListOf<JimmerDtoUnresolvedDocument>()
        val failures = mutableListOf<JimmerDtoCompilerFailure>()
        inputDocumentSnapshots
            .filter { snapshot -> snapshot.document.kind == CompilerInputDocumentKind.DTO }
            .sorted()
            .forEach { snapshot ->
                val inputDocument = snapshot.document
                val compiler = try {
                    LsiDtoCompiler(
                        dtoFile = inputDocument.toDtoFile(),
                        registry = registry,
                        defaultNullableInputModifier = defaultNullableInputModifier,
                    )
                } catch (exception: DtoAstException) {
                    failures += JimmerDtoCompilerFailure(
                        inputSnapshot = snapshot,
                        baseTypeId = null,
                        code = DTO_INVALID_DIAGNOSTIC_CODE,
                        severity = LsiDiagnosticSeverity.ERROR,
                        symbolId = null,
                        location = exception.toLocation(snapshot),
                        message = exception.message ?: "Invalid DTO document '${inputDocument.source.path}'",
                        details = sortedMapOf("document" to inputDocument.source.path),
                    )
                    return@forEach
                }
                if (!sourceFilter.accepts(compiler.sourceTypeName)) {
                    return@forEach
                }
                val baseTypeId = LsiSymbolId.type(compiler.sourceTypeName)
                val workspaceType = workspace[baseTypeId] as? LsiTypeDeclaration
                if (workspaceType != null && !workspaceType.hasImmutableMarker()) {
                    failures += JimmerDtoCompilerFailure(
                        inputSnapshot = snapshot,
                        baseTypeId = baseTypeId,
                        code = DTO_INVALID_DIAGNOSTIC_CODE,
                        severity = LsiDiagnosticSeverity.ERROR,
                        symbolId = baseTypeId,
                        location = snapshot.referenceLocation(baseTypeId),
                        message = "DTO base type '${compiler.sourceTypeName}' is not an immutable type",
                        details = sortedMapOf("document" to inputDocument.source.path),
                    )
                    return@forEach
                }
                if (workspaceType != null && baseTypeId !in immutableSemanticRootTypeIds) {
                    return@forEach
                }
                val unresolvedTypeIds = buildSet {
                    snapshot.referencedTypeIds.filterTo(this) { typeId -> workspace[typeId] == null }
                    if (workspaceType == null) {
                        add(baseTypeId)
                    }
                }.sorted()
                if (unresolvedTypeIds.isNotEmpty()) {
                    unresolvedDocuments += JimmerDtoUnresolvedDocument(
                        inputSnapshot = snapshot,
                        baseTypeId = baseTypeId,
                        unresolvedTypeIds = unresolvedTypeIds,
                        message = "Cannot resolve DTO document '${inputDocument.source.path}' types: " +
                            unresolvedTypeIds.joinToString { typeId -> typeId.value },
                    )
                    return@forEach
                }
                val baseType = registry[baseTypeId]
                if (baseType == null) {
                    unresolvedDocuments += JimmerDtoUnresolvedDocument(
                        inputSnapshot = snapshot,
                        baseTypeId = baseTypeId,
                        unresolvedTypeIds = listOf(baseTypeId),
                        message = "No immutable type '${compiler.sourceTypeName}' for DTO document " +
                            "'${inputDocument.source.path}'",
                    )
                    return@forEach
                }
                if (baseType.immutableType.kind == JimmerImmutableTypeKind.MAPPED_SUPERCLASS) {
                    failures += JimmerDtoCompilerFailure(
                        inputSnapshot = snapshot,
                        baseTypeId = baseTypeId,
                        code = DTO_INVALID_DIAGNOSTIC_CODE,
                        severity = LsiDiagnosticSeverity.ERROR,
                        symbolId = baseTypeId,
                        location = snapshot.referenceLocation(baseTypeId),
                        message = "DTO base type '${compiler.sourceTypeName}' cannot be a mapped superclass",
                        details = sortedMapOf("document" to inputDocument.source.path),
                    )
                    return@forEach
                }
                val compiledTypes = try {
                    compiler.compile(baseType)
                } catch (exception: DtoAstException) {
                    failures += JimmerDtoCompilerFailure(
                        inputSnapshot = snapshot,
                        baseTypeId = baseTypeId,
                        code = DTO_INVALID_DIAGNOSTIC_CODE,
                        severity = LsiDiagnosticSeverity.ERROR,
                        symbolId = baseTypeId,
                        location = exception.toLocation(snapshot),
                        message = exception.message ?: "Invalid DTO document '${inputDocument.source.path}'",
                        details = sortedMapOf("document" to inputDocument.source.path),
                    )
                    return@forEach
                }
                val renderGraph = JimmerDtoRenderGraphFreezer(snapshot).freeze(compiledTypes)
                val annotationContract = JimmerDtoAnnotationContractFreezer(
                    workspace = workspace,
                    immutableSchema = immutableSchema,
                ).freeze(renderGraph)
                val interfaceContractResolution = DtoInterfaceContractResolver(workspace).resolve(renderGraph)
                val configContractResolution = DtoConfigContractResolver(
                    workspace = workspace,
                    immutableSchema = immutableSchema,
                    platform = platform,
                ).resolve(renderGraph)
                val semanticDiagnostics =
                    annotationContract.diagnostics +
                        interfaceContractResolution.diagnostics +
                        configContractResolution.diagnostics
                if (semanticDiagnostics.isEmpty() && configContractResolution.unresolvedTypeIds.isNotEmpty()) {
                    unresolvedDocuments += JimmerDtoUnresolvedDocument(
                        inputSnapshot = snapshot,
                        baseTypeId = baseTypeId,
                        unresolvedTypeIds = configContractResolution.unresolvedTypeIds,
                        message = "Cannot resolve DTO document '${inputDocument.source.path}' config implementations: " +
                            configContractResolution.unresolvedTypeIds.joinToString { typeId -> typeId.value },
                    )
                    return@forEach
                }
                failures += semanticDiagnostics.map { diagnostic ->
                    diagnostic.toCompilerFailure(snapshot, baseTypeId)
                }
                documents += JimmerDtoPrecompiledDocument(
                    inputSnapshot = snapshot,
                    baseTypeId = baseTypeId,
                    sourceTypeName = compiler.sourceTypeName,
                    targetPackageName = compiler.targetPackageName,
                    renderGraph = renderGraph,
                    annotationContract = annotationContract,
                    interfaceContractResolution = interfaceContractResolution,
                    configContractResolution = configContractResolution,
                )
            }
        return JimmerDtoPrecompileOutcome(
            schema = JimmerDtoPrecompiledSchema(documents),
            unresolvedDocuments = unresolvedDocuments.sortedBy(JimmerDtoUnresolvedDocument::inputSnapshot),
            failures = failures.sortedWith(JIMMER_DTO_COMPILER_FAILURE_COMPARATOR),
        )
    }
}

private fun LsiDiagnostic.toCompilerFailure(
    inputSnapshot: CompilerInputDocumentSnapshot,
    baseTypeId: LsiSymbolId,
): JimmerDtoCompilerFailure {
    return JimmerDtoCompilerFailure(
        inputSnapshot = inputSnapshot,
        baseTypeId = baseTypeId,
        code = code,
        severity = severity,
        symbolId = symbolId,
        location = location,
        message = message,
        details = details.toSortedMap(),
    )
}

private fun CompilerInputDocumentSnapshot.referenceLocation(typeId: LsiSymbolId): LsiLocation? {
    return references.firstOrNull { reference -> reference.typeId == typeId }?.location
}

private fun DtoAstException.toLocation(snapshot: CompilerInputDocumentSnapshot): LsiLocation {
    return LsiLocation(
        source = snapshot.document.source,
        start = LsiPosition(lineNumber, colNumber + 1),
    )
}

private fun CompilerInputDocument.toDtoFile(): DtoFile {
    val pathParts = relativePath.split('/')
    return DtoFile(
        source.path,
        content,
        projectName,
        sourceRoot,
        pathParts.dropLast(1),
        pathParts.last(),
    )
}

private const val DTO_INVALID_DIAGNOSTIC_CODE = "jimmer.dto.invalid"
