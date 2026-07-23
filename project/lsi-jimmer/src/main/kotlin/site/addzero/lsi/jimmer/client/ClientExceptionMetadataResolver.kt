package site.addzero.lsi.jimmer.client

import site.addzero.lsi.jimmer.error.ErrorFamily
import site.addzero.lsi.jimmer.error.ErrorSchema
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiModality
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiWorkspace

internal class ClientExceptionMetadataResolver private constructor(
    private val knownTypeIds: Set<LsiSymbolId>,
    private val metadataProvider: (LsiSymbolId) -> Collection<ClientExceptionMetadata>,
) {
    constructor(metadata: Collection<ClientExceptionMetadata>) : this(
        knownTypeIds = metadata.mapTo(linkedSetOf(), ClientExceptionMetadata::typeId),
        metadataProvider = { metadata },
    )

    private var cachedMetadata: List<ClientExceptionMetadata>? = null

    fun resolve(
        directTypeIds: Collection<LsiSymbolId>,
        operationId: LsiSymbolId,
    ): ClientExceptionResolution {
        val orderedDirectTypeIds = directTypeIds.distinct()
        if (orderedDirectTypeIds.none(knownTypeIds::contains)) {
            return ClientExceptionResolution(emptyList(), emptyList())
        }
        val metadataByTypeId = validatedMetadata(operationId)
        val directMetadata = orderedDirectTypeIds.mapNotNull(metadataByTypeId::get)
        val exceptionTypeIds = linkedSetOf<LsiSymbolId>()
        directMetadata.forEach { metadata ->
            collectExceptionTypeIds(metadata, metadataByTypeId, exceptionTypeIds)
        }
        val rootTypeIds = linkedSetOf<LsiSymbolId>()
        directMetadata.forEach { metadata ->
            rootTypeIds += rootMetadata(metadata, metadataByTypeId).typeId
        }
        val resolvedTypeIds = linkedSetOf<LsiSymbolId>()
        rootTypeIds.forEach { rootTypeId ->
            collectMetadataTypeIds(
                metadata = requireNotNull(metadataByTypeId[rootTypeId]),
                metadataByTypeId = metadataByTypeId,
                resolvedTypeIds = resolvedTypeIds,
            )
        }
        return ClientExceptionResolution(
            typeIds = exceptionTypeIds.toList(),
            metadata = resolvedTypeIds.map { typeId -> requireNotNull(metadataByTypeId[typeId]) },
        )
    }

    private fun validatedMetadata(
        operationId: LsiSymbolId,
    ): Map<LsiSymbolId, ClientExceptionMetadata> {
        val metadata = cachedMetadata ?: metadataProvider(operationId).toList().also { resolved ->
            cachedMetadata = resolved
        }
        val metadataByTypeId = linkedMapOf<LsiSymbolId, ClientExceptionMetadata>()
        metadata.groupBy(ClientExceptionMetadata::typeId).forEach { (typeId, candidates) ->
            val distinctCandidates = candidates.distinct()
            if (distinctCandidates.size != 1) {
                throw invalidExceptionTree(
                    operationId,
                    "Conflicting client exception metadata for '${typeId.value}'",
                )
            }
            metadataByTypeId[typeId] = distinctCandidates.single()
        }
        metadataByTypeId.values.forEach { value ->
            validateNode(value, metadataByTypeId, operationId)
        }
        validateCycles(metadataByTypeId, operationId)
        validateUniqueCodes(metadataByTypeId.values, operationId)
        return metadataByTypeId
    }

    private fun validateNode(
        metadata: ClientExceptionMetadata,
        metadataByTypeId: Map<LsiSymbolId, ClientExceptionMetadata>,
        operationId: LsiSymbolId,
    ) {
        val hasCode = metadata.code != null
        val hasSubTypes = metadata.subTypeIds.isNotEmpty()
        if (hasCode == hasSubTypes) {
            throw invalidExceptionTree(
                operationId,
                "Client exception '${metadata.typeId.value}' must declare either code or subtypes, but not both",
            )
        }
        if (hasCode && metadata.abstract) {
            throw invalidExceptionTree(
                operationId,
                "Client exception '${metadata.typeId.value}' declares a code and cannot be abstract",
            )
        }
        if (hasSubTypes && !metadata.abstract) {
            throw invalidExceptionTree(
                operationId,
                "Client exception '${metadata.typeId.value}' declares subtypes and must be abstract",
            )
        }
        metadata.superTypeId?.let { superTypeId ->
            val superMetadata = metadataByTypeId[superTypeId] ?: throw invalidExceptionTree(
                operationId,
                "Client exception '${metadata.typeId.value}' references missing super type '${superTypeId.value}'",
            )
            if (metadata.typeId !in superMetadata.subTypeIds) {
                throw invalidExceptionTree(
                    operationId,
                    "Client exception '${metadata.typeId.value}' directly extends '${superTypeId.value}', " +
                        "but the super exception does not declare it as a subtype",
                )
            }
            validateInheritedMetadata(superMetadata, metadata, operationId)
        }
        metadata.subTypeIds.forEach { subTypeId ->
            val subMetadata = metadataByTypeId[subTypeId] ?: throw invalidExceptionTree(
                operationId,
                "Client exception '${metadata.typeId.value}' references missing subtype '${subTypeId.value}'",
            )
            if (subMetadata.superTypeId != metadata.typeId) {
                throw invalidExceptionTree(
                    operationId,
                    "Client exception '${metadata.typeId.value}' declares '${subTypeId.value}' as a subtype, " +
                        "but that type does not directly extend it",
                )
            }
            validateInheritedMetadata(metadata, subMetadata, operationId)
        }
    }

    private fun validateInheritedMetadata(
        superMetadata: ClientExceptionMetadata,
        subMetadata: ClientExceptionMetadata,
        operationId: LsiSymbolId,
    ) {
        if (superMetadata.family != subMetadata.family) {
            throw invalidExceptionTree(
                operationId,
                "Client exception '${subMetadata.typeId.value}' belongs to family '${subMetadata.family}', " +
                    "but its super exception belongs to '${superMetadata.family}'",
            )
        }
        if (superMetadata.checked != subMetadata.checked) {
            throw invalidExceptionTree(
                operationId,
                "Client exception '${subMetadata.typeId.value}' does not preserve the checked state of " +
                    "'${superMetadata.typeId.value}'",
            )
        }
        if (superMetadata.errorFamilyId != subMetadata.errorFamilyId) {
            throw invalidExceptionTree(
                operationId,
                "Client exception '${subMetadata.typeId.value}' does not preserve the error-family provenance of " +
                    "'${superMetadata.typeId.value}'",
            )
        }
    }

    private fun validateCycles(
        metadataByTypeId: Map<LsiSymbolId, ClientExceptionMetadata>,
        operationId: LsiSymbolId,
    ) {
        val visitedTypeIds = hashSetOf<LsiSymbolId>()
        val visitingTypeIds = linkedSetOf<LsiSymbolId>()

        fun visit(typeId: LsiSymbolId) {
            if (typeId in visitedTypeIds) {
                return
            }
            if (!visitingTypeIds.add(typeId)) {
                val cycle = (visitingTypeIds.dropWhile { visitingId -> visitingId != typeId } + typeId)
                    .joinToString(" -> ") { cycleTypeId -> cycleTypeId.value }
                throw invalidExceptionTree(
                    operationId,
                    "Client exception hierarchy contains a cycle: $cycle",
                )
            }
            metadataByTypeId[typeId]?.subTypeIds.orEmpty().forEach(::visit)
            visitingTypeIds -= typeId
            visitedTypeIds += typeId
        }

        metadataByTypeId.keys.forEach(::visit)
    }

    private fun validateUniqueCodes(
        metadata: Collection<ClientExceptionMetadata>,
        operationId: LsiSymbolId,
    ) {
        val metadataByError = linkedMapOf<Pair<String, String>, ClientExceptionMetadata>()
        metadata.forEach { value ->
            val code = value.code ?: return@forEach
            val key = value.family to code
            val conflict = metadataByError.putIfAbsent(key, value)
            if (conflict != null && conflict.typeId != value.typeId) {
                throw invalidExceptionTree(
                    operationId,
                    "Client exceptions '${conflict.typeId.value}' and '${value.typeId.value}' share family " +
                        "'${value.family}' and code '$code'",
                )
            }
        }
    }

    private fun collectExceptionTypeIds(
        metadata: ClientExceptionMetadata,
        metadataByTypeId: Map<LsiSymbolId, ClientExceptionMetadata>,
        exceptionTypeIds: MutableSet<LsiSymbolId>,
    ) {
        if (metadata.code != null) {
            exceptionTypeIds += metadata.typeId
            return
        }
        metadata.subTypeIds.forEach { subTypeId ->
            collectExceptionTypeIds(requireNotNull(metadataByTypeId[subTypeId]), metadataByTypeId, exceptionTypeIds)
        }
    }

    private fun rootMetadata(
        metadata: ClientExceptionMetadata,
        metadataByTypeId: Map<LsiSymbolId, ClientExceptionMetadata>,
    ): ClientExceptionMetadata {
        var current = metadata
        while (current.superTypeId != null) {
            current = requireNotNull(metadataByTypeId[current.superTypeId])
        }
        return current
    }

    private fun collectMetadataTypeIds(
        metadata: ClientExceptionMetadata,
        metadataByTypeId: Map<LsiSymbolId, ClientExceptionMetadata>,
        resolvedTypeIds: MutableSet<LsiSymbolId>,
    ) {
        if (!resolvedTypeIds.add(metadata.typeId)) {
            return
        }
        metadata.subTypeIds.forEach { subTypeId ->
            collectMetadataTypeIds(requireNotNull(metadataByTypeId[subTypeId]), metadataByTypeId, resolvedTypeIds)
        }
    }

    companion object {
        fun from(
            workspace: LsiWorkspace,
            schema: ErrorSchema,
        ): ClientExceptionMetadataResolver {
            val generatedMetadata = schema.families.flatMap(ErrorFamily::toClientExceptionMetadata)
            val generatedMetadataByTypeId = generatedMetadata.associateBy(ClientExceptionMetadata::typeId)
            val manualTypeIds = workspace.declarationsOfType<LsiTypeDeclaration>()
                .filter { type ->
                    type.id !in generatedMetadataByTypeId &&
                        type.annotations.hasAnnotation(CLIENT_EXCEPTION_ANNOTATION)
                }
                .mapTo(linkedSetOf(), LsiTypeDeclaration::id)
            return ClientExceptionMetadataResolver(
                knownTypeIds = generatedMetadataByTypeId.keys + manualTypeIds,
                metadataProvider = { operationId ->
                    generatedMetadata + ManualClientExceptionMetadataCompiler(
                        workspace = workspace,
                        generatedMetadataByTypeId = generatedMetadataByTypeId,
                        operationId = operationId,
                    ).compile()
                },
            )
        }
    }
}

private class ManualClientExceptionMetadataCompiler(
    workspace: LsiWorkspace,
    private val generatedMetadataByTypeId: Map<LsiSymbolId, ClientExceptionMetadata>,
    private val operationId: LsiSymbolId,
) {
    private val typesById = workspace.declarationsOfType<LsiTypeDeclaration>()
        .associateBy(LsiTypeDeclaration::id)

    private val manualTypesById = typesById.values
        .filter { type ->
            type.id !in generatedMetadataByTypeId &&
                type.annotations.hasAnnotation(CLIENT_EXCEPTION_ANNOTATION)
        }
        .associateBy(LsiTypeDeclaration::id)

    private val metadataByTypeId = linkedMapOf<LsiSymbolId, ClientExceptionMetadata>()
    private val compilingTypeIds = linkedSetOf<LsiSymbolId>()

    fun compile(): List<ClientExceptionMetadata> {
        manualTypesById.values
            .sortedBy(LsiTypeDeclaration::qualifiedName)
            .forEach { type -> compile(type) }
        return metadataByTypeId.values.toList()
    }

    private fun compile(type: LsiTypeDeclaration): ClientExceptionMetadata {
        metadataByTypeId[type.id]?.let { metadata -> return metadata }
        if (!compilingTypeIds.add(type.id)) {
            throw invalidExceptionTree(
                operationId,
                "Client exception inheritance contains a cycle at '${type.id.value}'",
            )
        }
        try {
            if (type.kind != LsiTypeDeclarationKind.CLASS) {
                throw invalidExceptionTree(
                    operationId,
                    "Client exception '${type.id.value}' must be a class",
                )
            }
            val annotation = requireNotNull(type.annotations.annotation(CLIENT_EXCEPTION_ANNOTATION))
            val directSuperTypeId = type.directClassSuperTypeId(typesById)
            val superMetadata = directSuperTypeId?.let { superTypeId ->
                generatedMetadataByTypeId[superTypeId]
                    ?: manualTypesById[superTypeId]?.let(::compile)
            }
            val explicitFamily = annotation.stringValue("family")?.takeIf(String::isNotBlank)
            val metadata = ClientExceptionMetadata(
                typeId = type.id,
                errorFamilyId = superMetadata?.errorFamilyId,
                family = explicitFamily ?: superMetadata?.family ?: DEFAULT_EXCEPTION_FAMILY,
                code = annotation.stringValue("code")?.takeIf(String::isNotBlank),
                checked = superMetadata?.checked ?: type.checkedException(typesById, operationId),
                abstract = type.modality == LsiModality.ABSTRACT || type.modality == LsiModality.SEALED,
                superTypeId = superMetadata?.typeId,
                subTypeIds = annotation.classTypeIds("subTypes").distinct(),
                documentation = type.documentation.normalizedDocumentation(),
            )
            metadataByTypeId[type.id] = metadata
            return metadata
        } finally {
            compilingTypeIds -= type.id
        }
    }
}

private fun ErrorFamily.toClientExceptionMetadata(): List<ClientExceptionMetadata> {
    val codeMetadata = codes.map { code ->
        ClientExceptionMetadata(
            typeId = code.exceptionTypeId,
            errorFamilyId = id,
            family = family,
            code = code.code,
            checked = checkedException,
            abstract = false,
            superTypeId = exceptionTypeId,
            subTypeIds = emptyList(),
            documentation = code.documentation,
        )
    }
    val baseMetadata = ClientExceptionMetadata(
        typeId = exceptionTypeId,
        errorFamilyId = id,
        family = family,
        code = null,
        checked = checkedException,
        abstract = true,
        superTypeId = null,
        subTypeIds = codeMetadata.map(ClientExceptionMetadata::typeId).distinct(),
        documentation = documentation,
    )
    return listOf(baseMetadata) + codeMetadata
}

private fun LsiTypeDeclaration.checkedException(
    typesById: Map<LsiSymbolId, LsiTypeDeclaration>,
    operationId: LsiSymbolId,
): Boolean {
    val visitingTypeIds = linkedSetOf<LsiSymbolId>()

    fun checked(typeId: LsiSymbolId): Boolean {
        if (typeId == CODE_BASED_EXCEPTION) {
            return true
        }
        if (typeId == CODE_BASED_RUNTIME_EXCEPTION) {
            return false
        }
        if (!visitingTypeIds.add(typeId)) {
            throw invalidExceptionTree(
                operationId,
                "Client exception inheritance contains a cycle at '${typeId.value}'",
            )
        }
        val type = typesById[typeId] ?: throw invalidExceptionTree(
            operationId,
            "Client exception '${this.id.value}' has unresolved super type '${typeId.value}'",
        )
        val superTypeId = type.directClassSuperTypeId(typesById) ?: throw invalidExceptionTree(
            operationId,
            "Client exception '${this.id.value}' must extend '${CODE_BASED_EXCEPTION.value}' or " +
                "'${CODE_BASED_RUNTIME_EXCEPTION.value}'",
        )
        val result = checked(superTypeId)
        visitingTypeIds -= typeId
        return result
    }

    return checked(id)
}

private fun LsiTypeDeclaration.directClassSuperTypeId(
    typesById: Map<LsiSymbolId, LsiTypeDeclaration>,
): LsiSymbolId? {
    val superTypeIds = superTypes.mapNotNull(LsiTypeRef::declaredTypeId)
    return superTypeIds.firstOrNull { typeId ->
        typeId == CODE_BASED_EXCEPTION ||
            typeId == CODE_BASED_RUNTIME_EXCEPTION ||
            typesById[typeId]?.kind == LsiTypeDeclarationKind.CLASS
    } ?: superTypeIds.firstOrNull()
}

private fun List<LsiAnnotation>.annotation(type: LsiSymbolId): LsiAnnotation? {
    return firstOrNull { annotation -> annotation.type == type }
}

private fun List<LsiAnnotation>.hasAnnotation(type: LsiSymbolId): Boolean {
    return any { annotation -> annotation.type == type }
}

private fun LsiAnnotation.stringValue(name: String): String? {
    return (arguments[name]?.value as? LsiAnnotationValue.StringValue)?.value
}

private fun LsiAnnotation.classTypeIds(name: String): List<LsiSymbolId> {
    val value = arguments[name]?.value ?: return emptyList()
    return when (value) {
        is LsiAnnotationValue.ClassValue -> listOfNotNull(value.type.declaredTypeId())
        is LsiAnnotationValue.ArrayValue -> value.elements.mapNotNull { element ->
            (element as? LsiAnnotationValue.ClassValue)?.type?.declaredTypeId()
        }
        else -> emptyList()
    }
}

private fun LsiTypeRef.declaredTypeId(): LsiSymbolId? {
    return (this as? LsiDeclaredType)?.declarationId
}

private fun String?.normalizedDocumentation(): String? {
    return this
        ?.replace("\r\n", "\n")
        ?.replace('\r', '\n')
        ?.trim()
        ?.takeIf(String::isNotEmpty)
}

private fun invalidExceptionTree(
    operationId: LsiSymbolId,
    message: String,
): ClientValidationException {
    return ClientValidationException(
        declarationId = operationId,
        message = message,
    )
}

internal data class ClientExceptionResolution(
    val typeIds: List<LsiSymbolId>,
    val metadata: List<ClientExceptionMetadata>,
)

private const val DEFAULT_EXCEPTION_FAMILY = "DEFAULT"

private val CLIENT_EXCEPTION_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.ClientException")
private val CODE_BASED_EXCEPTION = LsiSymbolId.type("org.babyfish.jimmer.error.CodeBasedException")
private val CODE_BASED_RUNTIME_EXCEPTION =
    LsiSymbolId.type("org.babyfish.jimmer.error.CodeBasedRuntimeException")
