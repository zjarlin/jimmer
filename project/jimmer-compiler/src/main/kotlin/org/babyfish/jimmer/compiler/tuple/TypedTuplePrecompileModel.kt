package org.babyfish.jimmer.compiler.tuple

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiTypeRef

data class TypedTuplePrecompiledSchema(
    val tuples: List<TypedTupleType>,
)

data class TypedTupleType(
    val id: LsiSymbolId,
    val qualifiedName: String,
    val packageName: String,
    val simpleName: String,
    val mapperSimpleName: String,
    val mapperQualifiedName: String,
    val platform: TypedTuplePlatform,
    val properties: List<TypedTupleProperty>,
    val construction: TypedTupleConstructionPlan,
    val dependencies: TypedTupleDependencies,
) {
    init {
        require(properties.map(TypedTupleProperty::index) == properties.indices.toList()) {
            "Typed tuple properties must use contiguous zero-based indexes: ${id.value}"
        }
        require(properties.map(TypedTupleProperty::sourceMemberId).distinct().size == properties.size) {
            "Typed tuple properties must reference distinct source members: ${id.value}"
        }
        require(construction.propertyIndexes.sorted() == properties.indices.toList()) {
            "Typed tuple construction must consume every property exactly once: ${id.value}"
        }
        require(
            construction.propertyIndexes.zip(construction.sourceMemberIds).all { (propertyIndex, sourceMemberId) ->
                propertyIndex in properties.indices && properties[propertyIndex].sourceMemberId == sourceMemberId
            }
        ) {
            "Typed tuple construction must reference the source member of each property: ${id.value}"
        }
        require(
            when (platform) {
                TypedTuplePlatform.JAVA -> construction is TypedTupleJavaSetterPlan ||
                    construction is TypedTupleJavaPositionalPlan
                TypedTuplePlatform.KOTLIN -> construction is TypedTupleKotlinNamedPlan
            }
        ) {
            "Typed tuple construction does not match platform '$platform': ${id.value}"
        }
    }
}

data class TypedTupleProperty(
    val id: LsiSymbolId,
    val sourceMemberId: LsiSymbolId,
    val name: String,
    val index: Int,
    val type: LsiTypeRef,
    val nullable: Boolean,
    val builderSimpleName: String?,
    val nextStepTypeName: String,
    val typeDependencyIds: List<LsiSymbolId>,
)

enum class TypedTuplePlatform {
    JAVA,
    KOTLIN,
}

sealed interface TypedTupleConstructionPlan {
    val constructorId: LsiSymbolId?
    val propertyIndexes: List<Int>
    val sourceMemberIds: List<LsiSymbolId>
}

data class TypedTupleJavaSetterPlan(
    override val constructorId: LsiSymbolId?,
    val assignments: List<TypedTupleSetterAssignment>,
) : TypedTupleConstructionPlan {
    override val propertyIndexes: List<Int>
        get() = assignments.map(TypedTupleSetterAssignment::propertyIndex)

    override val sourceMemberIds: List<LsiSymbolId>
        get() = assignments.map(TypedTupleSetterAssignment::sourceMemberId)
}

data class TypedTupleJavaPositionalPlan(
    override val constructorId: LsiSymbolId?,
    val arguments: List<TypedTupleConstructorArgument>,
) : TypedTupleConstructionPlan {
    init {
        require(arguments.map(TypedTupleConstructorArgument::parameterIndex) == arguments.indices.toList()) {
            "Java typed tuple constructor arguments must use contiguous parameter indexes"
        }
        require(arguments.all { argument -> argument.parameterId != null } == (constructorId != null)) {
            "Java typed tuple constructor arguments must match constructor source availability"
        }
    }

    override val propertyIndexes: List<Int>
        get() = arguments.map(TypedTupleConstructorArgument::propertyIndex)

    override val sourceMemberIds: List<LsiSymbolId>
        get() = arguments.map(TypedTupleConstructorArgument::sourceMemberId)
}

data class TypedTupleKotlinNamedPlan(
    override val constructorId: LsiSymbolId,
    val arguments: List<TypedTupleConstructorArgument>,
) : TypedTupleConstructionPlan {
    init {
        require(arguments.map(TypedTupleConstructorArgument::parameterIndex) == arguments.indices.toList()) {
            "Kotlin typed tuple constructor arguments must use contiguous parameter indexes"
        }
        require(arguments.all { argument -> argument.parameterId != null }) {
            "Kotlin typed tuple constructor arguments must reference primary parameters"
        }
    }

    override val propertyIndexes: List<Int>
        get() = arguments.map(TypedTupleConstructorArgument::propertyIndex)

    override val sourceMemberIds: List<LsiSymbolId>
        get() = arguments.map(TypedTupleConstructorArgument::sourceMemberId)
}

data class TypedTupleSetterAssignment(
    val sourceMemberId: LsiSymbolId,
    val propertyIndex: Int,
    val setterName: String,
) {
    init {
        require(propertyIndex >= 0) { "Typed tuple setter property index cannot be negative" }
        require(setterName.isNotBlank()) { "Typed tuple setter name cannot be blank" }
    }
}

data class TypedTupleConstructorArgument(
    val sourceMemberId: LsiSymbolId,
    val propertyIndex: Int,
    val parameterId: LsiSymbolId?,
    val parameterIndex: Int,
    val parameterName: String,
) {
    init {
        require(propertyIndex >= 0) { "Typed tuple constructor property index cannot be negative" }
        require(parameterIndex >= 0) { "Typed tuple constructor parameter index cannot be negative" }
        require(parameterName.isNotBlank()) { "Typed tuple constructor parameter name cannot be blank" }
    }
}

data class TypedTupleDependencies(
    val typeIds: List<LsiSymbolId>,
    val memberIds: List<LsiSymbolId>,
) {
    init {
        require(typeIds == typeIds.distinct().sorted()) {
            "Typed tuple type dependencies must be distinct and sorted"
        }
        require(memberIds == memberIds.distinct().sorted()) {
            "Typed tuple member dependencies must be distinct and sorted"
        }
    }

    val symbolIds: List<LsiSymbolId>
        get() = (typeIds + memberIds).distinct().sorted()
}
