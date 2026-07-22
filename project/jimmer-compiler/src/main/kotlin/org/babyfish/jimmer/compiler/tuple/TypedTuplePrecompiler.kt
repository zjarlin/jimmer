package org.babyfish.jimmer.compiler.tuple

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiConstructor
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiDeclaration
import site.addzero.lsi.model.LsiField
import site.addzero.lsi.model.LsiFunctionType
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiUnresolvedType
import site.addzero.lsi.model.LsiVisibility
import site.addzero.lsi.model.LsiWorkspace

class TypedTuplePrecompileException(
    val declarationId: LsiSymbolId,
    val recoverable: Boolean = false,
    message: String,
) : IllegalArgumentException(message)

class TypedTuplePrecompiler {
    fun compile(workspace: LsiWorkspace): TypedTuplePrecompiledSchema {
        val types = workspace.declarationsOfType<LsiTypeDeclaration>()
            .sortedBy(LsiTypeDeclaration::qualifiedName)
        val tuples = types
            .filter { type -> type.annotations.any { annotation -> annotation.type == TYPED_TUPLE_ANNOTATION } }
            .map { type -> compileType(type, workspace) }
        return TypedTuplePrecompiledSchema(tuples)
    }

    private fun compileType(
        type: LsiTypeDeclaration,
        workspace: LsiWorkspace,
    ): TypedTupleType {
        val members = type.memberIds.map { memberId ->
            workspace[memberId] ?: throw TypedTuplePrecompileException(
                declarationId = type.id,
                recoverable = true,
                message = "Typed tuple '${type.qualifiedName}' references missing member '${memberId.value}'",
            )
        }
        val platform = determinePlatform(type, members)
        validateType(type, platform, workspace)
        val preparedType = when (platform) {
            TypedTuplePlatform.JAVA -> prepareJavaType(type, members)
            TypedTuplePlatform.KOTLIN -> prepareKotlinType(type, members)
        }

        val packageName = type.qualifiedName
            .removeSuffix(".${type.name}")
            .takeUnless { value -> value == type.qualifiedName }
            .orEmpty()
        val mapperSimpleName = type.name + "Mapper"
        val mapperQualifiedName = if (packageName.isEmpty()) {
            mapperSimpleName
        } else {
            "$packageName.$mapperSimpleName"
        }
        val properties = preparedType.properties.mapIndexed { index, property ->
            val typeDependencyIds = property.type.dependencyTypeIds()
            val nextProperty = preparedType.properties.getOrNull(index + 1)
            TypedTupleProperty(
                id = LsiSymbolId.property(type.id, property.name),
                sourceMemberId = property.sourceMemberId,
                name = property.name,
                index = index,
                type = property.type,
                nullable = property.type.nullability == LsiNullability.NULLABLE,
                builderSimpleName = property.name.toTupleBuilderSimpleName().takeIf { index > 0 },
                nextStepTypeName = nextProperty?.name?.toTupleBuilderSimpleName() ?: mapperSimpleName,
                typeDependencyIds = typeDependencyIds,
            )
        }
        val duplicateBuilderName = properties
            .mapNotNull(TypedTupleProperty::builderSimpleName)
            .groupingBy { builderName -> builderName }
            .eachCount()
            .entries
            .firstOrNull { (_, count) -> count > 1 }
            ?.key
        if (duplicateBuilderName != null) {
            throw TypedTuplePrecompileException(
                declarationId = type.id,
                message = "Typed tuple '${type.qualifiedName}' produces duplicate builder '$duplicateBuilderName'",
            )
        }
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
            mapperSimpleName = mapperSimpleName,
            mapperQualifiedName = mapperQualifiedName,
            platform = platform,
            properties = properties,
            construction = preparedType.construction,
            dependencies = dependencies,
        )
    }

    private fun determinePlatform(
        type: LsiTypeDeclaration,
        members: List<LsiDeclaration>,
    ): TypedTuplePlatform {
        return when (type.origin.source?.language) {
            LsiLanguage.JAVA -> TypedTuplePlatform.JAVA
            LsiLanguage.KOTLIN -> TypedTuplePlatform.KOTLIN
            LsiLanguage.UNKNOWN,
            null,
            -> if (type.dataClass || members.filterIsInstance<LsiConstructor>().any(LsiConstructor::primary)) {
                TypedTuplePlatform.KOTLIN
            } else {
                TypedTuplePlatform.JAVA
            }
        }
    }

    private fun validateType(
        type: LsiTypeDeclaration,
        platform: TypedTuplePlatform,
        workspace: LsiWorkspace,
    ) {
        if (type.kind != LsiTypeDeclarationKind.CLASS) {
            throw TypedTuplePrecompileException(
                declarationId = type.id,
                message = "Type decorated by '@${TYPED_TUPLE_ANNOTATION.value}' must be a class",
            )
        }
        if (type.enclosingTypeId != null) {
            throw TypedTuplePrecompileException(
                declarationId = type.id,
                message = "Typed tuple '${type.qualifiedName}' must be a top-level class",
            )
        }
        if (type.typeParameters.isNotEmpty()) {
            throw TypedTuplePrecompileException(
                declarationId = type.id,
                message = "Typed tuple '${type.qualifiedName}' cannot be generic",
            )
        }
        if (type.annotations.any { annotation -> annotation.type == LOMBOK_BUILDER_ANNOTATION }) {
            throw TypedTuplePrecompileException(
                declarationId = type.id,
                message = "Typed tuple '${type.qualifiedName}' cannot be decorated by '@${LOMBOK_BUILDER_ANNOTATION.value}'",
            )
        }
        if (platform == TypedTuplePlatform.KOTLIN && !type.dataClass) {
            throw TypedTuplePrecompileException(
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
                        throw TypedTuplePrecompileException(
                            declarationId = type.id,
                            message = "Typed tuple '${type.qualifiedName}' cannot inherit class " +
                                "'${hierarchyEntry.qualifiedName}'",
                        )
                    }
                }
                is LsiUnresolvedType -> throw TypedTuplePrecompileException(
                    declarationId = type.id,
                    recoverable = true,
                    message = "Typed tuple '${type.qualifiedName}' has unresolved supertype '${superType.displayName}'",
                )
                is LsiTypeParameterRef -> throw TypedTuplePrecompileException(
                    declarationId = type.id,
                    message = "Typed tuple '${type.qualifiedName}' cannot inherit a type parameter",
                )
                is LsiArrayType,
                is LsiFunctionType,
                is LsiPrimitiveType,
                -> throw TypedTuplePrecompileException(
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
            throw TypedTuplePrecompileException(
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
        val construction = determineJavaConstruction(type, fields, constructors)
        return PreparedTypedTupleType(properties, construction)
    }

    private fun determineJavaConstruction(
        type: LsiTypeDeclaration,
        fields: List<LsiField>,
        constructors: List<LsiConstructor>,
    ): TypedTupleConstructionPlan {
        if (type.hasAnnotation(LOMBOK_ALL_ARGS_CONSTRUCTOR_ANNOTATION)) {
            return positionalPlan(fields)
        }
        if (type.hasAnnotation(LOMBOK_NO_ARGS_CONSTRUCTOR_ANNOTATION)) {
            return setterPlan(fields, constructors.accessibleDefaultConstructor()?.id)
        }
        if (type.hasAnnotation(LOMBOK_DATA_ANNOTATION)) {
            val mutableStates = fields.map(LsiField::mutable).distinct()
            if (mutableStates.size > 1) {
                throw TypedTuplePrecompileException(
                    declarationId = type.id,
                    message = "Java typed tuple '${type.qualifiedName}' uses '@${LOMBOK_DATA_ANNOTATION.value}' " +
                        "and cannot mix final and non-final fields",
                )
            }
            return if (mutableStates.single()) {
                setterPlan(fields, constructors.accessibleDefaultConstructor()?.id)
            } else {
                positionalPlan(fields)
            }
        }
        val defaultConstructor = constructors.accessibleDefaultConstructor()
        if (defaultConstructor != null || constructors.isEmpty()) {
            return setterPlan(fields, defaultConstructor?.id)
        }
        val constructorMatch = constructors.asSequence()
            .filterNot { constructor -> constructor.visibility == LsiVisibility.PRIVATE }
            .mapNotNull { constructor -> constructor.matchFields(fields) }
            .firstOrNull()
        if (constructorMatch != null) {
            return positionalPlan(fields, constructorMatch)
        }
        throw TypedTuplePrecompileException(
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
            ?: throw TypedTuplePrecompileException(
                declarationId = type.id,
                message = "Kotlin typed tuple '${type.qualifiedName}' must declare one primary constructor",
            )
        if (primaryConstructor.visibility == LsiVisibility.PRIVATE) {
            throw TypedTuplePrecompileException(
                declarationId = primaryConstructor.id,
                message = "Kotlin typed tuple primary constructor '${primaryConstructor.id.value}' cannot be private",
            )
        }
        if (primaryConstructor.parameters.isEmpty()) {
            throw TypedTuplePrecompileException(
                declarationId = primaryConstructor.id,
                message = "Kotlin typed tuple '${type.qualifiedName}' must declare at least one primary property",
            )
        }
        val propertiesByName = properties.associateBy(LsiProperty::name)
        val sourceProperties = primaryConstructor.parameters.map { parameter ->
            val property = propertiesByName[parameter.name]
                ?: throw TypedTuplePrecompileException(
                    declarationId = parameter.id,
                    message = "Kotlin typed tuple primary parameter '${parameter.name}' must declare a property",
                )
            if (property.type != parameter.type) {
                throw TypedTuplePrecompileException(
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
            construction = TypedTupleKotlinNamedPlan(primaryConstructor.id, arguments),
        )
    }

    private fun validateMemberOwner(
        type: LsiTypeDeclaration,
        memberId: LsiSymbolId,
        ownerId: LsiSymbolId,
    ) {
        if (ownerId != type.id) {
            throw TypedTuplePrecompileException(
                declarationId = memberId,
                message = "Typed tuple member '${memberId.value}' is not declared by '${type.qualifiedName}'",
            )
        }
    }
}

private data class PreparedTypedTupleType(
    val properties: List<SourceProperty>,
    val construction: TypedTupleConstructionPlan,
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

private fun setterPlan(
    fields: List<LsiField>,
    constructorId: LsiSymbolId?,
): TypedTupleJavaSetterPlan {
    return TypedTupleJavaSetterPlan(
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

private fun positionalPlan(
    fields: List<LsiField>,
    match: JavaConstructorMatch? = null,
): TypedTupleJavaPositionalPlan {
    val fieldIndexes = fields.withIndex().associate { (index, field) -> field.id to index }
    val parameters = match?.constructor?.parameters
    val orderedFields = match?.fieldsByParameter ?: fields
    return TypedTupleJavaPositionalPlan(
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
        is LsiFunctionType -> throw TypedTuplePrecompileException(
            declarationId = sourceMemberId,
            message = "Typed tuple property '${sourceMemberId.value}' cannot use a function type",
        )
        is LsiPrimitiveType -> if (kind == LsiPrimitiveKind.UNIT || kind == LsiPrimitiveKind.VOID) {
            throw TypedTuplePrecompileException(
                declarationId = sourceMemberId,
                message = "Typed tuple property '${sourceMemberId.value}' cannot use ${kind.name.lowercase()} type",
            )
        }
        is LsiTypeParameterRef -> throw TypedTuplePrecompileException(
            declarationId = sourceMemberId,
            message = "Typed tuple property '${sourceMemberId.value}' cannot reference a type parameter",
        )
        is LsiUnresolvedType -> throw TypedTuplePrecompileException(
            declarationId = tupleId,
            recoverable = true,
            message = "Typed tuple property '${sourceMemberId.value}' has unresolved type '$displayName'",
        )
    }
}

private fun LsiTypeRef.dependencyTypeIds(): List<LsiSymbolId> {
    return sortedSetOf<LsiSymbolId>()
        .apply { collectDependencyType(this@dependencyTypeIds) }
        .toList()
}

private fun MutableSet<LsiSymbolId>.collectDependencyType(type: LsiTypeRef) {
    type.annotations.forEach(::collectDependencyAnnotation)
    when (type) {
        is LsiDeclaredType -> {
            add(type.declarationId)
            type.arguments.forEach { argument -> argument.type?.let(::collectDependencyType) }
        }
        is LsiArrayType -> collectDependencyType(type.elementType)
        is LsiFunctionType -> {
            type.receiverType?.let(::collectDependencyType)
            type.parameterTypes.forEach(::collectDependencyType)
            collectDependencyType(type.returnType)
        }
        is LsiPrimitiveType,
        is LsiTypeParameterRef,
        is LsiUnresolvedType,
        -> Unit
    }
}

private fun MutableSet<LsiSymbolId>.collectDependencyAnnotation(annotation: LsiAnnotation) {
    add(annotation.type)
    annotation.arguments.values.forEach { argument -> collectDependencyAnnotationValue(argument.value) }
}

private fun MutableSet<LsiSymbolId>.collectDependencyAnnotationValue(value: LsiAnnotationValue) {
    when (value) {
        is LsiAnnotationValue.ArrayValue -> value.elements.forEach(::collectDependencyAnnotationValue)
        is LsiAnnotationValue.ClassValue -> collectDependencyType(value.type)
        is LsiAnnotationValue.EnumValue -> add(value.enumType)
        is LsiAnnotationValue.NestedAnnotationValue -> collectDependencyAnnotation(value.annotation)
        is LsiAnnotationValue.BooleanValue,
        is LsiAnnotationValue.ByteValue,
        is LsiAnnotationValue.ShortValue,
        is LsiAnnotationValue.IntValue,
        is LsiAnnotationValue.LongValue,
        is LsiAnnotationValue.FloatValue,
        is LsiAnnotationValue.DoubleValue,
        is LsiAnnotationValue.CharValue,
        is LsiAnnotationValue.StringValue,
        -> Unit
    }
}

private fun String.toTupleBuilderSimpleName(): String {
    return typeName(this, "Builder")
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

private fun typeName(vararg parts: String): String {
    val result = StringBuilder()
    var previousPartEndsWithLowercase = true
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
