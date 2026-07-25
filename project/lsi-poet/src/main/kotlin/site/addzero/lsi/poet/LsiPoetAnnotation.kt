package site.addzero.lsi.poet

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationUseSiteTarget
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiTypeRef

/**
 * 描述源码注解及其确定性参数顺序。
 */
data class LsiPoetAnnotation(
    val type: LsiSymbolId,
    val arguments: List<LsiPoetAnnotationArgument> = emptyList(),
    val useSiteTarget: LsiAnnotationUseSiteTarget? = null,
    val argumentLayout: LsiPoetAnnotationArgumentLayout = LsiPoetAnnotationArgumentLayout.PLATFORM_DEFAULT,
) {
    init {
        val namedArguments = arguments.filterIsInstance<LsiPoetAnnotationArgument.Named>()
        require(namedArguments.map(LsiPoetAnnotationArgument.Named::name).distinct().size == namedArguments.size) {
            "LSI Poet annotation cannot declare duplicate named arguments: $type"
        }
        var namedArgumentObserved = false
        arguments.forEach { argument ->
            when (argument) {
                is LsiPoetAnnotationArgument.Named -> namedArgumentObserved = true
                is LsiPoetAnnotationArgument.Positional -> require(!namedArgumentObserved) {
                    "LSI Poet positional annotation arguments must precede named arguments: $type"
                }
            }
        }
    }
}

/** 请求注解参数采用平台默认、单行或多行布局；不支持的渲染边界必须失败。 */
enum class LsiPoetAnnotationArgumentLayout {
    PLATFORM_DEFAULT,
    SINGLE_LINE,
    MULTI_LINE,
}

/**
 * 区分源码注解的位置参数与命名参数。
 */
sealed interface LsiPoetAnnotationArgument {
    val value: LsiPoetAnnotationValue

    data class Positional(
        override val value: LsiPoetAnnotationValue,
    ) : LsiPoetAnnotationArgument

    data class Named(
        val name: String,
        override val value: LsiPoetAnnotationValue,
        val nameStyle: LsiPoetAnnotationArgumentNameStyle =
            LsiPoetAnnotationArgumentNameStyle.POET_IDENTIFIER,
    ) : LsiPoetAnnotationArgument {
        init {
            require(name.isNotBlank()) { "LSI Poet annotation argument name cannot be blank" }
        }
    }
}

/** 控制命名注解参数由 Poet 按标识符转义，或按已验证源码原样写出。 */
enum class LsiPoetAnnotationArgumentNameStyle {
    POET_IDENTIFIER,
    VERBATIM,
}

/**
 * 保留源码注解值的精确类型，并允许嵌套源码注解继续携带位置参数。
 */
sealed interface LsiPoetAnnotationValue {
    data class BooleanValue(val value: Boolean) : LsiPoetAnnotationValue

    data class ByteValue(val value: Byte) : LsiPoetAnnotationValue

    data class ShortValue(val value: Short) : LsiPoetAnnotationValue

    data class IntValue(val value: Int) : LsiPoetAnnotationValue

    data class LongValue(val value: Long) : LsiPoetAnnotationValue

    data class FloatValue(val value: Float) : LsiPoetAnnotationValue

    data class DoubleValue(val value: Double) : LsiPoetAnnotationValue

    data class CharValue(val value: Char) : LsiPoetAnnotationValue

    data class StringValue(val value: String) : LsiPoetAnnotationValue

    data class EnumValue(
        val enumType: LsiSymbolId,
        val entryName: String,
    ) : LsiPoetAnnotationValue {
        init {
            require(entryName.isNotBlank()) {
                "LSI Poet enum annotation value entry name cannot be blank"
            }
        }
    }

    data class ClassValue(
        val type: LsiTypeRef,
        val sourceStyle: LsiPoetClassLiteralStyle = LsiPoetClassLiteralStyle.PLATFORM_TYPE,
    ) : LsiPoetAnnotationValue

    data class NestedAnnotationValue(
        val annotation: LsiPoetAnnotation,
    ) : LsiPoetAnnotationValue

    data class ArrayValue(
        val elements: List<LsiPoetAnnotationValue>,
        val sourceStyle: LsiPoetAnnotationArrayStyle = LsiPoetAnnotationArrayStyle.LITERAL,
    ) : LsiPoetAnnotationValue
}

/** 控制类字面量使用 Poet 类型引用或 Java 装箱类型全限定源码。 */
enum class LsiPoetClassLiteralStyle {
    PLATFORM_TYPE,
    JAVA_BOXED_PRIMITIVE_QUALIFIED,
}

/**
 * 控制注解数组值的源码表示，避免在共享层保存具体 Poet 对象。
 */
enum class LsiPoetAnnotationArrayStyle {
    LITERAL,
    LINE_SEPARATED_LITERAL,
    MULTI_LINE_LITERAL,
    COMPACT_MULTI_LINE_LITERAL,
    KOTLIN_ARRAY_OF,
}

/**
 * 将冻结语义注解降低为确定性的源码注解。
 */
fun LsiAnnotation.toLsiPoetAnnotation(): LsiPoetAnnotation {
    val explicitArguments = arguments
        .asSequence()
        .filter { (_, argument) -> argument.isExplicit }
        .sortedBy { (name, _) -> name }
        .map { (name, argument) ->
            LsiPoetAnnotationArgument.Named(
                name = name,
                value = argument.value.toLsiPoetAnnotationValue(),
            )
        }
        .toList()
    return LsiPoetAnnotation(
        type = type,
        arguments = explicitArguments,
        useSiteTarget = useSiteTarget,
    )
}

private fun LsiAnnotationValue.toLsiPoetAnnotationValue(): LsiPoetAnnotationValue {
    return when (this) {
        is LsiAnnotationValue.BooleanValue -> LsiPoetAnnotationValue.BooleanValue(value)
        is LsiAnnotationValue.ByteValue -> LsiPoetAnnotationValue.ByteValue(value)
        is LsiAnnotationValue.ShortValue -> LsiPoetAnnotationValue.ShortValue(value)
        is LsiAnnotationValue.IntValue -> LsiPoetAnnotationValue.IntValue(value)
        is LsiAnnotationValue.LongValue -> LsiPoetAnnotationValue.LongValue(value)
        is LsiAnnotationValue.FloatValue -> LsiPoetAnnotationValue.FloatValue(value)
        is LsiAnnotationValue.DoubleValue -> LsiPoetAnnotationValue.DoubleValue(value)
        is LsiAnnotationValue.CharValue -> LsiPoetAnnotationValue.CharValue(value)
        is LsiAnnotationValue.StringValue -> LsiPoetAnnotationValue.StringValue(value)
        is LsiAnnotationValue.EnumValue -> LsiPoetAnnotationValue.EnumValue(enumType, entryName)
        is LsiAnnotationValue.ClassValue -> LsiPoetAnnotationValue.ClassValue(type)
        is LsiAnnotationValue.NestedAnnotationValue -> LsiPoetAnnotationValue.NestedAnnotationValue(
            annotation.toLsiPoetAnnotation()
        )
        is LsiAnnotationValue.ArrayValue -> LsiPoetAnnotationValue.ArrayValue(
            elements.map(LsiAnnotationValue::toLsiPoetAnnotationValue)
        )
    }
}
