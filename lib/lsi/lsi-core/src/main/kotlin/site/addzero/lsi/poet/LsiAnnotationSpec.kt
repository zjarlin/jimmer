package site.addzero.lsi.poet

enum class LsiAnnotationUseSiteTarget {
    FIELD,
    GET,
    SET,
    PROPERTY,
    PARAM,
    SETPARAM,
    RECEIVER,
    DELEGATE,
    FILE,
    ALL,
}

sealed interface LsiAnnotationValue

object LsiNullAnnotationValue : LsiAnnotationValue

data class LsiStringAnnotationValue(val value: String) : LsiAnnotationValue

data class LsiLiteralAnnotationValue(val value: Any) : LsiAnnotationValue

data class LsiCharAnnotationValue(val value: Char) : LsiAnnotationValue

data class LsiEnumAnnotationValue(
    val enumType: LsiClassName,
    val constantName: String,
) : LsiAnnotationValue

data class LsiClassAnnotationValue(val className: LsiClassName) : LsiAnnotationValue

data class LsiTypeAnnotationValue(val typeName: LsiTypeName) : LsiAnnotationValue

data class LsiNestedAnnotationValue(val annotation: LsiAnnotationSpec) : LsiAnnotationValue

data class LsiArrayAnnotationValue(val elements: List<LsiAnnotationValue>) : LsiAnnotationValue

data class LsiRawAnnotationValue(val value: Any) : LsiAnnotationValue

data class LsiAnnotationSpec(
    val type: LsiClassName,
    val positionalArguments: List<LsiAnnotationValue> = emptyList(),
    val members: Map<String, LsiAnnotationValue> = emptyMap(),
    val useSiteTarget: LsiAnnotationUseSiteTarget? = null,
)
