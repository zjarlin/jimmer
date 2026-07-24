package site.addzero.lsi.model

import site.addzero.lsi.core.LsiSymbolId

enum class LsiAnnotationUseSiteTarget {
    PACKAGE,
    FILE,
    TYPE,
    CONSTRUCTOR,
    METHOD,
    RETURN_TYPE,
    PROPERTY,
    FIELD,
    GETTER,
    SETTER,
    RECEIVER,
    PARAMETER,
    SET_PARAMETER,
    DELEGATE,
    ALL
}

enum class LsiAnnotationArgumentOrigin {
    EXPLICIT,
    DEFAULT
}

data class LsiAnnotationArgument(
    val value: LsiAnnotationValue,
    val origin: LsiAnnotationArgumentOrigin
) {
    val isExplicit: Boolean
        get() = origin == LsiAnnotationArgumentOrigin.EXPLICIT
}

/**
 * 保留注解参数的精确类型，避免共享层依赖前端常量对象。
 */
sealed interface LsiAnnotationValue {

    data class BooleanValue(val value: Boolean) : LsiAnnotationValue

    data class ByteValue(val value: Byte) : LsiAnnotationValue

    data class ShortValue(val value: Short) : LsiAnnotationValue

    data class IntValue(val value: Int) : LsiAnnotationValue

    data class LongValue(val value: Long) : LsiAnnotationValue

    data class FloatValue(val value: Float) : LsiAnnotationValue

    data class DoubleValue(val value: Double) : LsiAnnotationValue

    data class CharValue(val value: Char) : LsiAnnotationValue

    data class StringValue(val value: String) : LsiAnnotationValue

    data class EnumValue(
        val enumType: LsiSymbolId,
        val entryName: String
    ) : LsiAnnotationValue {

        init {
            require(entryName.isNotBlank()) { "LSI enum annotation value entry name cannot be blank" }
        }
    }

    data class ClassValue(val type: LsiTypeRef) : LsiAnnotationValue

    data class NestedAnnotationValue(val annotation: LsiAnnotation) : LsiAnnotationValue

    data class ArrayValue(val elements: List<LsiAnnotationValue>) : LsiAnnotationValue
}

data class LsiAnnotation(
    val type: LsiSymbolId,
    val arguments: Map<String, LsiAnnotationArgument> = emptyMap(),
    val useSiteTarget: LsiAnnotationUseSiteTarget? = null,
    val explicitArgumentNamesInSourceOrder: List<String> = emptyList(),
) {

    init {
        require(arguments.keys.none(String::isBlank)) { "LSI annotation argument name cannot be blank" }
        require(explicitArgumentNamesInSourceOrder.distinct().size == explicitArgumentNamesInSourceOrder.size) {
            "LSI annotation explicit argument order cannot contain duplicate names: ${type.value}"
        }
        require(
            explicitArgumentNamesInSourceOrder.isEmpty() ||
                explicitArgumentNamesInSourceOrder.toSet() == arguments
                    .filterValues(LsiAnnotationArgument::isExplicit)
                    .keys
        ) {
            "LSI annotation explicit argument order must contain every explicit argument: ${type.value}"
        }
    }

    operator fun get(name: String): LsiAnnotationArgument? = arguments[name]
}
