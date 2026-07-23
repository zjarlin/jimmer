package site.addzero.lsi.jimmer.transactional

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationTarget
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiAnnotationUseSiteTarget
import site.addzero.lsi.model.LsiConstructor
import site.addzero.lsi.model.LsiDeclaration
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiField
import site.addzero.lsi.model.LsiFunction
import site.addzero.lsi.model.LsiModality
import site.addzero.lsi.model.LsiParameter
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiTypeSystem
import site.addzero.lsi.model.LsiVisibility
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.annotationTargetPolicy

/** Transactional 语义校验失败。 */
class TransactionalValidationException(
    val declarationId: LsiSymbolId,
    message: String,
) : IllegalArgumentException(message)

/** 将当前工作区解析为 Transactional 共享语义模型。 */
fun LsiWorkspace.toTransactionalSchema(): TransactionalSchema {
    return TransactionalSchemaBuilder().build(this)
}

private class TransactionalSchemaBuilder {
    fun build(workspace: LsiWorkspace): TransactionalSchema {
        val types = workspace.declarationsOfType<LsiTypeDeclaration>()
            .sortedBy(LsiTypeDeclaration::qualifiedName)
        val typeSystem = LsiTypeSystem(workspace)
        val transactionalTypes = types
            .filter { type -> type.isTransactionalType(workspace) }
            .map { type -> compileType(type, types, workspace, typeSystem) }
        return TransactionalSchema(transactionalTypes)
    }

    private fun compileType(
        type: LsiTypeDeclaration,
        allTypes: List<LsiTypeDeclaration>,
        workspace: LsiWorkspace,
        typeSystem: LsiTypeSystem,
    ): TransactionalType {
        validateType(type, allTypes, workspace)
        val classTx = type.annotations.annotation(TX_ANNOTATION)
        val members = type.memberIds.mapNotNull(workspace::get)
        val sqlClient = determineSqlClient(type, members, typeSystem)
        val constructors = members.filterIsInstance<LsiConstructor>()
            .filter { constructor -> constructor.visibility != LsiVisibility.PRIVATE }
            .map { constructor ->
                constructor.toTransactionalConstructor(workspace, sqlClient.platform)
            }
        val methods = members.mapNotNull { member ->
            compileMethod(member, classTx, typeSystem, workspace, sqlClient.platform)
        }
        val packageName = type.qualifiedName
            .removeSuffix(".${type.name}")
            .takeUnless { value -> value == type.qualifiedName }
            .orEmpty()
        return TransactionalType(
            id = type.id,
            qualifiedName = type.qualifiedName,
            packageName = packageName,
            simpleName = type.name,
            generatedSimpleName = type.name + "Tx",
            visibility = type.visibility,
            modality = type.modality,
            copiedAnnotations = type.annotations.filterNot { annotation ->
                annotation.type == TX_ANNOTATION || annotation.type == TARGET_ANNOTATION
            },
            targetAnnotationTypeId = type.annotations.annotation(TARGET_ANNOTATION)?.classTypeId("value"),
            sqlClient = sqlClient,
            constructors = constructors,
            methods = methods,
        )
    }

    private fun validateType(
        type: LsiTypeDeclaration,
        allTypes: List<LsiTypeDeclaration>,
        workspace: LsiWorkspace,
    ) {
        if (type.kind != LsiTypeDeclarationKind.CLASS) {
            throw TransactionalValidationException(
                declarationId = type.id,
                message = "Type using '@${TX_ANNOTATION.value}' must be a class",
            )
        }
        val enclosingType = allTypes
            .asSequence()
            .filter { candidate -> candidate.id != type.id }
            .filter { candidate -> type.qualifiedName.startsWith(candidate.qualifiedName + ".") }
            .maxByOrNull { candidate -> candidate.qualifiedName.length }
        if (enclosingType != null) {
            throw TransactionalValidationException(
                declarationId = type.id,
                message = "Transactional class '${type.qualifiedName}' must be top-level",
            )
        }
        if (type.modality == LsiModality.FINAL || type.modality == LsiModality.SEALED) {
            throw TransactionalValidationException(
                declarationId = type.id,
                message = "Transactional class '${type.qualifiedName}' must be open or abstract",
            )
        }
        if (type.typeParameters.isNotEmpty()) {
            throw TransactionalValidationException(
                declarationId = type.id,
                message = "Transactional class '${type.qualifiedName}' cannot declare type parameters",
            )
        }
        type.superTypes.filterIsInstance<LsiDeclaredType>().forEach { superType ->
            if (superType.declarationId in ROOT_OBJECT_TYPE_IDS) {
                return@forEach
            }
            val hierarchyEntry = workspace.typeHierarchyEntry(superType.declarationId) ?: return@forEach
            if (hierarchyEntry.kind in CLASS_LIKE_TYPE_KINDS) {
                throw TransactionalValidationException(
                    declarationId = type.id,
                    message = "Transactional class '${type.qualifiedName}' cannot inherit class " +
                        "'${hierarchyEntry.qualifiedName}'",
                )
            }
        }
    }

    private fun determineSqlClient(
        type: LsiTypeDeclaration,
        members: List<LsiDeclaration>,
        typeSystem: LsiTypeSystem,
    ): TransactionalSqlClient {
        val platform = when (type.origin.source?.language) {
            LsiLanguage.JAVA -> TransactionalPlatform.JAVA
            LsiLanguage.KOTLIN -> TransactionalPlatform.KOTLIN
            else -> if (members.any { member -> member is LsiField }) {
                TransactionalPlatform.JAVA
            } else {
                TransactionalPlatform.KOTLIN
            }
        }
        val targetTypeId = when (platform) {
            TransactionalPlatform.JAVA -> J_SQL_CLIENT_TYPE
            TransactionalPlatform.KOTLIN -> K_SQL_CLIENT_TYPE
        }
        val candidates = when (platform) {
            TransactionalPlatform.JAVA -> members.filterIsInstance<LsiField>().map { field ->
                StorageMember(field.id, field.name, field.type, field.static, field.visibility)
            }
            TransactionalPlatform.KOTLIN -> members.filterIsInstance<LsiProperty>().map { property ->
                StorageMember(property.id, property.name, property.type, property.static, property.visibility)
            }
        }.filter { member ->
            !member.static && member.type.isSubtypeOf(targetTypeId, typeSystem)
        }
        if (candidates.isEmpty()) {
            throw TransactionalValidationException(
                declarationId = type.id,
                message = "Transactional class '${type.qualifiedName}' must declare exactly one non-static " +
                    "${targetTypeId.value} member",
            )
        }
        if (candidates.size > 1) {
            throw TransactionalValidationException(
                declarationId = type.id,
                message = "Transactional class '${type.qualifiedName}' declares multiple non-static " +
                    "${targetTypeId.value} members",
            )
        }
        val candidate = candidates.single()
        if (candidate.visibility == LsiVisibility.PRIVATE) {
            throw TransactionalValidationException(
                declarationId = candidate.id,
                message = "Transactional sql client member '${candidate.id.value}' cannot be private",
            )
        }
        return TransactionalSqlClient(
            logicalId = LsiSymbolId.property(type.id, candidate.name),
            declarationId = candidate.id,
            name = candidate.name,
            type = candidate.type,
            platform = platform,
        )
    }

    private fun compileMethod(
        declaration: LsiDeclaration,
        classTx: LsiAnnotation?,
        typeSystem: LsiTypeSystem,
        workspace: LsiWorkspace,
        platform: TransactionalPlatform,
    ): TransactionalMethod? {
        val directTx = declaration.annotations.annotation(TX_ANNOTATION)
        val supportedCallable = declaration is LsiFunction ||
            declaration is LsiProperty && declaration.origin.source?.language == LsiLanguage.JAVA
        if (!supportedCallable) {
            if (directTx != null) {
                throw TransactionalValidationException(
                    declarationId = declaration.id,
                    message = "Only methods can be decorated by '@${TX_ANNOTATION.value}'",
                )
            }
            return null
        }
        val classLevel = directTx == null
        val effectiveTx = directTx ?: classTx ?: return null
        val callable = declaration.toCallable() ?: return null
        if (classLevel && (
                callable.visibility != LsiVisibility.PUBLIC ||
                    callable.static
            )
        ) {
            return null
        }
        if (callable.static) {
            throw TransactionalValidationException(
                declarationId = declaration.id,
                message = "Transactional method '${declaration.id.value}' cannot be static",
            )
        }
        if (callable.receiverType != null) {
            throw TransactionalValidationException(
                declarationId = declaration.id,
                message = "Transactional method '${declaration.id.value}' cannot be an extension function",
            )
        }
        if (callable.suspending) {
            throw TransactionalValidationException(
                declarationId = declaration.id,
                message = "Transactional method '${declaration.id.value}' cannot be suspend",
            )
        }
        if (callable.visibility == LsiVisibility.PRIVATE) {
            throw TransactionalValidationException(
                declarationId = declaration.id,
                message = "Transactional method '${declaration.id.value}' cannot be private",
            )
        }
        if (callable.modality == LsiModality.FINAL) {
            throw TransactionalValidationException(
                declarationId = declaration.id,
                message = "Transactional method '${declaration.id.value}' must be open",
            )
        }
        if (callable.modality == LsiModality.ABSTRACT) {
            throw TransactionalValidationException(
                declarationId = declaration.id,
                message = "Transactional method '${declaration.id.value}' cannot be abstract",
            )
        }
        val checkedThrownType = callable.thrownTypes.firstOrNull { thrownType ->
            !thrownType.isSubtypeOf(RUNTIME_EXCEPTION_TYPE, typeSystem)
        }
        if (checkedThrownType != null) {
            throw TransactionalValidationException(
                declarationId = declaration.id,
                message = "Transactional method '${declaration.id.value}' can only throw RuntimeException, " +
                    "but declares '$checkedThrownType'",
            )
        }
        return TransactionalMethod(
            id = declaration.id,
            name = callable.name,
            sourceKind = callable.sourceKind,
            visibility = callable.visibility,
            modality = callable.modality,
            returnType = callable.returnType,
            parameters = callable.parameters.map { parameter ->
                parameter.toTransactionalParameter(workspace, platform)
            },
            typeParameters = callable.typeParameters,
            thrownTypes = callable.thrownTypes,
            documentation = declaration.documentation,
            copiedAnnotations = declaration.annotations.filterNot { annotation ->
                annotation.type == TX_ANNOTATION || annotation.type == OVERRIDE_ANNOTATION
            },
            propagation = effectiveTx.propagation(),
            classLevel = classLevel,
        )
    }
}

private fun LsiTypeDeclaration.isTransactionalType(workspace: LsiWorkspace): Boolean {
    if (annotations.annotation(TX_ANNOTATION) != null) {
        return true
    }
    return memberIds.mapNotNull(workspace::get).any { member ->
        member.annotations.annotation(TX_ANNOTATION) != null
    }
}

private fun LsiConstructor.toTransactionalConstructor(
    workspace: LsiWorkspace,
    platform: TransactionalPlatform,
): TransactionalConstructor {
    return TransactionalConstructor(
        id = id,
        primary = primary,
        visibility = visibility,
        parameters = parameters.map { parameter ->
            parameter.toTransactionalParameter(workspace, platform)
        },
        typeParameters = typeParameters,
        thrownTypes = thrownTypes,
        documentation = documentation,
        copiedAnnotations = annotations,
    )
}

private fun LsiParameter.toTransactionalParameter(
    workspace: LsiWorkspace,
    platform: TransactionalPlatform,
): TransactionalParameter {
    val annotationProjection = annotations.transactionalParameterAnnotationProjection(workspace, platform)
    return TransactionalParameter(
        id = id,
        name = name,
        index = index,
        type = type,
        vararg = vararg,
        hasDefault = hasDefault,
        annotations = annotationProjection.annotations,
        annotationProjectionTypeIds = annotationProjection.dependencyTypeIds,
    )
}

private fun List<LsiAnnotation>.transactionalParameterAnnotationProjection(
    workspace: LsiWorkspace,
    platform: TransactionalPlatform,
): TransactionalParameterAnnotationProjection {
    return when (platform) {
        TransactionalPlatform.JAVA -> TransactionalParameterAnnotationProjection(
            annotations = filter { annotation ->
                annotation.useSiteTarget == null ||
                    annotation.useSiteTarget == LsiAnnotationUseSiteTarget.PARAMETER
            },
            dependencyTypeIds = emptySet(),
        )
        TransactionalPlatform.KOTLIN -> TransactionalParameterAnnotationProjection(
            annotations = filter { annotation ->
                annotation.useSiteTarget == null ||
                    annotation.useSiteTarget == LsiAnnotationUseSiteTarget.PARAMETER ||
                    annotation.useSiteTarget == LsiAnnotationUseSiteTarget.ALL &&
                    workspace.allowsParameterTarget(annotation.type)
            }.map { annotation -> annotation.copy(useSiteTarget = null) },
            dependencyTypeIds = asSequence()
                .filter { annotation -> annotation.useSiteTarget == LsiAnnotationUseSiteTarget.ALL }
                .mapTo(sortedSetOf(), LsiAnnotation::type),
        )
    }
}

private fun LsiWorkspace.allowsParameterTarget(annotationTypeId: LsiSymbolId): Boolean {
    val declaration = this[annotationTypeId] as? LsiTypeDeclaration ?: return false
    return declaration.annotationTargetPolicy().allows(LsiAnnotationTarget.PARAMETER)
}

private data class TransactionalParameterAnnotationProjection(
    val annotations: List<LsiAnnotation>,
    val dependencyTypeIds: Set<LsiSymbolId>,
)

private fun LsiDeclaration.toCallable(): CallableModel? {
    return when (this) {
        is LsiFunction -> CallableModel(
            name = name,
            sourceKind = TransactionalMethodSourceKind.FUNCTION,
            visibility = visibility,
            modality = modality,
            returnType = returnType,
            parameters = parameters,
            receiverType = receiverType,
            suspending = suspending,
            typeParameters = typeParameters,
            thrownTypes = thrownTypes,
            static = static,
        )
        is LsiProperty -> CallableModel(
            name = getterName,
            sourceKind = TransactionalMethodSourceKind.PROPERTY_GETTER,
            visibility = visibility,
            modality = modality,
            returnType = type,
            parameters = emptyList(),
            receiverType = null,
            suspending = false,
            typeParameters = emptyList(),
            thrownTypes = emptyList(),
            static = static,
        )
        else -> null
    }
}

private fun LsiTypeRef.isSubtypeOf(
    targetTypeId: LsiSymbolId,
    typeSystem: LsiTypeSystem,
): Boolean {
    val declaredType = this as? LsiDeclaredType ?: return false
    return declaredType.declarationId == targetTypeId ||
        typeSystem.resolveSuperType(declaredType.declarationId, targetTypeId) != null
}

private fun LsiAnnotation.propagation(): String {
    return when (val value = arguments["value"]?.value) {
        is LsiAnnotationValue.EnumValue -> value.entryName
        is LsiAnnotationValue.StringValue -> value.value.substringAfterLast('.')
        else -> "REQUIRED"
    }
}

private fun LsiAnnotation.classTypeId(name: String): LsiSymbolId? {
    val value = arguments[name]?.value as? LsiAnnotationValue.ClassValue ?: return null
    return (value.type as? LsiDeclaredType)?.declarationId
}

private fun List<LsiAnnotation>.annotation(type: LsiSymbolId): LsiAnnotation? {
    return firstOrNull { annotation -> annotation.type == type }
}

private data class StorageMember(
    val id: LsiSymbolId,
    val name: String,
    val type: LsiTypeRef,
    val static: Boolean,
    val visibility: LsiVisibility,
)

private data class CallableModel(
    val name: String,
    val sourceKind: TransactionalMethodSourceKind,
    val visibility: LsiVisibility,
    val modality: LsiModality,
    val returnType: LsiTypeRef,
    val parameters: List<LsiParameter>,
    val receiverType: LsiTypeRef?,
    val suspending: Boolean,
    val typeParameters: List<site.addzero.lsi.model.LsiTypeParameter>,
    val thrownTypes: List<LsiTypeRef>,
    val static: Boolean,
)

private val TX_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.transaction.Tx")
private val TARGET_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.transaction.TargetAnnotation")
private val OVERRIDE_ANNOTATION = LsiSymbolId.type("java.lang.Override")
private val J_SQL_CLIENT_TYPE = LsiSymbolId.type("org.babyfish.jimmer.sql.JSqlClient")
private val K_SQL_CLIENT_TYPE = LsiSymbolId.type("org.babyfish.jimmer.sql.kt.KSqlClient")
private val RUNTIME_EXCEPTION_TYPE = LsiSymbolId.type("java.lang.RuntimeException")

private val ROOT_OBJECT_TYPE_IDS = setOf(
    LsiSymbolId.type("java.lang.Object"),
    LsiSymbolId.type("kotlin.Any"),
)

private val CLASS_LIKE_TYPE_KINDS = setOf(
    LsiTypeDeclarationKind.CLASS,
    LsiTypeDeclarationKind.ENUM,
    LsiTypeDeclarationKind.OBJECT,
    LsiTypeDeclarationKind.RECORD,
)
