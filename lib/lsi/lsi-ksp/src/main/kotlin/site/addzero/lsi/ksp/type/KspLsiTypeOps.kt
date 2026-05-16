package site.addzero.lsi.ksp.type

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import site.addzero.lsi.type.LsiType

/**
 * KSP 平台下的 LsiType 关系扩展（不再引入 `LsiTypeOps` 对象）。
 */
fun LsiType?.isSameType(other: LsiType?): Boolean {
    val ksType1 = this.toKspTypeOrNull()
    val ksType2 = other.toKspTypeOrNull()
    if (ksType1 != null && ksType2 != null) {
        return ksType1 == ksType2
    }
    val left = this?.qualifiedName ?: this?.presentableText ?: this?.simpleName ?: return false
    val right = other?.qualifiedName ?: other?.presentableText ?: other?.simpleName ?: return false
    return left == right
}

fun LsiType?.isAssignableTo(target: LsiType?): Boolean {
    val sourceType = this.toKspTypeOrNull() ?: return false
    val targetType = target.toKspTypeOrNull() ?: return false
    return targetType.isAssignableFrom(sourceType)
}

fun LsiType?.isEnumType(): Boolean {
    val declaration = this.toKspTypeOrNull()?.declaration as? KSClassDeclaration ?: return false
    return declaration.classKind.name == "ENUM_CLASS"
}

fun LsiType?.isListStrictly(): Boolean {
    val qualifiedName = (this.toKspTypeOrNull()?.declaration as? KSClassDeclaration)
        ?.qualifiedName
        ?.asString()
        ?: return false
    return qualifiedName == "kotlin.collections.List" || qualifiedName == "java.util.List"
}

fun LsiType?.listElementTypeOrNull(): LsiType? {
    val ksType = this.toKspTypeOrNull() ?: return null
    if (!isListStrictly()) {
        return null
    }
    val first = ksType.arguments.firstOrNull()?.type?.resolve() ?: return null
    val resolver = (this as? KspLsiType)?.resolver ?: return null
    return KspLsiType(resolver, first)
}

private fun LsiType?.toKspTypeOrNull(): KSType? =
    (this as? KspLsiType)?.ksType
