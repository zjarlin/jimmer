package site.addzero.lsi.apt.type

import site.addzero.lsi.type.LsiType
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeMirror

/**
 * APT 平台下的 LsiType 关系扩展（不再引入 `LsiTypeOps` 对象）。
 */
fun LsiType?.isSameType(other: LsiType?): Boolean {
    val typeMirror1 = this.toAptTypeMirrorOrNull()
    val typeMirror2 = other.toAptTypeMirrorOrNull()
    if (typeMirror1 != null && typeMirror2 != null) {
        return typeMirror1.toString() == typeMirror2.toString()
    }
    val left = this?.qualifiedName ?: this?.presentableText ?: this?.simpleName ?: return false
    val right = other?.qualifiedName ?: other?.presentableText ?: other?.simpleName ?: return false
    return left == right
}

fun LsiType?.isAssignableTo(target: LsiType?): Boolean {
    val source = this.toAptTypeMirrorOrNull() as? DeclaredType ?: return false
    val targetMirror = target.toAptTypeMirrorOrNull() as? DeclaredType ?: return false
    return source.asElement() == targetMirror.asElement() || source.asElement().toString() == targetMirror.asElement().toString()
}

fun LsiType?.isEnumType(): Boolean {
    val typeMirror = this.toAptTypeMirrorOrNull() as? DeclaredType ?: return false
    val typeElement = typeMirror.asElement() as? TypeElement ?: return false
    return typeElement.kind == ElementKind.ENUM
}

fun LsiType?.isListStrictly(): Boolean {
    val typeMirror = this.toAptTypeMirrorOrNull() as? DeclaredType ?: return false
    val typeElement = typeMirror.asElement() as? TypeElement ?: return false
    return typeElement.qualifiedName.toString() == "java.util.List"
}

fun LsiType?.listElementTypeOrNull(): LsiType? {
    val typeMirror = this.toAptTypeMirrorOrNull() as? DeclaredType ?: return null
    if (!isListStrictly()) {
        return null
    }
    val first = typeMirror.typeArguments.firstOrNull() ?: return null
    val elements = (this as? AptLsiType)?.elements ?: return null
    return AptLsiType(elements, first)
}

private fun LsiType?.toAptTypeMirrorOrNull(): TypeMirror? =
    (this as? AptLsiType)?.typeMirror
