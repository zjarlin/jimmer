package org.babyfish.jimmer.compiler.dto

import org.babyfish.jimmer.compiler.CompilerInputDocument
import org.babyfish.jimmer.compiler.CompilerInputDocumentKind
import org.babyfish.jimmer.compiler.CompilerInputDocumentReference
import org.babyfish.jimmer.compiler.CompilerInputDocumentReferenceKind
import org.babyfish.jimmer.compiler.CompilerInputDocumentSnapshot
import org.babyfish.jimmer.compiler.CompilerInputDocumentTypeSelection
import org.babyfish.jimmer.compiler.CompilerInputDocumentTypeSelector
import org.babyfish.jimmer.compiler.CompilerPlatform
import org.babyfish.jimmer.compiler.JimmerCompilerSourceFilter
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableSchema
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableTypeKind
import org.babyfish.jimmer.compiler.immutable.hasImmutableMarker
import org.babyfish.jimmer.compiler.input.selectOwnerTarget
import org.babyfish.jimmer.compiler.input.selectType
import org.babyfish.jimmer.dto.compiler.DtoAstException
import org.babyfish.jimmer.dto.compiler.DtoCompiler
import org.babyfish.jimmer.dto.compiler.DtoFile
import org.babyfish.jimmer.dto.compiler.DtoModifier
import org.babyfish.jimmer.dto.compiler.DtoTypeLinker
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
        val failures = mutableListOf<JimmerDtoCompilerFailure>()
        val entries = inputDocumentSnapshots
            .filter { snapshot -> snapshot.document.kind == CompilerInputDocumentKind.DTO }
            .sorted()
            .mapNotNull { snapshot ->
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
                        targetTypeIds = snapshot.activeTargetTypeIds(
                            workspace = workspace,
                            immutableSemanticRootTypeIds = immutableSemanticRootTypeIds,
                            sourceFilter = sourceFilter,
                        ),
                        code = DTO_INVALID_DIAGNOSTIC_CODE,
                        severity = LsiDiagnosticSeverity.ERROR,
                        symbolId = null,
                        location = exception.toLocation(snapshot),
                        message = exception.message ?: "Invalid DTO document '${inputDocument.source.path}'",
                        details = sortedMapOf("document" to inputDocument.source.path),
                    )
                    return@mapNotNull null
                }
                val referenceResolution = snapshot.resolveStaticReferences(
                    workspace = workspace,
                    immutableSemanticRootTypeIds = immutableSemanticRootTypeIds,
                    sourceFilter = sourceFilter,
                )
                val targetTypeIds = referenceResolution.targetTypeIds
                if (referenceResolution.ambiguities.isNotEmpty()) {
                    failures += referenceResolution.ambiguities.map { ambiguity ->
                        ambiguity.toFailure(snapshot, targetTypeIds)
                    }
                    return@mapNotNull null
                }
                JimmerDtoCompilerEntry(
                    inputSnapshot = snapshot,
                    compiler = compiler,
                    targetTypeIds = targetTypeIds,
                    referenceResolution = referenceResolution,
                )
            }
        val unresolvedDocuments = mutableListOf<JimmerDtoUnresolvedDocument>()
        entries.forEach { entry ->
            val snapshot = entry.inputSnapshot
            val inputDocument = snapshot.document
            if (
                entry.targetTypeIds.isEmpty() &&
                snapshot.references.any { reference -> reference.kind.isDtoTarget() }
            ) {
                return@forEach
            }
            val invalidTargetTypeIds = entry.targetTypeIds.filter { targetTypeId ->
                val workspaceType = workspace[targetTypeId] as? LsiTypeDeclaration
                workspaceType != null && !workspaceType.hasImmutableMarker()
            }
            invalidTargetTypeIds.forEach { targetTypeId ->
                failures += JimmerDtoCompilerFailure(
                    inputSnapshot = snapshot,
                    targetTypeIds = entry.targetTypeIds,
                    code = DTO_INVALID_DIAGNOSTIC_CODE,
                    severity = LsiDiagnosticSeverity.ERROR,
                    symbolId = targetTypeId,
                    location = snapshot.referenceLocation(targetTypeId),
                    message = "DTO target type '${targetTypeId.value}' is not an immutable type",
                    details = sortedMapOf("document" to inputDocument.source.path),
                )
            }
            if (invalidTargetTypeIds.isNotEmpty()) {
                return@forEach
            }
            val unresolvedTypeIds = buildSet {
                entry.targetTypeIds.filterTo(this) { targetTypeId ->
                    val workspaceType = workspace[targetTypeId] as? LsiTypeDeclaration
                    workspaceType == null ||
                        workspaceType.hasImmutableMarker() && registry[targetTypeId] == null
                }
                snapshot.references
                    .asSequence()
                    .filterNot { reference ->
                        reference.kind.isDtoTarget() ||
                            reference.kind == CompilerInputDocumentReferenceKind.REUSABLE_DTO_TYPE
                    }
                    .filter { reference -> reference in entry.referenceResolution.typeIds }
                    .filter { reference ->
                        val ownerTargetTypeId = entry.referenceResolution.ownerTargetTypeIds.getValue(reference)
                        ownerTargetTypeId == null || ownerTargetTypeId in entry.targetTypeIds
                    }
                    .filter { reference ->
                        val typeId = entry.referenceResolution.typeIds.getValue(reference)
                        val declaration = workspace[typeId] as? LsiTypeDeclaration
                        when (reference.kind) {
                            CompilerInputDocumentReferenceKind.MODEL_TYPE ->
                                declaration == null ||
                                    declaration.hasImmutableMarker() && registry[typeId] == null

                            else -> declaration == null
                        }
                    }
                    .mapTo(this) { reference -> entry.referenceResolution.typeIds.getValue(reference) }
            }.sorted()
            if (unresolvedTypeIds.isNotEmpty()) {
                unresolvedDocuments += JimmerDtoUnresolvedDocument(
                    inputSnapshot = snapshot,
                    targetTypeIds = entry.targetTypeIds,
                    unresolvedTypeIds = unresolvedTypeIds,
                    message = "Cannot resolve DTO document '${inputDocument.source.path}' types: " +
                        unresolvedTypeIds.joinToString { typeId -> typeId.value },
                )
            }
        }
        if (failures.isNotEmpty() || unresolvedDocuments.isNotEmpty()) {
            return JimmerDtoPrecompileOutcome(
                schema = JimmerDtoPrecompiledSchema(emptyList()),
                unresolvedDocuments = unresolvedDocuments.sortedBy(JimmerDtoUnresolvedDocument::inputSnapshot),
                failures = failures.sortedWith(JIMMER_DTO_COMPILER_FAILURE_COMPARATOR),
            )
        }
        val compiledByCompiler = try {
            DtoCompiler.compileAll(entries.map(JimmerDtoCompilerEntry::compiler)) { qualifiedName ->
                val targetTypeId = LsiSymbolId.type(qualifiedName)
                if (!sourceFilter.accepts(qualifiedName)) {
                    false
                } else {
                    val targetType = workspace[targetTypeId] as? LsiTypeDeclaration
                    targetType == null || !targetType.hasImmutableMarker() ||
                        targetTypeId in immutableSemanticRootTypeIds
                }
            }
        } catch (exception: DtoAstException) {
            failures += exception.toFailure(entries)
            return JimmerDtoPrecompileOutcome(
                schema = JimmerDtoPrecompiledSchema(emptyList()),
                unresolvedDocuments = emptyList(),
                failures = failures.sortedWith(JIMMER_DTO_COMPILER_FAILURE_COMPARATOR),
            )
        }
        val compiledTypes = compiledByCompiler.values.flatten()
        val sourceDtoTypeIds = compiledTypes
            .mapNotNull { dtoType -> dtoType.qualifiedName }
            .mapTo(hashSetOf(), LsiSymbolId::type)
        entries.forEach entryLoop@{ entry ->
            if (!entry.isActive()) {
                return@entryLoop
            }
            val unresolvedReusableDtoTypeIds = sortedSetOf<LsiSymbolId>()
            entry.inputSnapshot.references
                .asSequence()
                .filter { reference ->
                    reference.kind == CompilerInputDocumentReferenceKind.REUSABLE_DTO_TYPE
                }
                .filter { reference -> reference in entry.referenceResolution.ownerTargetTypeIds }
                .filter { reference ->
                    val ownerTargetTypeId = entry.referenceResolution.ownerTargetTypeIds.getValue(reference)
                    ownerTargetTypeId == null || ownerTargetTypeId in entry.targetTypeIds
                }
                .forEach referenceLoop@{ reference ->
                    val selection = reference.selectType(workspace, sourceDtoTypeIds)
                    if (selection.isAmbiguous) {
                        failures += JimmerDtoSelectorAmbiguity(
                            selector = reference.typeSelector,
                            selection = selection,
                            location = reference.location,
                        ).toFailure(entry.inputSnapshot, entry.targetTypeIds)
                        return@referenceLoop
                    }
                    val typeId = requireNotNull(selection.selectedTypeId)
                    if (typeId !in sourceDtoTypeIds && workspace[typeId] !is LsiTypeDeclaration) {
                        unresolvedReusableDtoTypeIds += typeId
                    }
                }
            if (unresolvedReusableDtoTypeIds.isNotEmpty()) {
                unresolvedDocuments += JimmerDtoUnresolvedDocument(
                    inputSnapshot = entry.inputSnapshot,
                    targetTypeIds = entry.targetTypeIds,
                    unresolvedTypeIds = unresolvedReusableDtoTypeIds.toList(),
                    message = "Cannot resolve DTO document " +
                        "'${entry.inputSnapshot.document.source.path}' reusable DTO types: " +
                        unresolvedReusableDtoTypeIds.joinToString { typeId -> typeId.value },
                )
            }
        }
        if (failures.isNotEmpty() || unresolvedDocuments.isNotEmpty()) {
            return JimmerDtoPrecompileOutcome(
                schema = JimmerDtoPrecompiledSchema(emptyList()),
                unresolvedDocuments = unresolvedDocuments.sortedBy(JimmerDtoUnresolvedDocument::inputSnapshot),
                failures = failures.sortedWith(JIMMER_DTO_COMPILER_FAILURE_COMPARATOR),
            )
        }
        try {
            val reusableDtoTypeResolver = LsiDtoTypeInfoResolver(registry, platform)
            DtoTypeLinker.link(compiledTypes, reusableDtoTypeResolver::resolve)
        } catch (exception: DtoAstException) {
            failures += exception.toFailure(entries)
            return JimmerDtoPrecompileOutcome(
                schema = JimmerDtoPrecompiledSchema(emptyList()),
                unresolvedDocuments = emptyList(),
                failures = failures.sortedWith(JIMMER_DTO_COMPILER_FAILURE_COMPARATOR),
            )
        }
        compiledByCompiler.forEach { (compiler, compiledTypes) ->
            val entry = entries.first { candidate -> candidate.compiler === compiler }
            compiledTypes
                .map { dtoType -> dtoType.baseType }
                .filter { baseType ->
                    baseType.immutableType.kind == JimmerImmutableTypeKind.MAPPED_SUPERCLASS
                }
                .distinctBy(LsiDtoBaseType::id)
                .forEach { baseType ->
                    failures += JimmerDtoCompilerFailure(
                        inputSnapshot = entry.inputSnapshot,
                        targetTypeIds = entry.targetTypeIds,
                        code = DTO_INVALID_DIAGNOSTIC_CODE,
                        severity = LsiDiagnosticSeverity.ERROR,
                        symbolId = baseType.id,
                        location = entry.inputSnapshot.referenceLocation(baseType.id),
                        message = "DTO target type '${baseType.qualifiedName}' cannot be a mapped superclass",
                        details = sortedMapOf(
                            "document" to entry.inputSnapshot.document.source.path,
                        ),
                    )
                }
        }
        if (failures.isNotEmpty()) {
            return JimmerDtoPrecompileOutcome(
                schema = JimmerDtoPrecompiledSchema(emptyList()),
                unresolvedDocuments = emptyList(),
                failures = failures.sortedWith(JIMMER_DTO_COMPILER_FAILURE_COMPARATOR),
            )
        }
        val documents = entries
            .filter(JimmerDtoCompilerEntry::isActive)
            .map { entry ->
                val snapshot = entry.inputSnapshot
                val inputDocument = snapshot.document
                val renderGraph = JimmerDtoRenderGraphFreezer(snapshot).freeze(
                    compiledByCompiler.getValue(entry.compiler),
                )
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
                        targetTypeIds = entry.targetTypeIds,
                        unresolvedTypeIds = configContractResolution.unresolvedTypeIds,
                        message = "Cannot resolve DTO document '${inputDocument.source.path}' config implementations: " +
                            configContractResolution.unresolvedTypeIds.joinToString { typeId -> typeId.value },
                    )
                }
                failures += semanticDiagnostics.map { diagnostic ->
                    diagnostic.toCompilerFailure(snapshot, entry.targetTypeIds)
                }
                JimmerDtoPrecompiledDocument(
                    inputSnapshot = snapshot,
                    targetTypeIds = entry.targetTypeIds,
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
    targetTypeIds: List<LsiSymbolId>,
): JimmerDtoCompilerFailure {
    return JimmerDtoCompilerFailure(
        inputSnapshot = inputSnapshot,
        targetTypeIds = targetTypeIds,
        code = code,
        severity = severity,
        symbolId = symbolId,
        location = location,
        message = message,
        details = details.toSortedMap(),
    )
}

private data class JimmerDtoCompilerEntry(
    val inputSnapshot: CompilerInputDocumentSnapshot,
    val compiler: LsiDtoCompiler,
    val targetTypeIds: List<LsiSymbolId>,
    val referenceResolution: JimmerDtoStaticReferenceResolution,
) {
    fun isActive(): Boolean {
        return targetTypeIds.isNotEmpty() ||
            inputSnapshot.references.none { reference -> reference.kind.isDtoTarget() }
    }
}

private data class JimmerDtoStaticReferenceResolution(
    val targetTypeIds: List<LsiSymbolId>,
    val typeIds: Map<CompilerInputDocumentReference, LsiSymbolId>,
    val ownerTargetTypeIds: Map<CompilerInputDocumentReference, LsiSymbolId?>,
    val ambiguities: List<JimmerDtoSelectorAmbiguity>,
)

private data class JimmerDtoSelectorAmbiguity(
    val selector: CompilerInputDocumentTypeSelector,
    val selection: CompilerInputDocumentTypeSelection,
    val location: LsiLocation,
) {
    init {
        require(selection.isAmbiguous) { "DTO selector ambiguity requires conflicting type ids" }
    }

    fun toFailure(
        inputSnapshot: CompilerInputDocumentSnapshot,
        targetTypeIds: List<LsiSymbolId>,
    ): JimmerDtoCompilerFailure {
        val conflicts = selection.conflictingTypeIds
        return JimmerDtoCompilerFailure(
            inputSnapshot = inputSnapshot,
            targetTypeIds = targetTypeIds,
            code = DTO_INVALID_DIAGNOSTIC_CODE,
            severity = LsiDiagnosticSeverity.ERROR,
            symbolId = null,
            location = location,
            message = "Ambiguous type name \"${selector.sourceName}\", both " +
                "\"${conflicts[0].requireTypeQualifiedName()}\" and " +
                "\"${conflicts[1].requireTypeQualifiedName()}\" are matched by wildcard imports",
            details = sortedMapOf("document" to inputSnapshot.document.source.path),
        )
    }
}

private fun CompilerInputDocumentSnapshot.resolveStaticReferences(
    workspace: LsiWorkspace,
    immutableSemanticRootTypeIds: Set<LsiSymbolId>,
    sourceFilter: JimmerCompilerSourceFilter,
): JimmerDtoStaticReferenceResolution {
    val typeIds = linkedMapOf<CompilerInputDocumentReference, LsiSymbolId>()
    val ownerTargetTypeIds = linkedMapOf<CompilerInputDocumentReference, LsiSymbolId?>()
    val ambiguitiesBySelector = linkedMapOf<CompilerInputDocumentTypeSelector, JimmerDtoSelectorAmbiguity>()
    references
        .filter { reference -> reference.kind.isDtoTarget() }
        .forEach { reference ->
            val selection = reference.selectType(workspace)
            if (selection.isAmbiguous) {
                ambiguitiesBySelector[reference.typeSelector] =
                    JimmerDtoSelectorAmbiguity(reference.typeSelector, selection, reference.location)
                return@forEach
            }
            val typeId = requireNotNull(selection.selectedTypeId)
            typeIds[reference] = typeId
            ownerTargetTypeIds[reference] = typeId
        }
    val targetTypeIds = typeIds
        .asSequence()
        .filter { (reference, _) -> reference.kind.isDtoTarget() }
        .map { (_, typeId) -> typeId }
        .distinct()
        .filter { typeId -> sourceFilter.accepts(typeId.requireTypeQualifiedName()) }
        .filter { typeId ->
            val declaration = workspace[typeId] as? LsiTypeDeclaration
            declaration == null || !declaration.hasImmutableMarker() ||
                typeId in immutableSemanticRootTypeIds
        }
        .sorted()
        .toList()
    if (ambiguitiesBySelector.isNotEmpty()) {
        return JimmerDtoStaticReferenceResolution(
            targetTypeIds = targetTypeIds,
            typeIds = typeIds,
            ownerTargetTypeIds = ownerTargetTypeIds,
            ambiguities = ambiguitiesBySelector.values.toList(),
        )
    }
    references
        .filterNot { reference -> reference.kind.isDtoTarget() }
        .forEach referenceLoop@{ reference ->
            val ownerSelection = reference.selectOwnerTarget(workspace)
            if (ownerSelection?.isAmbiguous == true) {
                val ownerSelector = requireNotNull(reference.ownerTargetSelector)
                val ownerLocation = references.firstOrNull { candidate ->
                    candidate.kind.isDtoTarget() && candidate.typeSelector == ownerSelector
                }?.location ?: reference.location
                ambiguitiesBySelector.putIfAbsent(
                    ownerSelector,
                    JimmerDtoSelectorAmbiguity(ownerSelector, ownerSelection, ownerLocation),
                )
                return@referenceLoop
            }
            val ownerTargetTypeId = ownerSelection?.selectedTypeId
            if (ownerTargetTypeId != null && ownerTargetTypeId !in targetTypeIds) {
                return@referenceLoop
            }
            ownerTargetTypeIds[reference] = ownerTargetTypeId
            if (reference.kind == CompilerInputDocumentReferenceKind.REUSABLE_DTO_TYPE) {
                return@referenceLoop
            }
            val selection = reference.selectType(workspace)
            if (selection.isAmbiguous) {
                ambiguitiesBySelector.putIfAbsent(
                    reference.typeSelector,
                    JimmerDtoSelectorAmbiguity(reference.typeSelector, selection, reference.location),
                )
                return@referenceLoop
            }
            typeIds[reference] = requireNotNull(selection.selectedTypeId)
        }
    return JimmerDtoStaticReferenceResolution(
        targetTypeIds = targetTypeIds,
        typeIds = typeIds,
        ownerTargetTypeIds = ownerTargetTypeIds,
        ambiguities = ambiguitiesBySelector.values.toList(),
    )
}

private fun CompilerInputDocumentSnapshot.activeTargetTypeIds(
    workspace: LsiWorkspace,
    immutableSemanticRootTypeIds: Set<LsiSymbolId>,
    sourceFilter: JimmerCompilerSourceFilter,
): List<LsiSymbolId> {
    return resolveStaticReferences(
        workspace = workspace,
        immutableSemanticRootTypeIds = immutableSemanticRootTypeIds,
        sourceFilter = sourceFilter,
    ).targetTypeIds
}

private fun CompilerInputDocumentReferenceKind.isDtoTarget(): Boolean {
    return this == CompilerInputDocumentReferenceKind.SUBJECT_TYPE ||
        this == CompilerInputDocumentReferenceKind.TARGET_TYPE
}

private fun CompilerInputDocumentSnapshot.referenceLocation(typeId: LsiSymbolId): LsiLocation? {
    return references.firstOrNull { reference ->
        typeId in reference.typeSelector.candidateTypeIds ||
            typeId in reference.ownerTargetSelector?.candidateTypeIds.orEmpty()
    }?.location
}

private fun DtoAstException.toFailure(
    entries: List<JimmerDtoCompilerEntry>,
): JimmerDtoCompilerFailure {
    val entry = requireNotNull(entries.singleOrNull { candidate ->
        candidate.inputSnapshot.document.source.path == absolutePath
    }) {
        "DTO compiler exception path does not match one input document: $absolutePath"
    }
    return JimmerDtoCompilerFailure(
        inputSnapshot = entry.inputSnapshot,
        targetTypeIds = entry.targetTypeIds,
        code = DTO_INVALID_DIAGNOSTIC_CODE,
        severity = LsiDiagnosticSeverity.ERROR,
        symbolId = null,
        location = toLocation(entry.inputSnapshot),
        message = message ?: "Invalid DTO document '${entry.inputSnapshot.document.source.path}'",
        details = sortedMapOf("document" to entry.inputSnapshot.document.source.path),
    )
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
