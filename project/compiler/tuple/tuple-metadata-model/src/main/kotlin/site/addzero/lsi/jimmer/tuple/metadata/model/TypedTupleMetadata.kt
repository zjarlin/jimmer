package site.addzero.lsi.jimmer.tuple.metadata.model

import site.addzero.lsi.codegen.LsiClassName

/**
 * TypedTuple 类型元数据。
 *
 * 纯领域模型：
 * - 不暴露 `LsiClass` / `LsiField` / `LsiType`
 * - 不暴露 `KS*` / `TypeElement`
 * - 不暴露 `Context` / `LsiFiler`
 */
data class TypedTupleMetadata(
    val id: String,
    val sourceSimpleName: String,
    val sourceQualifiedName: String,
    val sourceClassName: LsiClassName,
    val packageName: String,
    val generatedSimpleName: String,
    val generatedQualifiedName: String,
    val generatedClassName: LsiClassName,
    val construction: TypedTupleConstructionMetadata,
    val properties: List<TypedTuplePropertyMetadata>,
)

sealed interface TypedTupleConstructionMetadata

data class TypedTupleConstructorConstructionMetadata(
    val argumentPropertyIndices: List<Int>,
) : TypedTupleConstructionMetadata

data class TypedTupleSetterConstructionMetadata(
    val setterNames: List<String>,
) : TypedTupleConstructionMetadata

data class TypedTuplePropertyMetadata(
    val id: String,
    val ownerTypeId: String,
    val name: String,
    val type: TypedTupleTypeRefMetadata?,
)

data class TypedTupleTypeRefMetadata(
    val qualifiedName: String?,
    val simpleName: String?,
    val presentableText: String?,
    val nullable: Boolean,
    val primitive: Boolean,
    val array: Boolean,
    val typeArguments: List<TypedTupleTypeRefMetadata>,
    val componentType: TypedTupleTypeRefMetadata?,
)
