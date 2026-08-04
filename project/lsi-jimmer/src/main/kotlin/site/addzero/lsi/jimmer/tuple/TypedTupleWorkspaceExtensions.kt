package site.addzero.lsi.jimmer.tuple

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiConstructor
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiDeclaration
import site.addzero.lsi.model.LsiField
import site.addzero.lsi.model.LsiFunctionType
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiTypeSystem
import site.addzero.lsi.model.LsiUnresolvedType
import site.addzero.lsi.model.LsiVisibility
import site.addzero.lsi.model.LsiWorkspace

/** TypedTuple 语义校验失败。 */
class TypedTupleValidationException(
    val declarationId: LsiSymbolId,
    val recoverable: Boolean = false,
    message: String,
) : IllegalArgumentException(message)

/** 将冻结的 LSI 工作区解析为 TypedTuple 共享语义。 */
fun LsiWorkspace.toTypedTupleSchema(
    entityTypeIds: Set<LsiSymbolId> = emptySet(),
    dtoTypeIds: Set<LsiSymbolId> = emptySet(),
): TypedTupleSchema {
    return TypedTupleSchemaBuilder(this, entityTypeIds, dtoTypeIds).build()
}

/** 返回当前工作区直接声明的全部 TypedTuple 类型符号。 */
fun LsiWorkspace.typedTupleTypeIds(): Set<LsiSymbolId> {
    return declarationsOfType<LsiTypeDeclaration>()
        .filter { type -> type.hasAnnotation(TYPED_TUPLE_ANNOTATION) }
        .mapTo(sortedSetOf()) { type -> type.id }
}

private class TypedTupleSchemaBuilder(
    private val workspace: LsiWorkspace,
    entityTypeIds: Set<LsiSymbolId>,
    private val dtoTypeIds: Set<LsiSymbolId>,
) {
    private val typeSystem = LsiTypeSystem(workspace)

    private val entityTypeIds = entityTypeIds + workspace
        .declarationsOfType<LsiTypeDeclaration>()
        .filter { type -> type.hasAnnotation(ENTITY_ANNOTATION) }
        .map(LsiTypeDeclaration::id)

    fun build(): TypedTupleSchema {
        val tuples = workspace.declarationsOfType<LsiTypeDeclaration>()
            .asSequence()
            .filter { type -> type.hasAnnotation(TYPED_TUPLE_ANNOTATION) }
            .sortedBy(LsiTypeDeclaration::qualifiedName)
            .map(::compileType)
            .toList()
        return TypedTupleSchema(tuples)
    }

    private fun compileType(type: LsiTypeDeclaration): TypedTupleType {
        val members = type.memberIds.map { memberId ->
            workspace[memberId] ?: throw TypedTupleValidationException(
                declarationId = type.id,
                recoverable = true,
                message = "Typed tuple '${type.qualifiedName}' references missing member '${memberId.value}'",
            )
        }
        val sourceLanguage = determineSourceLanguage(type, members)
        validateType(type, sourceLanguage)
        val preparedType = when (sourceLanguage) {
            LsiLanguage.JAVA -> prepareJavaType(type, members)
            LsiLanguage.KOTLIN -> prepareKotlinType(type, members)
            LsiLanguage.UNKNOWN -> error("Typed tuple source language must be resolved")
        }
        val packageName = type.qualifiedName
            .removeSuffix(".${type.name}")
            .takeUnless { value -> value == type.qualifiedName }
            .orEmpty()
        val properties = preparedType.properties.mapIndexed { index, property ->
            TypedTupleProperty(
                id = LsiSymbolId.property(type.id, property.name),
                sourceMemberId = property.sourceMemberId,
                name = property.name,
                index = index,
                type = property.type,
            )
        }
        val baseTableProjection = properties
            .takeUnless { tupleProperties -> tupleProperties.any(::isDtoProperty) }
            ?.mapIndexed { index, property -> property.toBaseTableSelection(index) }
            ?.let(::TypedTupleBaseTableProjection)
        val dependencies = TypedTupleDependencies(
            typeIds = (listOf(type.id) + properties.flatMap(TypedTupleProperty::typeDependencyIds))
                .distinct()
                .sorted(),
            memberIds = (properties.map(TypedTupleProperty::sourceMemberId) + preparedType.construction.constructorId)
                .filterNotNull()
                .distinct()
                .sorted(),
        )
        return TypedTupleType(
            id = type.id,
            qualifiedName = type.qualifiedName,
            packageName = packageName,
            simpleName = type.name,
            sourceLanguage = sourceLanguage,
            properties = properties,
            construction = preparedType.construction,
            baseTableProjection = baseTableProjection,
            dependencies = dependencies,
        )
    }

    private fun isDtoProperty(property: TypedTupleProperty): Boolean {
        val declaredType = property.type as? LsiDeclaredType ?: return false
        return declaredType.declarationId in dtoTypeIds || DTO_SUPER_TYPE_IDS.any { dtoSuperTypeId ->
            typeSystem.resolveSuperType(declaredType, dtoSuperTypeId) != null
        }
    }

    private fun TypedTupleProperty.toBaseTableSelection(
        expectedIndex: Int,
    ): TypedTupleBaseTableSelection {
        check(index == expectedIndex) {
            "Typed tuple property order changed while compiling base-table projection: ${id.value}"
        }
        val declaredType = type as? LsiDeclaredType
        val entityTypeId = declaredType?.declarationId?.takeIf(entityTypeIds::contains)
        if (entityTypeId != null) {
            val entityTypeName = workspace.typeHierarchyEntry(entityTypeId)?.qualifiedName
                ?: entityTypeId.requireTypeQualifiedName()
            val packageName = entityTypeName.substringBeforeLast('.', missingDelimiterValue = "")
            val simpleName = entityTypeName.substringAfterLast('.')
            val tableQualifiedName = if (packageName.isEmpty()) {
                "${simpleName}Table"
            } else {
                "$packageName.${simpleName}Table"
            }
            return TypedTupleBaseTableSelection(
                propertyIndex = index,
                kind = if (nullable) {
                    TypedTupleBaseTableSelectionKind.NULLABLE_TABLE
                } else {
                    TypedTupleBaseTableSelectionKind.NON_NULL_TABLE
                },
                entityTableTypeId = LsiSymbolId.type(tableQualifiedName),
            )
        }
        return TypedTupleBaseTableSelection(
            propertyIndex = index,
            kind = if (nullable) {
                TypedTupleBaseTableSelectionKind.NULLABLE_EXPRESSION
            } else {
                TypedTupleBaseTableSelectionKind.NON_NULL_EXPRESSION
            },
            scalarCategory = type.scalarCategory(),
        )
    }

    private fun LsiTypeRef.scalarCategory(): TypedTupleScalarCategory {
        return when (this) {
            is LsiPrimitiveType -> when (kind) {
                LsiPrimitiveKind.BYTE,
                LsiPrimitiveKind.SHORT,
                LsiPrimitiveKind.INT,
                LsiPrimitiveKind.LONG,
                LsiPrimitiveKind.FLOAT,
                LsiPrimitiveKind.DOUBLE,
                -> TypedTupleScalarCategory.NUMERIC
                LsiPrimitiveKind.BOOLEAN,
                LsiPrimitiveKind.CHAR,
                -> TypedTupleScalarCategory.COMPARABLE
                LsiPrimitiveKind.UNIT,
                LsiPrimitiveKind.VOID,
                -> TypedTupleScalarCategory.GENERIC
            }
            is LsiDeclaredType -> when {
                declarationId == STRING_TYPE_ID -> TypedTupleScalarCategory.STRING
                isSubtypeOf(NUMBER_TYPE_ID) -> TypedTupleScalarCategory.NUMERIC
                isSubtypeOf(DATE_TYPE_ID) -> TypedTupleScalarCategory.DATE
                isSubtypeOf(TEMPORAL_TYPE_ID) -> TypedTupleScalarCategory.TEMPORAL
                isSubtypeOf(COMPARABLE_TYPE_ID) -> TypedTupleScalarCategory.COMPARABLE
                else -> TypedTupleScalarCategory.GENERIC
            }
            is LsiArrayType,
            is LsiFunctionType,
            is LsiTypeParameterRef,
            is LsiUnresolvedType,
            -> TypedTupleScalarCategory.GENERIC
        }
    }

    private fun LsiDeclaredType.isSubtypeOf(superTypeId: LsiSymbolId): Boolean {
        return declarationId == superTypeId || typeSystem.resolveSuperType(this, superTypeId) != null
    }

    private fun determineSourceLanguage(
        type: LsiTypeDeclaration,
        members: List<LsiDeclaration>,
    ): LsiLanguage {
        return when (type.origin.source?.language) {
            LsiLanguage.JAVA -> LsiLanguage.JAVA
            LsiLanguage.KOTLIN -> LsiLanguage.KOTLIN
            LsiLanguage.UNKNOWN,
            null,
            -> if (type.dataClass || members.filterIsInstance<LsiConstructor>().any(LsiConstructor::primary)) {
                LsiLanguage.KOTLIN
            } else {
                LsiLanguage.JAVA
            }
        }
    }

    private fun validateType(
        type: LsiTypeDeclaration,
        sourceLanguage: LsiLanguage,
    ) {
        if (type.kind != LsiTypeDeclarationKind.CLASS) {
            throw TypedTupleValidationException(
                declarationId = type.id,
                message = "Type decorated by '@${TYPED_TUPLE_ANNOTATION.value}' must be a class",
            )
        }
        if (type.enclosingTypeId != null) {
            throw TypedTupleValidationException(
                declarationId = type.id,
                message = "Typed tuple '${type.qualifiedName}' must be a top-level class",
            )
        }
        if (type.typeParameters.isNotEmpty()) {
            throw TypedTupleValidationException(
                declarationId = type.id,
                message = "Typed tuple '${type.qualifiedName}' cannot be generic",
            )
        }
        if (type.hasAnnotation(LOMBOK_BUILDER_ANNOTATION)) {
            throw TypedTupleValidationException(
                declarationId = type.id,
                message = "Typed tuple '${type.qualifiedName}' cannot be decorated by " +
                    "'@${LOMBOK_BUILDER_ANNOTATION.value}'",
            )
        }
        if (sourceLanguage == LsiLanguage.KOTLIN && !type.dataClass) {
            throw TypedTupleValidationException(
                declarationId = type.id,
                message = "Kotlin typed tuple '${type.qualifiedName}' must be a data class",
            )
        }
        type.superTypes.forEach { superType ->
            when (superType) {
                is LsiDeclaredType -> {
                    if (superType.declarationId in ROOT_OBJECT_TYPE_IDS) {
                        return@forEach
                    }
                    val hierarchyEntry = workspace.typeHierarchyEntry(superType.declarationId)
                    if (hierarchyEntry != null && hierarchyEntry.kind in CLASS_LIKE_TYPE_KINDS) {
                        throw TypedTupleValidationException(
                            declarationId = type.id,
                            message = "Typed tuple '${type.qualifiedName}' cannot inherit class " +
                                "'${hierarchyEntry.qualifiedName}'",
                        )
                    }
                }
                is LsiUnresolvedType -> throw TypedTupleValidationException(
                    declarationId = type.id,
                    recoverable = true,
                    message = "Typed tuple '${type.qualifiedName}' has unresolved supertype '${superType.displayName}'",
                )
                is LsiTypeParameterRef -> throw TypedTupleValidationException(
                    declarationId = type.id,
                    message = "Typed tuple '${type.qualifiedName}' cannot inherit a type parameter",
                )
                is LsiArrayType,
                is LsiFunctionType,
                is LsiPrimitiveType,
                -> throw TypedTupleValidationException(
                    declarationId = type.id,
                    message = "Typed tuple '${type.qualifiedName}' has an invalid supertype",
                )
            }
        }
    }

    private fun prepareJavaType(
        type: LsiTypeDeclaration,
        members: List<LsiDeclaration>,
    ): PreparedTypedTupleType {
        val fields = members.filterIsInstance<LsiField>()
            .filterNot(LsiField::static)
        fields.forEach { field -> validateMemberOwner(type, field.id, field.ownerId) }
        if (fields.isEmpty()) {
            throw TypedTupleValidationException(
                declarationId = type.id,
                message = "Java typed tuple '${type.qualifiedName}' must declare at least one non-static field",
            )
        }
        fields.forEach { field -> field.type.validateTuplePropertyType(type.id, field.id) }
        val properties = fields.map { field ->
            SourceProperty(field.id, field.name, field.type)
        }
        val constructors = members.filterIsInstance<LsiConstructor>()
        constructors.forEach { constructor -> validateMemberOwner(type, constructor.id, constructor.ownerId) }
        return PreparedTypedTupleType(
            properties = properties,
            construction = determineJavaConstruction(type, fields, constructors),
        )
    }

    private fun determineJavaConstruction(
        type: LsiTypeDeclaration,
        fields: List<LsiField>,
        constructors: List<LsiConstructor>,
    ): TypedTupleConstruction {
        if (type.hasAnnotation(LOMBOK_ALL_ARGS_CONSTRUCTOR_ANNOTATION)) {
            return constructorConstruction(fields)
        }
        if (type.hasAnnotation(LOMBOK_NO_ARGS_CONSTRUCTOR_ANNOTATION)) {
            return setterConstruction(fields, constructors.accessibleDefaultConstructor()?.id)
        }
        if (type.hasAnnotation(LOMBOK_DATA_ANNOTATION)) {
            val mutableStates = fields.map(LsiField::mutable).distinct()
            if (mutableStates.size > 1) {
                throw TypedTupleValidationException(
                    declarationId = type.id,
                    message = "Java typed tuple '${type.qualifiedName}' uses '@${LOMBOK_DATA_ANNOTATION.value}' " +
                        "and cannot mix final and non-final fields",
                )
            }
            return if (mutableStates.single()) {
                setterConstruction(fields, constructors.accessibleDefaultConstructor()?.id)
            } else {
                constructorConstruction(fields)
            }
        }
        val defaultConstructor = constructors.accessibleDefaultConstructor()
        if (defaultConstructor != null || constructors.isEmpty()) {
            return setterConstruction(fields, defaultConstructor?.id)
        }
        val constructorMatch = constructors.asSequence()
            .filterNot { constructor -> constructor.visibility == LsiVisibility.PRIVATE }
            .mapNotNull { constructor -> constructor.matchFields(fields) }
            .firstOrNull()
        if (constructorMatch != null) {
            return constructorConstruction(fields, constructorMatch)
        }
        throw TypedTupleValidationException(
            declarationId = type.id,
            message = "Java typed tuple '${type.qualifiedName}' must declare an accessible no-argument constructor " +
                "or a constructor whose parameters match all fields by name and type",
        )
    }

    private fun prepareKotlinType(
        type: LsiTypeDeclaration,
        members: List<LsiDeclaration>,
    ): PreparedTypedTupleType {
        val properties = members.filterIsInstance<LsiProperty>()
            .filterNot(LsiProperty::static)
        properties.forEach { property -> validateMemberOwner(type, property.id, property.ownerId) }
        val primaryConstructor = members.filterIsInstance<LsiConstructor>()
            .singleOrNull(LsiConstructor::primary)
            ?: throw TypedTupleValidationException(
                declarationId = type.id,
                message = "Kotlin typed tuple '${type.qualifiedName}' must declare one primary constructor",
            )
        if (primaryConstructor.visibility == LsiVisibility.PRIVATE) {
            throw TypedTupleValidationException(
                declarationId = primaryConstructor.id,
                message = "Kotlin typed tuple primary constructor '${primaryConstructor.id.value}' cannot be private",
            )
        }
        if (primaryConstructor.parameters.isEmpty()) {
            throw TypedTupleValidationException(
                declarationId = primaryConstructor.id,
                message = "Kotlin typed tuple '${type.qualifiedName}' must declare at least one primary property",
            )
        }
        val propertiesByName = properties.associateBy(LsiProperty::name)
        val sourceProperties = primaryConstructor.parameters.map { parameter ->
            val property = propertiesByName[parameter.name]
                ?: throw TypedTupleValidationException(
                    declarationId = parameter.id,
                    message = "Kotlin typed tuple primary parameter '${parameter.name}' must declare a property",
                )
            if (property.type != parameter.type) {
                throw TypedTupleValidationException(
                    declarationId = parameter.id,
                    message = "Kotlin typed tuple primary property '${property.name}' must have the same type as its parameter",
                )
            }
            property.type.validateTuplePropertyType(type.id, property.id)
            SourceProperty(property.id, property.name, property.type)
        }
        val arguments = primaryConstructor.parameters.mapIndexed { propertyIndex, parameter ->
            val sourceProperty = sourceProperties[propertyIndex]
            TypedTupleConstructorArgument(
                sourceMemberId = sourceProperty.sourceMemberId,
                propertyIndex = propertyIndex,
                parameterId = parameter.id,
                parameterIndex = parameter.index,
                parameterName = parameter.name,
            )
        }
        return PreparedTypedTupleType(
            properties = sourceProperties,
            construction = TypedTupleKotlinConstructorConstruction(primaryConstructor.id, arguments),
        )
    }

    private fun validateMemberOwner(
        type: LsiTypeDeclaration,
        memberId: LsiSymbolId,
        ownerId: LsiSymbolId,
    ) {
        if (ownerId != type.id) {
            throw TypedTupleValidationException(
                declarationId = memberId,
                message = "Typed tuple member '${memberId.value}' is not declared by '${type.qualifiedName}'",
            )
        }
    }
}

private data class PreparedTypedTupleType(
    val properties: List<SourceProperty>,
    val construction: TypedTupleConstruction,
)

private data class SourceProperty(
    val sourceMemberId: LsiSymbolId,
    val name: String,
    val type: LsiTypeRef,
)

private data class JavaConstructorMatch(
    val constructor: LsiConstructor,
    val fieldsByParameter: List<LsiField>,
)

private fun List<LsiConstructor>.accessibleDefaultConstructor(): LsiConstructor? {
    return firstOrNull { constructor ->
        constructor.visibility != LsiVisibility.PRIVATE && constructor.parameters.isEmpty()
    }
}

private fun LsiConstructor.matchFields(fields: List<LsiField>): JavaConstructorMatch? {
    if (parameters.size != fields.size) {
        return null
    }
    val fieldsByName = fields.associateBy(LsiField::name)
    val matchedFields = parameters.map { parameter ->
        val field = fieldsByName[parameter.name] ?: return null
        if (field.type != parameter.type) {
            return null
        }
        field
    }
    if (matchedFields.map(LsiField::id).distinct().size != fields.size) {
        return null
    }
    return JavaConstructorMatch(this, matchedFields)
}

private fun setterConstruction(
    fields: List<LsiField>,
    constructorId: LsiSymbolId?,
): TypedTupleJavaSetterConstruction {
    return TypedTupleJavaSetterConstruction(
        constructorId = constructorId,
        assignments = fields.mapIndexed { propertyIndex, field ->
            TypedTupleSetterAssignment(
                sourceMemberId = field.id,
                propertyIndex = propertyIndex,
                setterName = identifierName("set", field.name),
            )
        },
    )
}

private fun constructorConstruction(
    fields: List<LsiField>,
    match: JavaConstructorMatch? = null,
): TypedTupleJavaConstructorConstruction {
    val fieldIndexes = fields.withIndex().associate { (index, field) -> field.id to index }
    val parameters = match?.constructor?.parameters
    val orderedFields = match?.fieldsByParameter ?: fields
    return TypedTupleJavaConstructorConstruction(
        constructorId = match?.constructor?.id,
        arguments = orderedFields.mapIndexed { parameterIndex, field ->
            val parameter = parameters?.get(parameterIndex)
            TypedTupleConstructorArgument(
                sourceMemberId = field.id,
                propertyIndex = requireNotNull(fieldIndexes[field.id]),
                parameterId = parameter?.id,
                parameterIndex = parameterIndex,
                parameterName = parameter?.name ?: field.name,
            )
        },
    )
}

private fun LsiTypeDeclaration.hasAnnotation(annotationType: LsiSymbolId): Boolean {
    return annotations.any { annotation -> annotation.type == annotationType }
}

private fun LsiTypeRef.validateTuplePropertyType(
    tupleId: LsiSymbolId,
    sourceMemberId: LsiSymbolId,
) {
    when (this) {
        is LsiDeclaredType -> arguments.forEach { argument ->
            argument.type?.validateTuplePropertyType(tupleId, sourceMemberId)
        }
        is LsiArrayType -> elementType.validateTuplePropertyType(tupleId, sourceMemberId)
        is LsiFunctionType -> throw TypedTupleValidationException(
            declarationId = sourceMemberId,
            message = "Typed tuple property '${sourceMemberId.value}' cannot use a function type",
        )
        is LsiPrimitiveType -> if (kind == LsiPrimitiveKind.UNIT || kind == LsiPrimitiveKind.VOID) {
            throw TypedTupleValidationException(
                declarationId = sourceMemberId,
                message = "Typed tuple property '${sourceMemberId.value}' cannot use ${kind.name.lowercase()} type",
            )
        }
        is LsiTypeParameterRef -> throw TypedTupleValidationException(
            declarationId = sourceMemberId,
            message = "Typed tuple property '${sourceMemberId.value}' cannot reference a type parameter",
        )
        is LsiUnresolvedType -> throw TypedTupleValidationException(
            declarationId = tupleId,
            recoverable = true,
            message = "Typed tuple property '${sourceMemberId.value}' has unresolved type '$displayName'",
        )
    }
}

private fun identifierName(vararg parts: String): String {
    val result = StringBuilder()
    var previousPartEndsWithLowercase = false
    for (part in parts) {
        if (part.isEmpty()) {
            continue
        }
        if (previousPartEndsWithLowercase) {
            if (part.first().isUpperCase()) {
                result.append(part)
            } else {
                result.append(part.first().uppercaseChar()).append(part.drop(1))
            }
        } else if (part.first().isLowerCase()) {
            result.append(part)
        } else {
            val characters = part.toCharArray()
            for (index in characters.indices) {
                if (characters[index].isLowerCase()) {
                    break
                }
                characters[index] = characters[index].lowercaseChar()
            }
            result.append(characters)
        }
        previousPartEndsWithLowercase = part.last().isLowerCase()
    }
    return result.toString()
}

private val TYPED_TUPLE_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.TypedTuple")
private val ENTITY_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.sql.Entity")
private val DTO_SUPER_TYPE_IDS = setOf(
    LsiSymbolId.type("org.babyfish.jimmer.View"),
    LsiSymbolId.type("org.babyfish.jimmer.Input"),
    LsiSymbolId.type("org.babyfish.jimmer.Specification"),
)
private val STRING_TYPE_ID = LsiSymbolId.type("java.lang.String")
private val NUMBER_TYPE_ID = LsiSymbolId.type("java.lang.Number")
private val DATE_TYPE_ID = LsiSymbolId.type("java.util.Date")
private val TEMPORAL_TYPE_ID = LsiSymbolId.type("java.time.temporal.Temporal")
private val COMPARABLE_TYPE_ID = LsiSymbolId.type("java.lang.Comparable")
private val LOMBOK_BUILDER_ANNOTATION = LsiSymbolId.type("lombok.Builder")
private val LOMBOK_ALL_ARGS_CONSTRUCTOR_ANNOTATION = LsiSymbolId.type("lombok.AllArgsConstructor")
private val LOMBOK_NO_ARGS_CONSTRUCTOR_ANNOTATION = LsiSymbolId.type("lombok.NoArgsConstructor")
private val LOMBOK_DATA_ANNOTATION = LsiSymbolId.type("lombok.Data")
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
