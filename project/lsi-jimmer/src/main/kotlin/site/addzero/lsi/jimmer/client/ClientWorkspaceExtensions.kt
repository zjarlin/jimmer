package site.addzero.lsi.jimmer.client

import site.addzero.lsi.jimmer.error.ErrorSchema
import site.addzero.lsi.jimmer.error.ErrorField
import site.addzero.lsi.jimmer.ImmutableProp
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaration
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiField
import site.addzero.lsi.model.LsiFunction
import site.addzero.lsi.model.LsiFunctionType
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
import site.addzero.lsi.model.LsiTypeSystem
import site.addzero.lsi.model.LsiTypeSeed
import site.addzero.lsi.model.LsiTypeSeedMode
import site.addzero.lsi.model.LsiUnresolvedType
import site.addzero.lsi.model.LsiVariance
import site.addzero.lsi.model.LsiVisibility
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.stableSignature

/** Client 语义模型的解析选项。 */
data class ClientSchemaOptions(
    val explicitApi: Boolean = false,
)

/** Client 语义解析所依赖的共享领域模型。 */
data class ClientSchemaDependencies(
    val immutableSchema: ImmutableSchema,
    val errorSchema: ErrorSchema,
    val definitionDocumentationByTypeId: Map<LsiSymbolId, ClientDefinitionDocumentation>,
)

/** Client 类型定义及其属性的冻结文档。 */
data class ClientDefinitionDocumentation(
    val type: String?,
    val properties: Map<String, String>,
) {
    init {
        require(properties.keys.none(String::isBlank)) {
            "Client definition documentation property name cannot be blank"
        }
        require(properties.values.none(String::isBlank)) {
            "Client definition property documentation cannot be blank"
        }
    }
}

/** Client 语义校验失败。 */
class ClientValidationException(
    val declarationId: LsiSymbolId,
    val rootTypeId: LsiSymbolId? = null,
    val recoverable: Boolean = false,
    message: String,
) : IllegalArgumentException(message)

/** 将当前工作区解析为完整的 Client 语义模型。 */
fun LsiWorkspace.toClientSchema(
    dependencies: ClientSchemaDependencies,
    options: ClientSchemaOptions = ClientSchemaOptions(),
): ClientSchema {
    return ClientSchemaBuilder(options).build(this, dependencies)
}

/** 将当前工作区中指定的 Client 目标解析为语义模型。 */
fun LsiWorkspace.toClientSchema(
    targets: ClientTargets,
    dependencies: ClientSchemaDependencies,
    options: ClientSchemaOptions = ClientSchemaOptions(),
): ClientSchema {
    return ClientSchemaBuilder(options).build(this, targets, dependencies)
}

/** 收集当前工作区中需要生成 Client 资源的服务目标。 */
fun LsiWorkspace.clientTargets(
    options: ClientSchemaOptions = ClientSchemaOptions(),
): ClientTargets {
    return ClientSchemaBuilder(options).targets(this)
}

/** 收集解析全部 Client 目标所需的完整类型种子。 */
fun LsiWorkspace.requestedClientTypeSeeds(
    dependencies: ClientSchemaDependencies = EMPTY_CLIENT_SCHEMA_DEPENDENCIES,
    options: ClientSchemaOptions = ClientSchemaOptions(),
): List<LsiTypeSeed> {
    val builder = ClientSchemaBuilder(options)
    return builder.requestedTypeSeeds(this, builder.targets(this), dependencies)
}

/** 收集解析指定 Client 目标所需的完整类型种子。 */
fun LsiWorkspace.requestedClientTypeSeeds(
    targets: ClientTargets,
    dependencies: ClientSchemaDependencies = EMPTY_CLIENT_SCHEMA_DEPENDENCIES,
    options: ClientSchemaOptions = ClientSchemaOptions(),
): List<LsiTypeSeed> {
    return ClientSchemaBuilder(options).requestedTypeSeeds(this, targets, dependencies)
}

/** 返回指定 Client 目标中尚未完整解析的类型标识。 */
fun LsiWorkspace.unresolvedClientTargetTypeIds(
    targets: ClientTargets,
    options: ClientSchemaOptions = ClientSchemaOptions(),
): Set<LsiSymbolId> {
    return ClientSchemaBuilder(options).unresolvedTargetTypeIds(this, targets)
}

private class ClientSchemaBuilder(
    private val options: ClientSchemaOptions = ClientSchemaOptions(),
) {
    fun build(
        workspace: LsiWorkspace,
        dependencies: ClientSchemaDependencies,
    ): ClientSchema {
        return build(workspace, targets(workspace), dependencies)
    }

    fun build(
        workspace: LsiWorkspace,
        targets: ClientTargets,
        dependencies: ClientSchemaDependencies,
    ): ClientSchema {
        val unresolvedTypeIds = unresolvedTargetTypeIds(workspace, targets)
        if (unresolvedTypeIds.isNotEmpty()) {
            val unresolvedTypeId = unresolvedTypeIds.first()
            throw ClientValidationException(
                declarationId = unresolvedTypeId,
                recoverable = true,
                message = "Client declaration '${unresolvedTypeId.value}' cannot be fully resolved",
            )
        }
        val types = workspace.declarationsOfType<LsiTypeDeclaration>()
            .sortedBy(LsiTypeDeclaration::qualifiedName)
        val exceptionResolver = ClientExceptionMetadataResolver.from(workspace, dependencies.errorSchema)
        val services = types
            .filter { type -> type.id in targets.serviceTypeIds }
            .map { service -> compileService(service, types, workspace, exceptionResolver) }
            .sortedBy { service -> service.id }
        val definitionsById = linkedMapOf<LsiSymbolId, ClientTypeDefinition>()
        services.forEach { service ->
            compileDefinitions(
                workspace = workspace,
                service = service,
                immutableSchema = dependencies.immutableSchema,
                errorSchema = dependencies.errorSchema,
                definitionDocumentationByTypeId = dependencies.definitionDocumentationByTypeId,
            ).forEach { definition -> definitionsById.putIfAbsent(definition.id, definition) }
        }
        return ClientSchema(
            services = services,
            definitions = definitionsById.values.sortedBy(ClientTypeDefinition::id),
        )
    }

    fun targets(workspace: LsiWorkspace): ClientTargets {
        val types = workspace.declarationsOfType<LsiTypeDeclaration>()
            .sortedBy(LsiTypeDeclaration::qualifiedName)
        return ClientTargets(
            serviceTypeIds = types
                .filter(::isApiService)
                .mapTo(sortedSetOf(), LsiTypeDeclaration::id),
        )
    }

    fun requestedTypeSeeds(
        workspace: LsiWorkspace,
        targets: ClientTargets,
        dependencies: ClientSchemaDependencies,
    ): List<LsiTypeSeed> {
        val seedIds = sortedSetOf<LsiSymbolId>()
        val definitionTypeIds = ArrayDeque<LsiSymbolId>()
        val scannedDefinitionTypeIds = mutableSetOf<LsiSymbolId>()
        val types = workspace.declarationsOfType<LsiTypeDeclaration>()
        dependencies.errorSchema.families.forEach { family ->
            (family.declaredFields + family.codes.flatMap { code -> code.declaredFields }).forEach { field ->
                field.type.collectClientTypeIds(
                    definitionTypeIds = definitionTypeIds,
                    seedIds = seedIds,
                    defaultFetcherOwnerId = null,
                    fallbackFetcherOwnerId = field.declaredBy,
                )
            }
        }
        targets.serviceTypeIds.forEach { serviceTypeId ->
            val service = workspace[serviceTypeId] as? LsiTypeDeclaration ?: return@forEach
            val defaultFetcherOwnerId = service.defaultFetcherOwnerId()
            defaultFetcherOwnerId?.let(seedIds::add)
            service.memberIds
                .mapNotNull(workspace::get)
                .filter { member -> member is LsiFunction || member is LsiProperty }
                .filterNot { member -> member.annotations.hasAnnotation(API_IGNORE_ANNOTATION) }
                .filter(::isApiOperation)
                .forEach { operation ->
                    operation.collectClientDefinitionTypeIds(
                        definitionTypeIds = definitionTypeIds,
                        seedIds = seedIds,
                        defaultFetcherOwnerId = defaultFetcherOwnerId,
                        serviceTypeId = serviceTypeId,
                    )
                }
        }
        while (definitionTypeIds.isNotEmpty()) {
            val typeId = definitionTypeIds.removeFirst()
            if (!workspace.clientTypeName(typeId).isDefinitionRequired()) {
                continue
            }
            seedIds += typeId
            if (!scannedDefinitionTypeIds.add(typeId)) {
                continue
            }
            val type = workspace[typeId] as? LsiTypeDeclaration ?: continue
            val immutable = type.annotations.hasAnyAnnotation(IMMUTABLE_TYPE_ANNOTATIONS)
            val clientException = type.annotations.hasAnnotation(CLIENT_EXCEPTION_ANNOTATION)
            val defaultFetcherOwnerId = type.defaultFetcherOwnerId()
            val immutablePropsByDeclarationId = dependencies.immutableSchema.typesById[type.id]
                ?.props
                .orEmpty()
                .associateBy(ImmutableProp::declarationId)
            workspace.jsonValueType(type.id)?.collectClientTypeIds(
                definitionTypeIds = definitionTypeIds,
                seedIds = seedIds,
                defaultFetcherOwnerId = defaultFetcherOwnerId,
                fallbackFetcherOwnerId = type.id,
            )
            type.superTypes.forEach { superType ->
                superType.collectClientTypeIds(
                    definitionTypeIds,
                    seedIds,
                    defaultFetcherOwnerId,
                    typeId,
                )
            }
            type.memberIds.mapNotNull(workspace::get).forEach { member ->
                when (member) {
                    is LsiProperty -> if (
                        member.isClientDefinitionProperty(type.kind, immutable, clientException)
                    ) {
                        val effectiveType = immutablePropsByDeclarationId[member.id]
                            ?.converter
                            ?.targetType
                            ?: member.type
                        effectiveType.collectClientTypeIds(
                            definitionTypeIds,
                            seedIds,
                            defaultFetcherOwnerId,
                            typeId,
                        )
                    }
                    is LsiFunction -> if (member.clientDefinitionPropertyName(clientException) != null) {
                        member.returnType.collectClientTypeIds(
                            definitionTypeIds,
                            seedIds,
                            defaultFetcherOwnerId,
                            typeId,
                        )
                    }
                    else -> Unit
                }
            }
            types.asSequence()
                .filter { candidate -> candidate.enclosingTypeId == type.id }
                .filter { candidate -> candidate.isPolymorphicBranchOf(type.id) }
                .mapTo(definitionTypeIds, LsiTypeDeclaration::id)
        }
        return seedIds.map { typeId -> LsiTypeSeed(typeId, LsiTypeSeedMode.FULL_DECLARATION) }
    }

    private fun LsiDeclaration.collectClientDefinitionTypeIds(
        definitionTypeIds: MutableCollection<LsiSymbolId>,
        seedIds: MutableSet<LsiSymbolId>,
        defaultFetcherOwnerId: LsiSymbolId?,
        serviceTypeId: LsiSymbolId,
    ) {
        annotations.collectClientFetcherOwnerIds(
            seedIds = seedIds,
            defaultFetcherOwnerId = defaultFetcherOwnerId,
            fallbackFetcherOwnerId = serviceTypeId,
        )
        when (this) {
            is LsiFunction -> {
                parameters
                    .filterNot { parameter -> parameter.annotations.hasAnnotation(API_IGNORE_ANNOTATION) }
                    .forEach { parameter ->
                        parameter.type.collectClientTypeIds(
                            definitionTypeIds,
                            seedIds,
                            defaultFetcherOwnerId,
                            serviceTypeId,
                        )
                    }
                returnType.collectClientTypeIds(
                    definitionTypeIds,
                    seedIds,
                    defaultFetcherOwnerId,
                    serviceTypeId,
                )
                thrownTypes.forEach { thrownType ->
                    thrownType.collectClientTypeIds(definitionTypeIds, seedIds, null, serviceTypeId)
                }
            }
            is LsiProperty -> type.collectClientTypeIds(
                definitionTypeIds,
                seedIds,
                defaultFetcherOwnerId,
                serviceTypeId,
            )
            else -> Unit
        }
    }

    private fun LsiTypeRef.collectClientTypeIds(
        definitionTypeIds: MutableCollection<LsiSymbolId>,
        seedIds: MutableSet<LsiSymbolId>,
        defaultFetcherOwnerId: LsiSymbolId?,
        fallbackFetcherOwnerId: LsiSymbolId,
    ) {
        annotations.collectClientFetcherOwnerIds(seedIds, defaultFetcherOwnerId, fallbackFetcherOwnerId)
        when (this) {
            is LsiDeclaredType -> {
                definitionTypeIds += declarationId
                arguments.mapNotNull(LsiTypeArgument::type).forEach { argumentType ->
                    argumentType.collectClientTypeIds(
                        definitionTypeIds,
                        seedIds,
                        defaultFetcherOwnerId,
                        fallbackFetcherOwnerId,
                    )
                }
            }
            is LsiArrayType -> elementType.collectClientTypeIds(
                definitionTypeIds,
                seedIds,
                defaultFetcherOwnerId,
                fallbackFetcherOwnerId,
            )
            is LsiFunctionType -> {
                receiverType?.collectClientTypeIds(
                    definitionTypeIds,
                    seedIds,
                    defaultFetcherOwnerId,
                    fallbackFetcherOwnerId,
                )
                parameterTypes.forEach { parameterType ->
                    parameterType.collectClientTypeIds(
                        definitionTypeIds,
                        seedIds,
                        defaultFetcherOwnerId,
                        fallbackFetcherOwnerId,
                    )
                }
                returnType.collectClientTypeIds(
                    definitionTypeIds,
                    seedIds,
                    defaultFetcherOwnerId,
                    fallbackFetcherOwnerId,
                )
            }
            is LsiPrimitiveType,
            is LsiTypeParameterRef,
            is LsiUnresolvedType,
            -> Unit
        }
    }

    private fun List<LsiAnnotation>.collectClientFetcherOwnerIds(
        seedIds: MutableSet<LsiSymbolId>,
        defaultFetcherOwnerId: LsiSymbolId?,
        fallbackFetcherOwnerId: LsiSymbolId,
    ) {
        annotation(FETCH_BY_ANNOTATION)?.let { fetchBy ->
            seedIds += fetchBy.classTypeId("ownerType")
                ?.takeUnless(LsiSymbolId::isVoidType)
                ?: defaultFetcherOwnerId
                ?: fallbackFetcherOwnerId
        }
    }

    fun unresolvedTargetTypeIds(
        workspace: LsiWorkspace,
        targets: ClientTargets,
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
        exceptionResolver: ClientExceptionMetadataResolver,
    ): ClientService {
        validateService(service, allTypes)
        val groups = service.annotations.apiGroups()
        val operations = service.memberIds
            .map { memberId ->
                workspace[memberId] ?: throw ClientValidationException(
                    declarationId = service.id,
                    recoverable = true,
                    message = "Client API service '${service.qualifiedName}' references missing member " +
                        "'${memberId.value}'",
                )
            }
            .filter { declaration -> declaration is LsiFunction || declaration is LsiProperty }
            .filterNot { declaration -> declaration.annotations.hasAnnotation(API_IGNORE_ANNOTATION) }
            .filter(::isApiOperation)
            .map { declaration ->
                compileOperation(service, groups, declaration, workspace, exceptionResolver)
            }
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
        exceptionResolver: ClientExceptionMetadataResolver,
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
                        sourceLanguage = parameter.origin.language,
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
                sourceLanguage = declaration.origin.language,
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
                    sourceLanguage = parameter.origin.language,
                    workspace = workspace,
                ).stableTypeSignature()
            },
        )
        val declaredExceptionTypeIds = function?.thrownTypes
            .orEmpty()
            .mapNotNull(LsiTypeRef::declaredTypeId)
            .distinct()
        val exceptionResolution = exceptionResolver.resolve(declaredExceptionTypeIds, operationId)
        return ClientOperation(
            id = operationId,
            name = name,
            groups = operationGroups,
            doc = declaration.clientDoc(),
            parameters = parameters,
            ignoredParameters = ignoredParameters,
            returnType = returnType,
            declaredExceptionTypeIds = declaredExceptionTypeIds,
            exceptionTypeIds = exceptionResolution.typeIds,
            exceptionMetadata = exceptionResolution.metadata,
        )
    }

    private fun compileDefinitions(
        workspace: LsiWorkspace,
        service: ClientService,
        immutableSchema: ImmutableSchema,
        errorSchema: ErrorSchema,
        definitionDocumentationByTypeId: Map<LsiSymbolId, ClientDefinitionDocumentation>,
    ): List<ClientTypeDefinition> {
        val exceptionMetadataByTypeId = service.operations
            .flatMap(ClientOperation::exceptionMetadata)
            .associateBy(ClientExceptionMetadata::typeId)
        val definitionsById = linkedMapOf<LsiSymbolId, ClientTypeDefinition>()
        val pendingTypeIds = ArrayDeque<LsiSymbolId>()
        service.operations.forEach { operation ->
            operation.parameters.forEach { parameter ->
                parameter.type.collectDefinitionTypeIds(pendingTypeIds)
            }
            operation.returnType?.collectDefinitionTypeIds(pendingTypeIds)
            pendingTypeIds.addAll(operation.exceptionTypeIds)
        }
        while (pendingTypeIds.isNotEmpty()) {
            val typeId = pendingTypeIds.removeFirst()
            if (typeId in definitionsById) {
                continue
            }
            val typeName = workspace.clientTypeName(typeId)
            if (!typeName.isDefinitionRequired()) {
                continue
            }
            val type = workspace[typeId] as? LsiTypeDeclaration
            val generatedError = errorSchema.generatedErrorType(typeId)
            if (type == null && generatedError == null) {
                throw ClientValidationException(
                    declarationId = typeId,
                    rootTypeId = service.id,
                    recoverable = true,
                    message = "Client definition '${typeId.value}' cannot be resolved",
                )
            }
            val definition = try {
                if (type != null) {
                    compileDefinition(
                        workspace = workspace,
                        rootServiceId = service.id,
                        type = type,
                        immutableSchema = immutableSchema,
                        definitionDocumentation = definitionDocumentationByTypeId[type.id],
                        exceptionMetadata = exceptionMetadataByTypeId[type.id],
                    )
                } else {
                    compileGeneratedErrorDefinition(
                        workspace = workspace,
                        rootServiceId = service.id,
                        generatedError = requireNotNull(generatedError),
                    )
                }
            } catch (exception: ClientValidationException) {
                if (exception.rootTypeId != null) {
                    throw exception
                }
                throw ClientValidationException(
                    declarationId = exception.declarationId,
                    rootTypeId = service.id,
                    recoverable = exception.recoverable,
                    message = exception.message ?: "Invalid client definition '${typeId.value}'",
                )
            }
            definitionsById[typeId] = definition
            definition.properties.forEach { property ->
                property.type.collectDefinitionTypeIds(pendingTypeIds)
            }
            definition.superTypes.forEach { superType ->
                superType.collectDefinitionTypeIds(pendingTypeIds)
            }
            definition.polymorphicBranches.forEach { branch ->
                branch.collectDefinitionTypeIds(pendingTypeIds)
            }
        }
        return definitionsById.values.sortedBy(ClientTypeDefinition::id)
    }

    private fun compileGeneratedErrorDefinition(
        workspace: LsiWorkspace,
        rootServiceId: LsiSymbolId,
        generatedError: GeneratedClientErrorType,
    ): ClientTypeDefinition {
        val typeId = generatedError.typeId
        val properties = generatedError.fields.map { field ->
            ClientDefinitionProperty(
                id = LsiSymbolId.property(typeId, field.name),
                name = field.name,
                type = field.type.toClientTypeRef(
                    annotations = emptyList(),
                    serviceId = rootServiceId,
                    defaultFetcherOwnerId = null,
                    sourceId = typeId,
                    sourceLanguage = LsiLanguage.UNKNOWN,
                    workspace = workspace,
                ).requireResolvedDefinitionType(rootServiceId, typeId),
                doc = field.documentation,
            )
        }
        val superTypes = generatedError.superTypeId?.let { superTypeId ->
            listOf(
                ClientDeclaredTypeRef(
                    typeId = superTypeId,
                    typeName = workspace.clientTypeName(superTypeId),
                )
            )
        }.orEmpty()
        return ClientTypeDefinition(
            id = typeId,
            typeName = workspace.clientTypeName(typeId),
            kind = ClientDefinitionKind.OBJECT,
            apiIgnore = false,
            doc = generatedError.documentation,
            error = generatedError.code?.let { code ->
                ClientDefinitionError(generatedError.family, code)
            },
            properties = properties,
            superTypes = superTypes,
            polymorphicBranches = emptyList(),
            enumConstants = emptyList(),
        )
    }

    private fun compileDefinition(
        workspace: LsiWorkspace,
        rootServiceId: LsiSymbolId,
        type: LsiTypeDeclaration,
        immutableSchema: ImmutableSchema,
        definitionDocumentation: ClientDefinitionDocumentation?,
        exceptionMetadata: ClientExceptionMetadata?,
    ): ClientTypeDefinition {
        val immutableType = immutableSchema.typesById[type.id]
        if (type.kind == LsiTypeDeclarationKind.ENUM) {
            return ClientTypeDefinition(
                id = type.id,
                typeName = workspace.clientTypeName(type.id),
                kind = ClientDefinitionKind.ENUM,
                apiIgnore = type.annotations.hasAnnotation(API_IGNORE_ANNOTATION),
                doc = type.clientDoc() ?: definitionDocumentation?.type.normalizedClientDoc(),
                error = null,
                properties = emptyList(),
                superTypes = emptyList(),
                polymorphicBranches = emptyList(),
                enumConstants = type.enumEntries.map { entry ->
                    ClientEnumConstant(
                        id = entry.id,
                        name = entry.name,
                        doc = entry.clientDoc(),
                    )
                },
            )
        }

        val immutable = immutableType != null
        val properties = if (!immutable || type.kind == LsiTypeDeclarationKind.INTERFACE) {
            compileDefinitionProperties(
                workspace = workspace,
                rootServiceId = rootServiceId,
                type = type,
                immutable = immutable,
                immutableProps = immutableType?.props.orEmpty(),
                clientException = type.annotations.hasAnnotation(CLIENT_EXCEPTION_ANNOTATION),
                defaultFetcherOwnerId = type.defaultFetcherOwnerId(),
                definitionDocumentation = definitionDocumentation,
            )
        } else {
            emptyList()
        }
        val superTypes = type.superTypes
            .filterIsInstance<LsiDeclaredType>()
            .filter { superType ->
                val superDeclaration = workspace[superType.declarationId] as? LsiTypeDeclaration
                superDeclaration?.annotations?.hasAnnotation(API_IGNORE_ANNOTATION) != true &&
                    workspace.clientTypeName(superType.declarationId).isDefinitionRequired() &&
                    superType.declarationId !in CLIENT_EXCEPTION_BASE_TYPE_IDS
            }
            .map { superType ->
                superType.toClientTypeRef(
                    annotations = emptyList(),
                    serviceId = type.id,
                    defaultFetcherOwnerId = type.defaultFetcherOwnerId(),
                    sourceId = type.id,
                    sourceLanguage = type.origin.language,
                    workspace = workspace,
                ).requireResolvedDefinitionType(rootServiceId, type.id)
            }
        val polymorphicBranches = if (type.kind == LsiTypeDeclarationKind.INTERFACE) {
            val branchesByOrder = workspace.declarationsOfType<LsiTypeDeclaration>()
                .asSequence()
                .filter { candidate -> candidate.enclosingTypeId == type.id }
                .filter { candidate -> candidate.kind == LsiTypeDeclarationKind.CLASS }
                .mapNotNull { candidate ->
                    val order = candidate.polymorphicBranchOrder(type.id) ?: return@mapNotNull null
                    order to ClientDeclaredTypeRef(
                        typeId = candidate.id,
                        typeName = workspace.clientTypeName(candidate.id),
                    )
                }
                .toList()
            val duplicateOrder = branchesByOrder
                .groupingBy { (order) -> order }
                .eachCount()
                .entries
                .firstOrNull { (_, count) -> count > 1 }
                ?.key
            if (duplicateOrder != null) {
                throw ClientValidationException(
                    declarationId = type.id,
                    rootTypeId = rootServiceId,
                    message = "Client polymorphic definition '${type.id.value}' has duplicate branch order $duplicateOrder",
                )
            }
            branchesByOrder
                .sortedBy { (order) -> order }
                .map { (_, branch) -> branch }
        } else {
            emptyList()
        }
        return ClientTypeDefinition(
            id = type.id,
            typeName = workspace.clientTypeName(type.id),
            kind = if (immutable) ClientDefinitionKind.IMMUTABLE else ClientDefinitionKind.OBJECT,
            apiIgnore = type.annotations.hasAnnotation(API_IGNORE_ANNOTATION),
            doc = type.clientDoc() ?: definitionDocumentation?.type.normalizedClientDoc(),
            error = exceptionMetadata?.code?.let { code ->
                ClientDefinitionError(exceptionMetadata.family, code)
            },
            properties = properties,
            superTypes = superTypes,
            polymorphicBranches = polymorphicBranches,
            enumConstants = emptyList(),
        )
    }

    private fun compileDefinitionProperties(
        workspace: LsiWorkspace,
        rootServiceId: LsiSymbolId,
        type: LsiTypeDeclaration,
        immutable: Boolean,
        immutableProps: List<ImmutableProp>,
        clientException: Boolean,
        defaultFetcherOwnerId: LsiSymbolId?,
        definitionDocumentation: ClientDefinitionDocumentation?,
    ): List<ClientDefinitionProperty> {
        val immutablePropsByDeclarationId = immutableProps.associateBy(ImmutableProp::declarationId)
        val fieldsByName = type.memberIds
            .mapNotNull(workspace::get)
            .filterIsInstance<LsiField>()
            .associateBy(LsiField::name)
        val propertiesByName = linkedMapOf<String, ClientDefinitionProperty>()
        type.memberIds.mapNotNull(workspace::get).forEach { member ->
            val property = when (member) {
                is LsiProperty -> {
                    val field = fieldsByName[member.name]
                    if (
                        !member.isClientDefinitionProperty(type.kind, immutable, clientException) ||
                        field?.annotations?.hasAnyAnnotation(JSON_IGNORE_ANNOTATIONS) == true
                    ) {
                        return@forEach
                    }
                    val immutableProp = immutablePropsByDeclarationId[member.id]
                    val renderedType = immutableProp?.converter?.targetType ?: member.type
                    ClientDefinitionProperty(
                        id = member.id,
                        name = member.name,
                        type = renderedType.toClientTypeRef(
                            annotations = member.annotations,
                            serviceId = type.id,
                            defaultFetcherOwnerId = defaultFetcherOwnerId,
                            sourceId = member.id,
                            sourceLanguage = member.origin.language,
                            workspace = workspace,
                        ).requireResolvedDefinitionType(rootServiceId, member.id),
                        doc = member.clientDoc()
                            ?: field?.clientDoc()
                            ?: definitionDocumentation?.properties?.get(member.name).normalizedClientDoc(),
                    )
                }
                is LsiFunction -> {
                    val propertyName = member.clientDefinitionPropertyName(clientException)
                        ?: return@forEach
                    val field = fieldsByName[propertyName]
                    if (field?.annotations?.hasAnyAnnotation(JSON_IGNORE_ANNOTATIONS) == true) {
                        return@forEach
                    }
                    ClientDefinitionProperty(
                        id = member.id,
                        name = propertyName,
                        type = member.returnType.toClientTypeRef(
                            annotations = member.annotations,
                            serviceId = type.id,
                            defaultFetcherOwnerId = defaultFetcherOwnerId,
                            sourceId = member.id,
                            sourceLanguage = member.origin.language,
                            workspace = workspace,
                        ).requireResolvedDefinitionType(rootServiceId, member.id),
                        doc = member.clientDoc()
                            ?: field?.clientDoc()
                            ?: definitionDocumentation?.properties?.get(propertyName).normalizedClientDoc(),
                    )
                }
                else -> null
            }
            if (property != null) {
                propertiesByName.putIfAbsent(property.name, property)
            }
        }
        return propertiesByName.values.toList()
    }

    private fun validateService(
        service: LsiTypeDeclaration,
        allTypes: List<LsiTypeDeclaration>,
    ) {
        val enclosingType = service.enclosingType(allTypes)
        if (enclosingType != null) {
            throw ClientValidationException(
                declarationId = service.id,
                message = "Client API service '${service.qualifiedName}' must be top-level",
            )
        }
        if (service.typeParameters.isNotEmpty()) {
            throw ClientValidationException(
                declarationId = service.id,
                message = "Client API service '${service.qualifiedName}' cannot declare type parameters",
            )
        }
    }

    private fun validateOperation(declaration: LsiDeclaration) {
        if (declaration.visibility != LsiVisibility.PUBLIC) {
            throw ClientValidationException(
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
            throw ClientValidationException(
                declarationId = declaration.id,
                message = "Client API operation '${declaration.id.value}' cannot be static",
            )
        }
        val typeParameters = (declaration as? LsiFunction)?.typeParameters.orEmpty()
        if (typeParameters.isNotEmpty()) {
            throw ClientValidationException(
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
        throw ClientValidationException(
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

    private fun LsiTypeRef.toClientTypeRef(
        annotations: List<LsiAnnotation>,
        serviceId: LsiSymbolId,
        defaultFetcherOwnerId: LsiSymbolId?,
        sourceId: LsiSymbolId,
        sourceLanguage: LsiLanguage,
        workspace: LsiWorkspace,
        jsonValueTypeIds: Set<LsiSymbolId> = emptySet(),
        nestedType: Boolean = false,
    ): ClientTypeRef {
        val effectiveAnnotations = (this.annotations + annotations).distinctBy(LsiAnnotation::type)
        val fetchByAnnotation = effectiveAnnotations.annotation(FETCH_BY_ANNOTATION)
        val fetchByNullable = fetchByAnnotation?.booleanValue("nullable") == true
        val javaFetchByNullable = when {
            !fetchByNullable -> false
            sourceLanguage == LsiLanguage.JAVA -> true
            sourceLanguage == LsiLanguage.KOTLIN -> false
            else -> throw ClientValidationException(
                declarationId = sourceId,
                message = "FetchBy nullable on '${sourceId.value}' requires a known source language",
            )
        }
        val nullableAnnotations = effectiveAnnotations.filter(LsiAnnotation::isClientNullableAnnotation)
        val nonNullAnnotations = effectiveAnnotations.filter(LsiAnnotation::isClientNonNullAnnotation)
        if ((nullableAnnotations.isNotEmpty() || javaFetchByNullable) && nonNullAnnotations.isNotEmpty()) {
            val nullableAnnotationText = nullableAnnotations.firstOrNull()?.let { annotation ->
                "'@${annotation.type.value}'"
            } ?: "'@FetchBy(nullable = true)'"
            throw ClientValidationException(
                declarationId = sourceId,
                message = "Client type '${sourceId.value}' has conflicting nullability annotations " +
                    "$nullableAnnotationText and '@${nonNullAnnotations.first().type.value}'",
            )
        }
        val primitiveType = this as? LsiPrimitiveType
        if (nullableAnnotations.isNotEmpty() && primitiveType?.boxed == false) {
            throw ClientValidationException(
                declarationId = sourceId,
                message = "Nullable annotation '@${nullableAnnotations.first().type.value}' cannot decorate " +
                    "primitive type '${primitiveType.kind.name.lowercase()}'",
            )
        }
        if (nonNullAnnotations.isNotEmpty() && primitiveType?.boxed == true) {
            throw ClientValidationException(
                declarationId = sourceId,
                message = "Non-null annotation '@${nonNullAnnotations.first().type.value}' cannot decorate " +
                    "boxed primitive type '${primitiveType.kind.name.lowercase()}'; use the unboxed primitive type",
            )
        }
        val fetchBy = fetchByAnnotation?.toClientFetchBy(
            decoratedType = this,
            serviceId = serviceId,
            defaultFetcherOwnerId = defaultFetcherOwnerId,
            sourceId = sourceId,
            workspace = workspace,
        )
        val nullable = nullability == LsiNullability.NULLABLE ||
            !nestedType && primitiveType?.boxed == true ||
            nullableAnnotations.isNotEmpty() ||
            javaFetchByNullable
        if (this is LsiDeclaredType) {
            workspace.jsonValueType(declarationId)?.let { jsonValueType ->
                if (declarationId in jsonValueTypeIds) {
                    throw ClientValidationException(
                        declarationId = sourceId,
                        message = "Cannot resolve client @JsonValue type because of recursion: " +
                            (jsonValueTypeIds + declarationId).joinToString { typeId -> typeId.value },
                    )
                }
                return jsonValueType.toClientTypeRef(
                    annotations = emptyList(),
                    serviceId = serviceId,
                    defaultFetcherOwnerId = defaultFetcherOwnerId,
                    sourceId = sourceId,
                    sourceLanguage = sourceLanguage,
                    workspace = workspace,
                    jsonValueTypeIds = jsonValueTypeIds + declarationId,
                    nestedType = true,
                ).withAdditionalNullability(nullable)
            }
            val typeName = workspace.clientTypeName(declarationId)
            if (typeName.simpleNames.last().lowercase() in JSON_NODE_SIMPLE_NAMES) {
                return ClientDeclaredTypeRef(
                    typeId = OBJECT_TYPE_ID,
                    typeName = OBJECT_TYPE_NAME,
                    nullable = nullable,
                    fetchBy = fetchBy,
                )
            }
            if (typeName.toMetaQualifiedName() == OBJECT_TYPE_NAME.toMetaQualifiedName()) {
                return ClientDeclaredTypeRef(
                    typeId = OBJECT_TYPE_ID,
                    typeName = OBJECT_TYPE_NAME,
                    nullable = nullable,
                    fetchBy = fetchBy,
                )
            }
            val declaration = workspace[declarationId] as? LsiTypeDeclaration
            if (declaration?.requiresEnclosingInstance == true) {
                throw ClientValidationException(
                    declarationId = sourceId,
                    message = "Client API only accepts top-level or static nested types: '${typeName.qualifiedName}'",
                )
            }
            if (declaration?.typeParameters?.isNotEmpty() == true && arguments.isEmpty()) {
                throw ClientValidationException(
                    declarationId = sourceId,
                    message = "Client API type system does not accept raw generic type '${typeName.qualifiedName}'",
                )
            }
        }
        return when (this) {
            is LsiDeclaredType -> {
                val clientTypeId = CLIENT_CANONICAL_TYPE_IDS[declarationId] ?: declarationId
                ClientDeclaredTypeRef(
                    typeId = clientTypeId,
                    typeName = workspace.clientTypeName(clientTypeId),
                    arguments = arguments.map { argument ->
                        argument.toClientTypeArgument(
                            serviceId = serviceId,
                            defaultFetcherOwnerId = defaultFetcherOwnerId,
                            sourceId = sourceId,
                            sourceLanguage = sourceLanguage,
                            workspace = workspace,
                            jsonValueTypeIds = jsonValueTypeIds,
                            nestedType = true,
                        )
                    },
                    nullable = nullable,
                    fetchBy = fetchBy,
                )
            }
            is LsiPrimitiveType -> ClientPrimitiveTypeRef(kind, nullable, fetchBy)
            is LsiArrayType -> ClientArrayTypeRef(
                elementType = elementType.toClientTypeRef(
                    annotations = emptyList(),
                    serviceId = serviceId,
                    defaultFetcherOwnerId = defaultFetcherOwnerId,
                    sourceId = sourceId,
                    sourceLanguage = sourceLanguage,
                    workspace = workspace,
                    jsonValueTypeIds = jsonValueTypeIds,
                    nestedType = true,
                ),
                nullable = nullable,
                fetchBy = fetchBy,
            )
            is LsiFunctionType -> throw ClientValidationException(
                declarationId = sourceId,
                message = "Client API type system does not support function type '${stableSignature()}'",
            )
            is LsiTypeParameterRef -> {
                val owner = workspace.typeParameterOwner(parameterId)
                    ?: throw ClientValidationException(
                        declarationId = sourceId,
                        recoverable = true,
                        message = "Client type parameter '${parameterId.value}' has no declaring type",
                    )
                val parameter = owner.typeParameters.first { parameter -> parameter.id == parameterId }
                ClientTypeParameterRef(
                    parameterId = parameterId,
                    ownerTypeName = workspace.clientTypeName(owner.id),
                    name = parameter.name,
                    nullable = nullable,
                    fetchBy = fetchBy,
                )
            }
            is LsiUnresolvedType -> ClientUnresolvedTypeRef(displayName, nullable, fetchBy)
        }
    }

    private fun LsiTypeArgument.toClientTypeArgument(
        serviceId: LsiSymbolId,
        defaultFetcherOwnerId: LsiSymbolId?,
        sourceId: LsiSymbolId,
        sourceLanguage: LsiLanguage,
        workspace: LsiWorkspace,
        jsonValueTypeIds: Set<LsiSymbolId> = emptySet(),
        nestedType: Boolean = true,
    ): ClientTypeArgument {
        if (variance == LsiVariance.STAR || variance == LsiVariance.IN) {
            throw ClientValidationException(
                declarationId = sourceId,
                message = "Client API type system does not accept ${variance.name.lowercase()} type arguments",
            )
        }
        return ClientTypeArgument(
            variance = variance,
            type = type?.toClientTypeRef(
                annotations = emptyList(),
                serviceId = serviceId,
                defaultFetcherOwnerId = defaultFetcherOwnerId,
                sourceId = sourceId,
                sourceLanguage = sourceLanguage,
                workspace = workspace,
                jsonValueTypeIds = jsonValueTypeIds,
                nestedType = nestedType,
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
            ?: throw ClientValidationException(
                declarationId = sourceId,
                message = "FetchBy on '${sourceId.value}' requires a non-blank value",
            )
        val explicitOwnerId = classTypeId("ownerType")?.takeUnless(LsiSymbolId::isVoidType)
        val targetEntityTypeId = decoratedType.fetchTargetEntityTypeId(workspace, sourceId)
        val ownerTypeId = explicitOwnerId ?: defaultFetcherOwnerId ?: serviceId
        val fetcherMember = workspace.fetcherMember(ownerTypeId, value)
            ?: throw ClientValidationException(
                declarationId = sourceId,
                recoverable = workspace[ownerTypeId] == null,
                message = "FetchBy on '${sourceId.value}' cannot find fetcher '$value' in " +
                    "'${ownerTypeId.requireTypeQualifiedName()}'",
            )
        val fetcherType = when (fetcherMember) {
            is LsiField -> fetcherMember.type
            is LsiProperty -> fetcherMember.type
            else -> error("Client fetcher member must be a field or property")
        }
        val declaredFetcherType = fetcherType as? LsiDeclaredType
        val typeSystem = LsiTypeSystem(workspace)
        val resolvedFetcherType = declaredFetcherType?.let { type ->
            if (type.declarationId == FETCHER_TYPE_ID) {
                type
            } else {
                typeSystem.resolveSuperType(type.declarationId, FETCHER_TYPE_ID)
            }
        }
        val fetchedTypeId = resolvedFetcherType
            ?.arguments
            ?.singleOrNull()
            ?.takeIf { argument -> argument.variance == LsiVariance.INVARIANT }
            ?.type
            ?.declaredTypeId()
        if (fetchedTypeId != targetEntityTypeId) {
            throw ClientValidationException(
                declarationId = sourceId,
                message = "FetchBy '$value' in '${ownerTypeId.requireTypeQualifiedName()}' must have type " +
                    "'${FETCHER_TYPE_ID.requireTypeQualifiedName()}<${targetEntityTypeId.requireTypeQualifiedName()}>'",
            )
        }
        return ClientFetchBy(
            value = value,
            ownerTypeId = ownerTypeId,
            ownerTypeName = workspace.clientTypeName(ownerTypeId),
            targetEntityTypeId = targetEntityTypeId,
            documentation = fetcherMember.clientDoc(),
        )
    }

    private fun LsiTypeRef.fetchTargetEntityTypeId(
        workspace: LsiWorkspace,
        sourceId: LsiSymbolId,
    ): LsiSymbolId {
        val typeId = (this as? LsiDeclaredType)?.declarationId
        val type = typeId?.let { id -> workspace[id] as? LsiTypeDeclaration }
        if (type == null || !type.annotations.hasAnnotation(ENTITY_ANNOTATION)) {
            throw ClientValidationException(
                declarationId = sourceId,
                recoverable = typeId != null && workspace[typeId] == null,
                message = "FetchBy on '${sourceId.value}' can only decorate an entity type",
            )
        }
        return type.id
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

private data class GeneratedClientErrorType(
    val typeId: LsiSymbolId,
    val family: String,
    val code: String?,
    val superTypeId: LsiSymbolId?,
    val documentation: String?,
    val fields: List<ErrorField>,
)

private val EMPTY_CLIENT_SCHEMA_DEPENDENCIES = ClientSchemaDependencies(
    immutableSchema = ImmutableSchema(emptyList()),
    errorSchema = ErrorSchema(emptyList()),
    definitionDocumentationByTypeId = emptyMap(),
)

private fun ErrorSchema.generatedErrorType(typeId: LsiSymbolId): GeneratedClientErrorType? {
    families.forEach { family ->
        if (family.exceptionTypeId == typeId) {
            return GeneratedClientErrorType(
                typeId = typeId,
                family = family.family,
                code = null,
                superTypeId = null,
                documentation = family.documentation,
                fields = family.declaredFields,
            )
        }
        family.codes.firstOrNull { code -> code.exceptionTypeId == typeId }?.let { code ->
            return GeneratedClientErrorType(
                typeId = typeId,
                family = family.family,
                code = code.code,
                superTypeId = family.exceptionTypeId,
                documentation = code.documentation,
                fields = family.declaredFields + code.declaredFields,
            )
        }
    }
    return null
}

private fun ClientTypeRef.collectDefinitionTypeIds(target: MutableCollection<LsiSymbolId>) {
    when (this) {
        is ClientDeclaredTypeRef -> {
            target += typeId
            arguments.mapNotNull(ClientTypeArgument::type).forEach { argumentType ->
                argumentType.collectDefinitionTypeIds(target)
            }
        }
        is ClientArrayTypeRef -> elementType.collectDefinitionTypeIds(target)
        is ClientPrimitiveTypeRef,
        is ClientTypeParameterRef,
        is ClientUnresolvedTypeRef,
        -> Unit
    }
}

private fun ClientTypeRef.requireResolvedDefinitionType(
    rootServiceId: LsiSymbolId,
    declarationId: LsiSymbolId,
): ClientTypeRef {
    val unresolved = when (this) {
        is ClientUnresolvedTypeRef -> true
        is ClientDeclaredTypeRef -> arguments
            .mapNotNull(ClientTypeArgument::type)
            .any { argumentType -> argumentType.hasUnresolvedType() }
        is ClientArrayTypeRef -> elementType.hasUnresolvedType()
        is ClientPrimitiveTypeRef,
        is ClientTypeParameterRef,
        -> false
    }
    if (unresolved) {
        throw ClientValidationException(
            declarationId = declarationId,
            rootTypeId = rootServiceId,
            recoverable = true,
            message = "Client definition member '${declarationId.value}' has an unresolved type",
        )
    }
    return this
}

private fun ClientTypeRef.hasUnresolvedType(): Boolean {
    return when (this) {
        is ClientUnresolvedTypeRef -> true
        is ClientDeclaredTypeRef -> arguments
            .mapNotNull(ClientTypeArgument::type)
            .any { argumentType -> argumentType.hasUnresolvedType() }
        is ClientArrayTypeRef -> elementType.hasUnresolvedType()
        is ClientPrimitiveTypeRef,
        is ClientTypeParameterRef,
        -> false
    }
}

private fun LsiProperty.isClientDefinitionProperty(
    ownerKind: LsiTypeDeclarationKind,
    immutable: Boolean,
    clientException: Boolean,
): Boolean {
    if (
        visibility != LsiVisibility.PUBLIC ||
        static ||
        annotations.hasAnnotation(API_IGNORE_ANNOTATION) ||
        annotations.hasAnyAnnotation(JSON_IGNORE_ANNOTATIONS)
    ) {
        return false
    }
    if (name == "toString" || name == "hashCode" || getterName == "toString" || getterName == "hashCode") {
        return false
    }
    if (
        origin.language == LsiLanguage.JAVA &&
        !immutable &&
        ownerKind != LsiTypeDeclarationKind.RECORD &&
        !isJavaBeanGetter()
    ) {
        return false
    }
    return !clientException || name != "code" && name != "fields"
}

private fun LsiProperty.isJavaBeanGetter(): Boolean {
    return getterName.isJavaBeanGetterName("get") ||
        type.isBooleanLike() && getterName.isJavaBeanGetterName("is")
}

private fun LsiFunction.clientDefinitionPropertyName(clientException: Boolean): String? {
    if (
        visibility != LsiVisibility.PUBLIC ||
        static ||
        parameters.isNotEmpty() ||
        returnType.isVoidLike() ||
        annotations.hasAnnotation(API_IGNORE_ANNOTATION) ||
        annotations.hasAnyAnnotation(JSON_IGNORE_ANNOTATIONS)
    ) {
        return null
    }
    val propertyName = when {
        returnType.isBooleanLike() && name.isJavaBeanGetterName("is") -> {
            name.substring(2).clientDecapitalize()
        }
        name.isJavaBeanGetterName("get") -> {
            name.substring(3).clientDecapitalize()
        }
        else -> null
    } ?: return null
    if (propertyName == "toString" || propertyName == "hashCode") {
        return null
    }
    if (clientException && (propertyName == "code" || propertyName == "fields")) {
        return null
    }
    return propertyName
}

private fun String.isJavaBeanGetterName(prefix: String): Boolean {
    return startsWith(prefix) && length > prefix.length && !this[prefix.length].isLowerCase()
}

private fun String.clientDecapitalize(): String {
    if (isEmpty() || first().isLowerCase()) {
        return this
    }
    val characters = toCharArray()
    for (index in characters.indices) {
        if (characters[index].isLowerCase()) {
            break
        }
        characters[index] = characters[index].lowercaseChar()
    }
    return characters.concatToString()
}

private fun LsiTypeRef.isBooleanLike(): Boolean {
    return this is LsiPrimitiveType && kind == LsiPrimitiveKind.BOOLEAN ||
        this is LsiDeclaredType && declarationId.requireTypeQualifiedName() in BOOLEAN_TYPE_NAMES
}

private fun LsiWorkspace.jsonValueType(typeId: LsiSymbolId): LsiTypeRef? {
    val type = this[typeId] as? LsiTypeDeclaration ?: return null
    return type.memberIds
        .asSequence()
        .mapNotNull(::get)
        .filter { member -> member.annotations.hasAnyAnnotation(JSON_VALUE_ANNOTATIONS) }
        .mapNotNull { member ->
            when (member) {
                is LsiFunction -> member.returnType.takeIf {
                    !member.static && member.parameters.isEmpty() && !it.isVoidLike()
                }
                is LsiProperty -> member.type.takeIf { !member.static }
                else -> null
            }
        }
        .firstOrNull()
}

private fun ClientTypeRef.withAdditionalNullability(nullable: Boolean): ClientTypeRef {
    if (!nullable || this.nullable) {
        return this
    }
    return when (this) {
        is ClientDeclaredTypeRef -> copy(nullable = true)
        is ClientPrimitiveTypeRef -> copy(nullable = true)
        is ClientArrayTypeRef -> copy(nullable = true)
        is ClientTypeParameterRef -> copy(nullable = true)
        is ClientUnresolvedTypeRef -> copy(nullable = true)
    }
}

private fun LsiTypeDeclaration.polymorphicBranchOrder(ownerTypeId: LsiSymbolId): Int? {
    val annotation = annotations.annotation(GENERATED_POLYMORPHIC_BRANCH_ANNOTATION) ?: return null
    val value = annotation.arguments["value"]?.value as? LsiAnnotationValue.ClassValue
        ?: throw ClientValidationException(
            declarationId = id,
            message = "Client polymorphic branch '${id.value}' has no owner type",
        )
    if (value.type.declaredTypeId() != ownerTypeId) {
        return null
    }
    val order = (annotation.arguments["order"]?.value as? LsiAnnotationValue.IntValue)?.value
        ?: throw ClientValidationException(
            declarationId = id,
            message = "Client polymorphic branch '${id.value}' has no order",
        )
    if (order < 0) {
        throw ClientValidationException(
            declarationId = id,
            message = "Client polymorphic branch '${id.value}' has negative order $order",
        )
    }
    return order
}

private fun LsiTypeDeclaration.isPolymorphicBranchOf(ownerTypeId: LsiSymbolId): Boolean {
    val annotation = annotations.annotation(GENERATED_POLYMORPHIC_BRANCH_ANNOTATION) ?: return false
    val value = annotation.arguments["value"]?.value as? LsiAnnotationValue.ClassValue ?: return false
    return value.type.declaredTypeId() == ownerTypeId
}

private fun LsiWorkspace.clientTypeName(typeId: LsiSymbolId): ClientTypeName {
    val declaration = this[typeId] as? LsiTypeDeclaration
    if (declaration == null) {
        val qualifiedName = typeId.requireTypeQualifiedName().replace('$', '.')
        return ClientTypeName.parse(qualifiedName)
    }
    val simpleNames = ArrayDeque<String>()
    var current: LsiTypeDeclaration = declaration
    simpleNames.addFirst(current.name)
    while (true) {
        val enclosingTypeId = current.enclosingTypeId ?: break
        current = this[enclosingTypeId] as? LsiTypeDeclaration ?: break
        simpleNames.addFirst(current.name)
    }
    val packageName = current.qualifiedName
        .removeSuffix(".${current.name}")
        .takeIf { value -> value != current.qualifiedName && value.isNotEmpty() }
    return ClientTypeName(packageName, simpleNames.toList())
}

private fun LsiWorkspace.typeParameterOwner(parameterId: LsiSymbolId): LsiTypeDeclaration? {
    return declarationsOfType<LsiTypeDeclaration>().firstOrNull { type ->
        type.typeParameters.any { parameter -> parameter.id == parameterId }
    }
}

private fun ClientTypeName.toMetaQualifiedName(): String {
    return when (qualifiedName) {
        "kotlin.Any" -> "java.lang.Object"
        else -> qualifiedName
    }
}

private fun LsiWorkspace.fetcherMember(
    ownerTypeId: LsiSymbolId,
    name: String,
): LsiDeclaration? {
    val owner = this[ownerTypeId] as? LsiTypeDeclaration ?: return null
    fun LsiTypeDeclaration.member(allowInstanceMember: Boolean): LsiDeclaration? {
        return memberIds
            .mapNotNull(this@fetcherMember::get)
            .firstOrNull { member ->
                member.name == name &&
                    when (member) {
                        is LsiField -> allowInstanceMember || member.static
                        is LsiProperty -> allowInstanceMember || member.static
                        else -> false
                    }
            }
    }
    owner.member(allowInstanceMember = owner.kind == LsiTypeDeclarationKind.OBJECT)?.let { member ->
        return member
    }
    return declarationsOfType<LsiTypeDeclaration>()
        .asSequence()
        .filter { type ->
            type.enclosingTypeId == owner.id &&
                type.kind == LsiTypeDeclarationKind.OBJECT &&
                type.name == KOTLIN_COMPANION_NAME
        }
        .mapNotNull { type -> type.member(allowInstanceMember = true) }
        .firstOrNull()
}

private fun ClientTypeName.isDefinitionRequired(): Boolean {
    if (qualifiedName.startsWith("<")) {
        return false
    }
    return qualifiedName !in NON_DEFINITION_TYPE_NAMES
}

private fun LsiDeclaration.clientDoc(): String? {
    val source = documentation
        ?: annotations.annotation(DESCRIPTION_ANNOTATION)?.stringValue("value")
        ?: return null
    return source.normalizeDoc().takeIf(String::isNotBlank)
}

private fun String?.normalizedClientDoc(): String? {
    return this?.normalizeDoc()?.takeIf(String::isNotBlank)
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
        is LsiFunctionType -> receiverType?.hasUnresolvedClientType() == true ||
            parameterTypes.any(LsiTypeRef::hasUnresolvedClientType) ||
            returnType.hasUnresolvedClientType()
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
}

private fun List<LsiAnnotation>.annotation(type: LsiSymbolId): LsiAnnotation? {
    return firstOrNull { annotation -> annotation.type == type }
}

private fun List<LsiAnnotation>.hasAnnotation(type: LsiSymbolId): Boolean {
    return any { annotation -> annotation.type == type }
}

private fun List<LsiAnnotation>.hasAnyAnnotation(types: Set<LsiSymbolId>): Boolean {
    return any { annotation -> annotation.type in types }
}

private fun LsiAnnotation.isClientNullableAnnotation(): Boolean {
    val qualifiedName = type.requireTypeQualifiedName()
    val simpleName = qualifiedName.substringAfterLast('.')
    return qualifiedName == "org.babyfish.jimmer.client.TNullable" ||
        simpleName == "Null" ||
        simpleName == "Nullable"
}

private fun LsiAnnotation.isClientNonNullAnnotation(): Boolean {
    val qualifiedName = type.requireTypeQualifiedName()
    if (qualifiedName in VALIDATION_NOT_NULL_ANNOTATIONS) {
        return false
    }
    val simpleName = qualifiedName.substringAfterLast('.')
    return simpleName == "NotNull" || simpleName == "NonNull"
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
private val CLIENT_EXCEPTION_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.ClientException")
private val DESCRIPTION_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.client.Description")
private val FETCH_BY_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.client.FetchBy")
private val DEFAULT_FETCHER_OWNER_ANNOTATION =
    LsiSymbolId.type("org.babyfish.jimmer.client.meta.DefaultFetcherOwner")
private val ENTITY_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.Entity")
private val FETCHER_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.fetcher.Fetcher")
private val REST_CONTROLLER_ANNOTATION =
    LsiSymbolId.type("org.springframework.web.bind.annotation.RestController")
private val GENERATED_POLYMORPHIC_BRANCH_ANNOTATION =
    LsiSymbolId.type("org.babyfish.jimmer.internal.GeneratedPolymorphicDtoBranch")

private val JSON_IGNORE_ANNOTATIONS = setOf(
    LsiSymbolId.type("com.fasterxml.jackson.annotation.JsonIgnore"),
)

private val JSON_VALUE_ANNOTATIONS = setOf(
    LsiSymbolId.type("com.fasterxml.jackson.annotation.JsonValue"),
)

private val IMMUTABLE_TYPE_ANNOTATIONS = setOf(
    LsiSymbolId.type("org.babyfish.jimmer.Immutable"),
    ENTITY_ANNOTATION,
    LsiSymbolId.type("org.babyfish.jimmer.sql.MappedSuperclass"),
    LsiSymbolId.type("org.babyfish.jimmer.sql.Embeddable"),
)

private val OBJECT_TYPE_ID = LsiSymbolId.type("java.lang.Object")
private val OBJECT_TYPE_NAME = ClientTypeName("java.lang", listOf("Object"))
private val CLIENT_CANONICAL_TYPE_IDS = mapOf(
    LsiSymbolId.type("kotlin.collections.MutableList") to LsiSymbolId.type("java.util.List"),
)
private val JSON_NODE_SIMPLE_NAMES = setOf(
    "jsonnode",
    "jsonobject",
    "jsonelement",
    "objectnode",
    "arraynode",
)

private const val KOTLIN_COMPANION_NAME = "Companion"

private val CLIENT_EXCEPTION_BASE_TYPE_IDS = setOf(
    LsiSymbolId.type("org.babyfish.jimmer.error.CodeBasedException"),
    LsiSymbolId.type("org.babyfish.jimmer.error.CodeBasedRuntimeException"),
)

private val SPRING_MAPPING_ANNOTATIONS = listOf(
    "org.springframework.web.bind.annotation.RequestMapping",
    "org.springframework.web.bind.annotation.GetMapping",
    "org.springframework.web.bind.annotation.PostMapping",
    "org.springframework.web.bind.annotation.PutMapping",
    "org.springframework.web.bind.annotation.DeleteMapping",
    "org.springframework.web.bind.annotation.PatchMapping",
).map(LsiSymbolId::type)

private val VALIDATION_NOT_NULL_ANNOTATIONS = setOf(
    "jakarta.validation.constraints.NotNull",
    "javax.validation.constraints.NotNull",
)

private val VOID_TYPE_NAMES = setOf(
    "java.lang.Void",
    "kotlin.Nothing",
    "kotlin.Unit",
)

private val BOOLEAN_TYPE_NAMES = setOf(
    "java.lang.Boolean",
    "kotlin.Boolean",
)

private val NON_DEFINITION_TYPE_NAMES = setOf(
    "boolean",
    "char",
    "byte",
    "short",
    "int",
    "long",
    "float",
    "double",
    "java.lang.Boolean",
    "java.lang.Character",
    "java.lang.Byte",
    "java.lang.Short",
    "java.lang.Integer",
    "java.lang.Long",
    "java.lang.Float",
    "java.lang.Double",
    "kotlin.Boolean",
    "kotlin.Char",
    "kotlin.Byte",
    "kotlin.Short",
    "kotlin.Int",
    "kotlin.Long",
    "kotlin.Float",
    "kotlin.Double",
    "java.lang.Object",
    "kotlin.Any",
    "java.io.Closeable",
    "java.lang.AutoCloseable",
    "java.lang.Enum",
    "java.lang.Class",
    "java.math.BigDecimal",
    "java.math.BigInteger",
    "java.lang.String",
    "kotlin.String",
    "java.util.UUID",
    "java.util.Date",
    "java.sql.Date",
    "java.sql.Time",
    "java.sql.Timestamp",
    "java.time.Instant",
    "java.time.LocalTime",
    "java.time.LocalDate",
    "java.time.LocalDateTime",
    "java.time.OffsetDateTime",
    "java.time.ZonedDateTime",
    "java.lang.Iterable",
    "java.util.Collection",
    "java.util.List",
    "java.util.Set",
    "java.util.SortedSet",
    "java.util.NavigableSet",
    "java.util.SequencedSet",
    "java.util.Map",
    "java.util.SortedMap",
    "java.util.NavigableMap",
    "java.util.SequencedMap",
    "java.util.Optional",
    "kotlin.collections.Iterable",
    "kotlin.collections.Collection",
    "kotlin.collections.List",
    "kotlin.collections.Set",
    "kotlin.collections.MutableIterable",
    "kotlin.collections.MutableCollection",
    "kotlin.collections.MutableList",
    "kotlin.collections.MutableSet",
    "kotlin.collections.Map",
    "kotlin.collections.MutableMap",
    "kotlin.Array",
)
