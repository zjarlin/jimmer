package site.addzero.lsi.jimmer.dto

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeRef

/** 按 DTO 声明顺序返回枚举常量到映射值的索引。 */
fun DtoEnumType.mappingsByConstant(): Map<String, String> {
    return mappings.associateTo(linkedMapOf()) { mapping ->
        mapping.constant to mapping.value
    }
}

/** 按 DTO 声明顺序返回映射值到枚举常量的索引。 */
fun DtoEnumType.mappingsByValue(): Map<String, String> {
    return mappings.associateTo(linkedMapOf()) { mapping ->
        mapping.value to mapping.constant
    }
}

/** 返回枚举映射在目标源码语言中暴露的标量类型。 */
fun DtoEnumType.scalarType(targetLanguage: LsiLanguage): LsiTypeRef {
    val language = targetLanguage.requireDtoTargetLanguage()
    if (numeric) {
        return LsiPrimitiveType(LsiPrimitiveKind.INT)
    }
    return LsiDeclaredType(language.dtoEnumStringTypeId())
}

/** 返回 DTO 属性尾部不可变枚举的稳定声明类型。 */
fun DtoBaseProp.enumTypeRef(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
): LsiDeclaredType {
    val enumTypeId = enumTypeId(graph, immutableSchema)
    return LsiDeclaredType(enumTypeId)
}

/** 返回 DTO 属性尾部不可变枚举的稳定符号 ID。 */
fun DtoBaseProp.enumTypeId(
    graph: DtoGraph,
    immutableSchema: ImmutableSchema,
): LsiSymbolId {
    require(enumType != null) {
        "DTO property does not declare an enum mapping: ${id.value}"
    }
    val tailProp = tailProp(graph)
    val enumTypeIds = tailProp.baseProps.map { binding ->
        val immutableProp = requireNotNull(immutableSchema.propsById[binding.propId]) {
            "DTO enum property references a missing immutable property: ${binding.propId.value}"
        }
        val declaredType = immutableProp.type as? LsiDeclaredType
            ?: error("DTO enum property must reference a declared immutable type: ${immutableProp.id.value}")
        declaredType.declarationId
    }.distinct()
    require(enumTypeIds.size == 1) {
        "DTO enum property bindings must reference one enum type: ${id.value}"
    }
    return enumTypeIds.single()
}

private fun LsiLanguage.dtoEnumStringTypeId(): LsiSymbolId {
    return when (this) {
        LsiLanguage.JAVA -> LsiSymbolId.type("java.lang.String")
        LsiLanguage.KOTLIN -> LsiSymbolId.type("kotlin.String")
        LsiLanguage.UNKNOWN -> error("DTO target language must be Java or Kotlin")
    }
}
