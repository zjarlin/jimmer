package org.babyfish.jimmer.compiler.client

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaration
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiFunction
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiParameter
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiUnresolvedType
import site.addzero.lsi.model.LsiVariance
import site.addzero.lsi.model.LsiVisibility
import site.addzero.lsi.model.LsiWorkspace

data class ClientPrecompileOptions(
    val explicitApi: Boolean = false,
)

class ClientPrecompileException(
    val declarationId: LsiSymbolId,
    val recoverable: Boolean = false,
    message: String,
) : IllegalArgumentException(message)

class ClientPrecompiler(
    private val options: ClientPrecompileOptions = ClientPrecompileOptions(),
) {
    fun compile(workspace: LsiWorkspace): ClientPrecompiledSchema {
        return compile(workspace, targets(workspace))
    }

    fun compile(
        workspace: LsiWorkspace,
        targets: ClientPrecompileTargets,
    ): ClientPrecompiledSchema {
        val unresolvedTypeIds = unresolvedTargetTypeIds(workspace, targets)
        if (unresolvedTypeIds.isNotEmpty()) {
            val unresolvedTypeId = unresolvedTypeIds.first()
            throw ClientPrecompileException(
                declarationId = unresolvedTypeId,
                recoverable = true,
                message = "Client declaration '${unresolvedTypeId.value}' cannot be fully resolved",
            )
        }
        val types = workspace.declarationsOfType<LsiTypeDeclaration>()
            .sortedBy(LsiTypeDeclaration::qualifiedName)
        val services = types
            .filter { type -> type.id in targets.serviceTypeIds }
            .map { service -> compileService(service, types, workspace) }
            .sortedBy { service -> service.id }
        return ClientPrecompiledSchema(
            services = services,
            exportedDocs = compileExportedDocs(types, targets.exportedTypeIds, workspace),
        )
    }

    fun targets(workspace: LsiWorkspace): ClientPrecompileTargets {
        val types = workspace.declarationsOfType<LsiTypeDeclaration>()
            .sortedBy(LsiTypeDeclaration::qualifiedName)
        return ClientPrecompileTargets(
            serviceTypeIds = types
                .filter(::isApiService)
                .mapTo(sortedSetOf(), LsiTypeDeclaration::id),
            exportedTypeIds = exportedTypeIds(types),
        )
    }

    fun unresolvedTargetTypeIds(
        workspace: LsiWorkspace,
        targets: ClientPrecompileTargets,
    ): Set<LsiSymbolId> {
        return targets.rootTypeIds
            .filterTo(sortedSetOf()) { typeId ->
                val type = workspace[typeId] as? LsiTypeDeclaration
                type == null ||
                    type.hasMissingMember(workspace) ||
                    type.hasUnresolvedAnnotations() ||
                    typeId in targets.serviceTypeIds && type.hasUnresolvedServiceSurface(workspace)
            }
    }

    private fun LsiTypeDeclaration.hasMissingMember(workspace: LsiWorkspace): Boolean {
        return memberIds.any { memberId -> workspace[memberId] == null }
    }

    private fun LsiTypeDeclaration.hasUnresolvedServiceSurface(workspace: LsiWorkspace): Boolean {
        return memberIds
            .mapNotNull(workspace::get)
            .filter { declaration -> declaration is LsiFunction || declaration is LsiProperty }
            .filterNot { declaration -> declaration.annotations.hasAnnotation(API_IGNORE_ANNOTATION) }
            .filter(::isApiOperation)
            .any(LsiDeclaration::hasUnresolvedClientType)
    }

    private fun compileService(
        service: LsiTypeDeclaration,
        allTypes: List<LsiTypeDeclaration>,
        workspace: LsiWorkspace,
    ): ClientService {
        validateService(service, allTypes)
        val groups = service.annotations.apiGroups()
        val operations = service.memberIds
            .map { memberId ->
                workspace[memberId] ?: throw ClientPrecompileException(
                    declarationId = service.id,
                    recoverable = true,
                    message = "Client API service '${service.qualifiedName}' references missing member " +
                        "'${memberId.value}'",
                )
            }
            .filter { declaration -> declaration is LsiFunction || declaration is LsiProperty }
            .filterNot { declaration -> declaration.annotations.hasAnnotation(API_IGNORE_ANNOTATION) }
            .filter(::isApiOperation)
            .map { declaration -> compileOperation(service, groups, declaration, workspace) }
            .sortedBy { operation -> operation.id }
        return ClientService(
            id = service.id,
            qualifiedName = service.qualifiedName,
            groups = groups,
            doc = service.clientDoc(),
            operations = operations,
        )
    }

    private fun compileOperation(
        service: LsiTypeDeclaration,
        serviceGroups: List<String>,
        declaration: LsiDeclaration,
        workspace: LsiWorkspace,
    ): ClientOperation {
        validateOperation(declaration)
        val function = declaration as? LsiFunction
        val property = declaration as? LsiProperty
        val name = function?.name ?: requireNotNull(property).getterName
        val rawParameters = function?.parameters.orEmpty()
        val defaultFetcherOwnerId = service.defaultFetcherOwnerId()
        val parameters = rawParameters
            .filterNot { parameter -> parameter.annotations.hasAnnotation(API_IGNORE_ANNOTATION) }
            .map { parameter ->
                ClientParameter(
                    id = parameter.id,
                    name = parameter.name,
                    originalIndex = parameter.index,
                    type = parameter.type.toClientTypeRef(
                        annotations = parameter.annotations,
                        serviceId = service.id,
                        defaultFetcherOwnerId = defaultFetcherOwnerId,
                        sourceId = parameter.id,
                        workspace = workspace,
                    ),
                )
            }
        val ignoredParameters = rawParameters
            .filter { parameter -> parameter.annotations.hasAnnotation(API_IGNORE_ANNOTATION) }
            .map { parameter -> parameter.toIgnoredParameter() }
        val operationGroups = declaration.annotations.apiGroups()
        validateOperationGroups(service, declaration, serviceGroups, operationGroups)
        val returnType = (function?.returnType ?: property?.type)
            ?.takeUnless(LsiTypeRef::isVoidLike)
            ?.toClientTypeRef(
                annotations = declaration.annotations,
                serviceId = service.id,
                defaultFetcherOwnerId = defaultFetcherOwnerId,
                sourceId = declaration.id,
                workspace = workspace,
            )
        val operationId = LsiSymbolId.function(
            owner = service.id,
            name = name,
            parameterTypeSignatures = rawParameters.map { parameter ->
                parameter.type.toClientTypeRef(
                    annotations = emptyList(),
                    serviceId = service.id,
                    defaultFetcherOwnerId = defaultFetcherOwnerId,
                    sourceId = parameter.id,
                    workspace = workspace,
                ).stableTypeSignature()
            },
        )
        return ClientOperation(
            id = operationId,
            name = name,
            groups = operationGroups,
            doc = declaration.clientDoc(),
            parameters = parameters,
            ignoredParameters = ignoredParameters,
            returnType = returnType,
            directExceptionTypeIds = function?.thrownTypes
                .orEmpty()
                .mapNotNull(LsiTypeRef::declaredTypeId)
                .distinct()
                .sorted(),
        )
    }

    private fun validateService(
        service: LsiTypeDeclaration,
        allTypes: List<LsiTypeDeclaration>,
    ) {
        val enclosingType = service.enclosingType(allTypes)
        if (enclosingType != null) {
            throw ClientPrecompileException(
                declarationId = service.id,
                message = "Client API service '${service.qualifiedName}' must be top-level",
            )
        }
        if (service.typeParameters.isNotEmpty()) {
            throw ClientPrecompileException(
                declarationId = service.id,
                message = "Client API service '${service.qualifiedName}' cannot declare type parameters",
            )
        }
    }

    private fun validateOperation(declaration: LsiDeclaration) {
        if (declaration.visibility != LsiVisibility.PUBLIC) {
            throw ClientPrecompileException(
                declarationId = declaration.id,
                message = "Client API operation '${declaration.id.value}' must be public",
            )
        }
        val static = when (declaration) {
            is LsiFunction -> declaration.static
            is LsiProperty -> declaration.static
            else -> false
        }
        if (static) {
            throw ClientPrecompileException(
                declarationId = declaration.id,
                message = "Client API operation '${declaration.id.value}' cannot be static",
            )
        }
        val typeParameters = (declaration as? LsiFunction)?.typeParameters.orEmpty()
        if (typeParameters.isNotEmpty()) {
            throw ClientPrecompileException(
                declarationId = declaration.id,
                message = "Client API operation '${declaration.id.value}' cannot declare type parameters",
            )
        }
    }

    private fun validateOperationGroups(
        service: LsiTypeDeclaration,
        operation: LsiDeclaration,
        serviceGroups: List<String>,
        operationGroups: List<String>,
    ) {
        if (serviceGroups.isEmpty() || operationGroups.isEmpty()) {
            return
        }
        val illegalGroups = operationGroups.filterNot(serviceGroups::contains)
        if (illegalGroups.isEmpty()) {
            return
        }
        throw ClientPrecompileException(
            declarationId = operation.id,
            message = "Client API operation '${operation.id.value}' declares groups " +
                "${illegalGroups.joinToString()} outside service '${service.qualifiedName}'",
        )
    }

    private fun isApiService(type: LsiTypeDeclaration): Boolean {
        if (type.annotations.hasAnnotation(API_IGNORE_ANNOTATION)) {
            return false
        }
        if (type.annotations.hasAnnotation(API_ANNOTATION)) {
            return true
        }
        return options.explicitApi && type.annotations.hasAnnotation(REST_CONTROLLER_ANNOTATION)
    }

    private fun isApiOperation(declaration: LsiDeclaration): Boolean {
        if (declaration.annotations.hasAnnotation(API_ANNOTATION)) {
            return true
        }
        return options.explicitApi && SPRING_MAPPING_ANNOTATIONS.any(declaration.annotations::hasAnnotation)
    }

    private fun compileExportedDocs(
        types: List<LsiTypeDeclaration>,
        exportedTypeIds: Set<LsiSymbolId>,
        workspace: LsiWorkspace,
    ): List<ClientExportedDoc> {
        return buildList {
            for (type in types) {
                if (type.id !in exportedTypeIds) {
                    continue
                }
                type.clientDoc()?.let { doc ->
                    add(ClientExportedDoc(type.id, type.qualifiedName, doc))
                }
                type.memberIds
                    .map { memberId ->
                        workspace[memberId] ?: throw ClientPrecompileException(
                            declarationId = type.id,
                            recoverable = true,
                            message = "Exported client document type '${type.qualifiedName}' references missing " +
                                "member '${memberId.value}'",
                        )
                    }
                    .filterIsInstance<LsiProperty>()
                    .filterNot(LsiProperty::static)
                    .forEach { property ->
                        property.clientDoc()?.let { doc ->
                            add(
                                ClientExportedDoc(
                                    declarationId = property.id,
                                    key = "${type.qualifiedName}.${property.name}",
                                    content = doc,
                                )
                            )
                        }
                    }
            }
        }.distinctBy(ClientExportedDoc::key).sortedBy(ClientExportedDoc::key)
    }

    private fun exportedTypeIds(
        types: List<LsiTypeDeclaration>,
    ): Set<LsiSymbolId> {
        val exportByTypeId = mutableMapOf<LsiSymbolId, Boolean>()
        fun isExported(type: LsiTypeDeclaration): Boolean {
            exportByTypeId[type.id]?.let { exported -> return exported }
            val exportDoc = type.annotations.annotation(EXPORT_DOC_ANNOTATION)
            val exported = if (exportDoc != null) {
                !exportDoc.booleanValue("excluded")
            } else {
                type.enclosingType(types)?.let(::isExported) ?: false
            }
            exportByTypeId[type.id] = exported
            return exported
        }
        return types
            .asSequence()
            .filter { type -> type.kind in EXPORTABLE_TYPE_KINDS && isExported(type) }
            .mapTo(sortedSetOf(), LsiTypeDeclaration::id)
    }

    private fun LsiTypeRef.toClientTypeRef(
        annotations: List<LsiAnnotation>,
        serviceId: LsiSymbolId,
        defaultFetcherOwnerId: LsiSymbolId?,
        sourceId: LsiSymbolId,
        workspace: LsiWorkspace,
    ): ClientTypeRef {
        val fetchBy = annotations.annotation(FETCH_BY_ANNOTATION)?.toClientFetchBy(
            decoratedType = this,
            serviceId = serviceId,
            defaultFetcherOwnerId = defaultFetcherOwnerId,
            sourceId = sourceId,
            workspace = workspace,
        )
        val nullable = nullability == LsiNullability.NULLABLE ||
            annotations.any { annotation -> annotation.type in NULLABLE_ANNOTATIONS } ||
            fetchBy?.nullable == true
        return when (this) {
            is LsiDeclaredType -> ClientDeclaredTypeRef(
                typeId = declarationId,
                arguments = arguments.map { argument ->
                    argument.toClientTypeArgument(
                        serviceId = serviceId,
                        defaultFetcherOwnerId = defaultFetcherOwnerId,
                        sourceId = sourceId,
                        workspace = workspace,
                    )
                },
                nullable = nullable,
                fetchBy = fetchBy,
            )
            is LsiPrimitiveType -> ClientPrimitiveTypeRef(kind, nullable, fetchBy)
            is LsiArrayType -> ClientArrayTypeRef(
                elementType = elementType.toClientTypeRef(
                    annotations = emptyList(),
                    serviceId = serviceId,
                    defaultFetcherOwnerId = defaultFetcherOwnerId,
                    sourceId = sourceId,
                    workspace = workspace,
                ),
                nullable = nullable,
                fetchBy = fetchBy,
            )
            is LsiTypeParameterRef -> ClientTypeParameterRef(parameterId, nullable, fetchBy)
            is LsiUnresolvedType -> ClientUnresolvedTypeRef(displayName, nullable, fetchBy)
        }
    }

    private fun LsiTypeArgument.toClientTypeArgument(
        serviceId: LsiSymbolId,
        defaultFetcherOwnerId: LsiSymbolId?,
        sourceId: LsiSymbolId,
        workspace: LsiWorkspace,
    ): ClientTypeArgument {
        return ClientTypeArgument(
            variance = variance,
            type = type?.toClientTypeRef(
                annotations = emptyList(),
                serviceId = serviceId,
                defaultFetcherOwnerId = defaultFetcherOwnerId,
                sourceId = sourceId,
                workspace = workspace,
            ),
        )
    }

    private fun LsiAnnotation.toClientFetchBy(
        decoratedType: LsiTypeRef,
        serviceId: LsiSymbolId,
        defaultFetcherOwnerId: LsiSymbolId?,
        sourceId: LsiSymbolId,
        workspace: LsiWorkspace,
    ): ClientFetchBy {
        val value = stringValue("value")?.takeIf(String::isNotBlank)
            ?: throw ClientPrecompileException(
                declarationId = sourceId,
                message = "FetchBy on '${sourceId.value}' requires a non-blank value",
            )
        val explicitOwnerId = classTypeId("ownerType")?.takeUnless(LsiSymbolId::isVoidType)
        return ClientFetchBy(
            value = value,
            ownerTypeId = explicitOwnerId ?: defaultFetcherOwnerId ?: serviceId,
            targetEntityTypeId = decoratedType.inferFetchTargetTypeId(workspace),
            nullable = booleanValue("nullable"),
        )
    }

    private fun LsiTypeRef.inferFetchTargetTypeId(workspace: LsiWorkspace): LsiSymbolId? {
        val declaredTypeIds = buildList {
            collectDeclaredTypeIds(this@inferFetchTargetTypeId)
        }
        val entityTypeIds = declaredTypeIds.filter { typeId ->
            val type = workspace[typeId] as? LsiTypeDeclaration
            type?.annotations?.hasAnnotation(ENTITY_ANNOTATION) == true
        }.distinct()
        if (entityTypeIds.size == 1) {
            return entityTypeIds.single()
        }
        return (this as? LsiDeclaredType)?.declarationId
    }

    private fun MutableList<LsiSymbolId>.collectDeclaredTypeIds(type: LsiTypeRef) {
        when (type) {
            is LsiDeclaredType -> {
                add(type.declarationId)
                type.arguments.mapNotNull(LsiTypeArgument::type).forEach { argumentType ->
                    collectDeclaredTypeIds(argumentType)
                }
            }
            is LsiArrayType -> collectDeclaredTypeIds(type.elementType)
            is LsiPrimitiveType,
            is LsiTypeParameterRef,
            is LsiUnresolvedType,
            -> Unit
        }
    }

    private fun LsiTypeDeclaration.defaultFetcherOwnerId(): LsiSymbolId? {
        return annotations.annotation(DEFAULT_FETCHER_OWNER_ANNOTATION)
            ?.classTypeId("value")
            ?.takeUnless(LsiSymbolId::isVoidType)
    }

    private fun LsiParameter.toIgnoredParameter(): ClientIgnoredParameter {
        return ClientIgnoredParameter(
            id = id,
            name = name,
            originalIndex = index,
        )
    }
}

private fun LsiDeclaration.clientDoc(): String? {
    val source = documentation
        ?: annotations.annotation(DESCRIPTION_ANNOTATION)?.stringValue("value")
        ?: return null
    return source.normalizeDoc().takeIf(String::isNotBlank)
}

private fun LsiDeclaration.hasUnresolvedClientType(): Boolean {
    if (annotations.any(LsiAnnotation::hasUnresolvedClientType)) {
        return true
    }
    return when (this) {
        is LsiFunction -> {
            returnType.hasUnresolvedClientType() ||
                receiverType?.hasUnresolvedClientType() == true ||
                parameters.any { parameter ->
                    parameter.type.hasUnresolvedClientType() ||
                        parameter.annotations.any(LsiAnnotation::hasUnresolvedClientType)
                } ||
                thrownTypes.any(LsiTypeRef::hasUnresolvedClientType)
        }
        is LsiProperty -> type.hasUnresolvedClientType()
        else -> false
    }
}

private fun LsiTypeDeclaration.hasUnresolvedAnnotations(): Boolean {
    return annotations.any(LsiAnnotation::hasUnresolvedClientType)
}

private fun LsiTypeRef.hasUnresolvedClientType(): Boolean {
    return when (this) {
        is LsiDeclaredType -> arguments
            .mapNotNull(LsiTypeArgument::type)
            .any(LsiTypeRef::hasUnresolvedClientType)
        is LsiArrayType -> elementType.hasUnresolvedClientType()
        is LsiUnresolvedType -> true
        is LsiPrimitiveType,
        is LsiTypeParameterRef,
        -> false
    }
}

private fun LsiAnnotation.hasUnresolvedClientType(): Boolean {
    return arguments.values.any { argument -> argument.value.hasUnresolvedClientType() }
}

private fun LsiAnnotationValue.hasUnresolvedClientType(): Boolean {
    return when (this) {
        is LsiAnnotationValue.ClassValue -> type.hasUnresolvedClientType()
        is LsiAnnotationValue.NestedAnnotationValue -> annotation.hasUnresolvedClientType()
        is LsiAnnotationValue.ArrayValue -> elements.any(LsiAnnotationValue::hasUnresolvedClientType)
        is LsiAnnotationValue.BooleanValue,
        is LsiAnnotationValue.ByteValue,
        is LsiAnnotationValue.ShortValue,
        is LsiAnnotationValue.IntValue,
        is LsiAnnotationValue.LongValue,
        is LsiAnnotationValue.FloatValue,
        is LsiAnnotationValue.DoubleValue,
        is LsiAnnotationValue.CharValue,
        is LsiAnnotationValue.StringValue,
        is LsiAnnotationValue.EnumValue,
        -> false
    }
}

private fun String.normalizeDoc(): String {
    return replace("\r\n", "\n")
        .replace('\r', '\n')
        .lines()
        .joinToString("\n") { line -> line.trimEnd() }
        .trim()
}

private fun LsiTypeDeclaration.enclosingType(
    allTypes: List<LsiTypeDeclaration>,
): LsiTypeDeclaration? {
    return allTypes
        .asSequence()
        .filter { candidate -> candidate.id != id }
        .filter { candidate -> qualifiedName.startsWith("${candidate.qualifiedName}.") }
        .maxByOrNull { candidate -> candidate.qualifiedName.length }
}

private fun List<LsiAnnotation>.apiGroups(): List<String> {
    return annotation(API_ANNOTATION)
        ?.stringListValue("value")
        .orEmpty()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .sorted()
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

private fun LsiAnnotation.stringListValue(name: String): List<String> {
    return when (val value = arguments[name]?.value) {
        is LsiAnnotationValue.StringValue -> listOf(value.value)
        is LsiAnnotationValue.ArrayValue -> value.elements.mapNotNull { element ->
            (element as? LsiAnnotationValue.StringValue)?.value
        }
        else -> emptyList()
    }
}

private fun LsiAnnotation.booleanValue(name: String): Boolean {
    return (arguments[name]?.value as? LsiAnnotationValue.BooleanValue)?.value ?: false
}

private fun LsiAnnotation.classTypeId(name: String): LsiSymbolId? {
    val value = arguments[name]?.value as? LsiAnnotationValue.ClassValue ?: return null
    return value.type.declaredTypeId()
}

private fun LsiTypeRef.declaredTypeId(): LsiSymbolId? {
    return (this as? LsiDeclaredType)?.declarationId
}

private fun LsiTypeRef.isVoidLike(): Boolean {
    return this is LsiPrimitiveType && (kind == LsiPrimitiveKind.UNIT || kind == LsiPrimitiveKind.VOID)
}

private fun LsiSymbolId.isVoidType(): Boolean {
    return requireTypeQualifiedName() in VOID_TYPE_NAMES
}

private fun ClientTypeRef.stableTypeSignature(): String {
    val base = when (this) {
        is ClientDeclaredTypeRef -> buildString {
            append(typeId.value)
            if (arguments.isNotEmpty()) {
                append('<')
                append(arguments.joinToString(",") { argument -> argument.stableTypeSignature() })
                append('>')
            }
        }
        is ClientPrimitiveTypeRef -> "primitive:${kind.name.lowercase()}"
        is ClientArrayTypeRef -> "array:${elementType.stableTypeSignature()}"
        is ClientTypeParameterRef -> "parameter:${parameterId.value}"
        is ClientUnresolvedTypeRef -> "unresolved:${displayName.filterNot(Char::isWhitespace)}"
    }
    return if (nullable) "$base?" else base
}

private fun ClientTypeArgument.stableTypeSignature(): String {
    return when (variance) {
        LsiVariance.STAR -> "*"
        LsiVariance.INVARIANT -> requireNotNull(type).stableTypeSignature()
        LsiVariance.IN -> "in:${requireNotNull(type).stableTypeSignature()}"
        LsiVariance.OUT -> "out:${requireNotNull(type).stableTypeSignature()}"
    }
}

private val API_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.client.meta.Api")
private val API_IGNORE_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.client.ApiIgnore")
private val DESCRIPTION_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.client.Description")
private val EXPORT_DOC_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.client.ExportDoc")
private val FETCH_BY_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.client.FetchBy")
private val DEFAULT_FETCHER_OWNER_ANNOTATION =
    LsiSymbolId.type("org.babyfish.jimmer.client.meta.DefaultFetcherOwner")
private val ENTITY_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.Entity")
private val REST_CONTROLLER_ANNOTATION =
    LsiSymbolId.type("org.springframework.web.bind.annotation.RestController")

private val SPRING_MAPPING_ANNOTATIONS = listOf(
    "org.springframework.web.bind.annotation.RequestMapping",
    "org.springframework.web.bind.annotation.GetMapping",
    "org.springframework.web.bind.annotation.PostMapping",
    "org.springframework.web.bind.annotation.PutMapping",
    "org.springframework.web.bind.annotation.DeleteMapping",
    "org.springframework.web.bind.annotation.PatchMapping",
).map(LsiSymbolId::type)

private val NULLABLE_ANNOTATIONS = setOf(
    "edu.umd.cs.findbugs.annotations.Nullable",
    "jakarta.annotation.Nullable",
    "javax.annotation.Nullable",
    "org.babyfish.jimmer.client.TNullable",
    "org.jetbrains.annotations.Nullable",
    "org.jspecify.annotations.Nullable",
    "org.springframework.lang.Nullable",
).mapTo(linkedSetOf(), LsiSymbolId::type)

private val EXPORTABLE_TYPE_KINDS = setOf(
    LsiTypeDeclarationKind.CLASS,
    LsiTypeDeclarationKind.INTERFACE,
    LsiTypeDeclarationKind.ENUM,
)

private val VOID_TYPE_NAMES = setOf(
    "java.lang.Void",
    "kotlin.Nothing",
    "kotlin.Unit",
)
