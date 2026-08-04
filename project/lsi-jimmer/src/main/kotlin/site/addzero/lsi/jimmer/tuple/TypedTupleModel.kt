package site.addzero.lsi.jimmer.tuple

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.collectTypeRefDependencies

/** 当前工作区内全部 TypedTuple 的共享语义。 */
data class TypedTupleSchema(
    val tuples: List<TypedTupleType>,
) {
    init {
        require(tuples.map(TypedTupleType::id).distinct().size == tuples.size) {
            "Typed tuple schema cannot contain duplicate tuple ids"
        }
    }
}

/** 单个 TypedTuple 的稳定声明与构造语义。 */
data class TypedTupleType(
    val id: LsiSymbolId,
    val qualifiedName: String,
    val packageName: String,
    val simpleName: String,
    val sourceLanguage: LsiLanguage,
    val properties: List<TypedTupleProperty>,
    val construction: TypedTupleConstruction,
    val baseTableProjection: TypedTupleBaseTableProjection? = null,
    val dependencies: TypedTupleDependencies,
) {
    init {
        require(sourceLanguage == LsiLanguage.JAVA || sourceLanguage == LsiLanguage.KOTLIN) {
            "Typed tuple source language must be Java or Kotlin: ${id.value}"
        }
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
            when (sourceLanguage) {
                LsiLanguage.JAVA -> construction is TypedTupleJavaSetterConstruction ||
                    construction is TypedTupleJavaConstructorConstruction
                LsiLanguage.KOTLIN -> construction is TypedTupleKotlinConstructorConstruction
                LsiLanguage.UNKNOWN -> false
            }
        ) {
            "Typed tuple construction does not match source language '$sourceLanguage': ${id.value}"
        }
        require(
            baseTableProjection == null ||
                baseTableProjection.selections.map(TypedTupleBaseTableSelection::propertyIndex) == properties.indices.toList()
        ) {
            "Typed tuple base-table projection must describe every property in declaration order: ${id.value}"
        }
    }
}

/** TypedTuple 属性的稳定源身份与类型。 */
data class TypedTupleProperty(
    val id: LsiSymbolId,
    val sourceMemberId: LsiSymbolId,
    val name: String,
    val index: Int,
    val type: LsiTypeRef,
) {
    val nullable: Boolean
        get() = type.nullability == LsiNullability.NULLABLE

    val typeDependencyIds: List<LsiSymbolId>
        get() = sortedSetOf<LsiSymbolId>()
            .apply { collectTypeRefDependencies(type) }
            .toList()
}

/** TypedTuple 作为具名 base-table facade 时的稳定选择布局。 */
data class TypedTupleBaseTableProjection(
    val selections: List<TypedTupleBaseTableSelection>,
) {
    init {
        require(selections.isNotEmpty()) {
            "Typed tuple base-table projection must contain at least one selection"
        }
        require(selections.map(TypedTupleBaseTableSelection::propertyIndex) == selections.indices.toList()) {
            "Typed tuple base-table selections must use contiguous zero-based indexes"
        }
    }
}

/** 单个 base-table 选择的表/表达式类别及 Java 表达式能力。 */
data class TypedTupleBaseTableSelection(
    val propertyIndex: Int,
    val kind: TypedTupleBaseTableSelectionKind,
    val entityTableTypeId: LsiSymbolId? = null,
    val scalarCategory: TypedTupleScalarCategory? = null,
) {
    init {
        require(propertyIndex >= 0) { "Typed tuple base-table property index cannot be negative" }
        require((entityTableTypeId != null) == kind.table) {
            "Typed tuple table selection must declare exactly one generated entity table type"
        }
        require((scalarCategory != null) == !kind.table) {
            "Typed tuple expression selection must declare exactly one scalar category"
        }
        entityTableTypeId?.requireTypeQualifiedName()
    }
}

/** base-table 选择的结构类别，与运行时 selection layout 一一对应。 */
enum class TypedTupleBaseTableSelectionKind(
    val table: Boolean,
    val nullable: Boolean,
) {
    NON_NULL_TABLE(true, false),
    NULLABLE_TABLE(true, true),
    NON_NULL_EXPRESSION(false, false),
    NULLABLE_EXPRESSION(false, true),
}

/** Java facade 为标量选择暴露的表达式能力。 */
enum class TypedTupleScalarCategory {
    GENERIC,
    STRING,
    NUMERIC,
    DATE,
    TEMPORAL,
    COMPARABLE,
}

/** TypedTuple 实例化时消费属性的稳定契约。 */
sealed interface TypedTupleConstruction {
    val constructorId: LsiSymbolId?
    val propertyIndexes: List<Int>
    val sourceMemberIds: List<LsiSymbolId>
}

/** Java 无参构造后通过 setter 赋值的契约。 */
data class TypedTupleJavaSetterConstruction(
    override val constructorId: LsiSymbolId?,
    val assignments: List<TypedTupleSetterAssignment>,
) : TypedTupleConstruction {
    override val propertyIndexes: List<Int>
        get() = assignments.map(TypedTupleSetterAssignment::propertyIndex)

    override val sourceMemberIds: List<LsiSymbolId>
        get() = assignments.map(TypedTupleSetterAssignment::sourceMemberId)
}

/** Java 按构造参数位置实例化的契约。 */
data class TypedTupleJavaConstructorConstruction(
    override val constructorId: LsiSymbolId?,
    val arguments: List<TypedTupleConstructorArgument>,
) : TypedTupleConstruction {
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

/** Kotlin 按主构造参数名实例化的契约。 */
data class TypedTupleKotlinConstructorConstruction(
    override val constructorId: LsiSymbolId,
    val arguments: List<TypedTupleConstructorArgument>,
) : TypedTupleConstruction {
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

/** Java setter 赋值与源字段的对应关系。 */
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

/** 构造参数与 TypedTuple 属性的对应关系。 */
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

/** TypedTuple 语义所依赖的全部类型与成员符号。 */
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
