package site.addzero.lsi.jimmer.dto

import site.addzero.lsi.core.LsiSymbolId

/** DTO 在 Kotlin 生成端采用的最终可变性。 */
enum class DtoKotlinMutability {
    IMMUTABLE,
    MUTABLE,
}

/** 按类型声明覆盖默认策略，解析 DTO 的最终 Kotlin 可变性。 */
fun DtoType.effectiveKotlinMutability(
    default: DtoKotlinMutability,
): DtoKotlinMutability {
    val annotations = annotations.filter { annotation ->
        annotation.typeId == KOTLIN_DTO_ANNOTATION_TYPE_ID
    }
    if (annotations.isEmpty()) {
        return default
    }
    require(annotations.size == 1) {
        "DTO root type cannot declare KotlinDto more than once: ${id.value}"
    }
    val immutabilityArgument = annotations.single().arguments.singleOrNull { argument ->
        argument.name == KOTLIN_DTO_IMMUTABILITY_ARGUMENT
    } ?: error("DTO KotlinDto annotation requires immutability: ${id.value}")
    val immutability = immutabilityArgument.value as? DtoAnnotationValue.EnumValue
        ?: error("DTO KotlinDto immutability must be an enum value: ${id.value}")
    require(immutability.enumTypeId == KOTLIN_DTO_IMMUTABILITY_TYPE_ID) {
        "DTO KotlinDto immutability must use ${KOTLIN_DTO_IMMUTABILITY_TYPE_ID.value}: ${id.value}"
    }
    return when (immutability.constant) {
        "AUTO" -> default
        "IMMUTABLE" -> DtoKotlinMutability.IMMUTABLE
        "MUTABLE" -> DtoKotlinMutability.MUTABLE
        else -> error(
            "Unsupported DTO KotlinDto immutability '${immutability.constant}': ${id.value}",
        )
    }
}

/** 为全部根 DTO 类型生成按稳定标识排序的 Kotlin 可变性计划。 */
fun DtoGraph.effectiveKotlinMutabilityByRootTypeId(
    default: DtoKotlinMutability,
): Map<DtoTypeId, DtoKotlinMutability> {
    return rootTypeIds
        .sorted()
        .associateWith { rootTypeId ->
            typesById.getValue(rootTypeId).effectiveKotlinMutability(default)
        }
}

private const val KOTLIN_DTO_IMMUTABILITY_ARGUMENT = "immutability"
private val KOTLIN_DTO_ANNOTATION_TYPE_ID =
    LsiSymbolId.type("org.babyfish.jimmer.kt.dto.KotlinDto")
private val KOTLIN_DTO_IMMUTABILITY_TYPE_ID =
    LsiSymbolId.type("org.babyfish.jimmer.kt.dto.KotlinDtoImmutability")
