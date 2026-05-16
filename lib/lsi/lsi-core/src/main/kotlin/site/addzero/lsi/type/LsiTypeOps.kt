package site.addzero.lsi.type

/**
 * 语言无关的类型关系扩展。
 *
 * 说明：
 * - 不再通过 `LsiTypeOps` 对象承载能力；
 * - 默认实现采用保守策略（按规范化类型名比较）；
 * - 平台模块（APT/KSP）可在各自包下提供更精确的同名扩展并在调用处显式导入。
 */

fun LsiType?.isSameType(other: LsiType?): Boolean {
    val left = this?.normalizedTypeName() ?: return false
    val right = other?.normalizedTypeName() ?: return false
    return left == right
}

fun LsiType?.isAssignableTo(target: LsiType?): Boolean =
    isSameType(target)

fun LsiType?.isSubtypeOf(superType: LsiType?): Boolean =
    isAssignableTo(superType)

fun LsiType?.isEnumType(): Boolean =
    this?.lsiClass?.isEnum == true

fun LsiType?.isListStrictly(): Boolean {
    val name = this?.normalizedTypeName() ?: return false
    return name == "java.util.List" || name == "kotlin.collections.List"
}

fun LsiType?.listElementTypeOrNull(): LsiType? =
    if (isListStrictly()) this?.typeParameters?.firstOrNull() else null

private fun LsiType.normalizedTypeName(): String? =
    qualifiedName
        ?.substringBefore('<')
        ?.removeSuffix("?")
        ?.removeSuffix("!")
        ?.trim()
