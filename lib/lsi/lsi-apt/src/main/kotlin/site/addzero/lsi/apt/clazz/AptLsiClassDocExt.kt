@file:JvmName("AptLsiClassDocMetadata")

package site.addzero.lsi.apt.clazz

import site.addzero.lsi.apt.context.AptLsiContext
import site.addzero.lsi.clazz.LsiClass
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeKind

/**
 * 覆盖来源：project/jimmer-apt/.../client/DraftDocMetadataSupport
 * 迁移说明：APT Draft/Producer/Impl 文档扫描下沉到 lsi-apt 边界，shared DocMetadata 只通过 callback 消费结果。
 */
fun LsiClass.findAptDraftImplDocMap(
    annotationQualifiedName: String,
    valueAttributeName: String = "value",
): Map<String, String> {
    val qualifiedName = qualifiedName
        ?.takeIf { it.isNotEmpty() }
        ?: return emptyMap()
    var implElement = AptLsiContext.elements
        .getTypeElement("${qualifiedName}Draft")
        ?: return emptyMap()
    implElement = implElement.nestedType("Producer") ?: return emptyMap()
    implElement = implElement.nestedType("Impl") ?: return emptyMap()

    val map = linkedMapOf<String, String>()
    implElement.annotationValue(annotationQualifiedName, valueAttributeName)
        ?.takeIf { it.isNotEmpty() }
        ?.let { map[""] = it }
    for (element in implElement.enclosedElements) {
        val executableElement = element as? ExecutableElement ?: continue
        if (executableElement.parameters.isNotEmpty() ||
            executableElement.typeParameters.isNotEmpty() ||
            executableElement.returnType.kind == TypeKind.VOID
        ) {
            continue
        }
        val doc = executableElement.annotationValue(annotationQualifiedName, valueAttributeName)
            ?.takeIf { it.isNotEmpty() }
            ?: continue
        val propName = propNameOf(
            executableElement.simpleName.toString(),
            executableElement.returnType.kind == TypeKind.BOOLEAN,
        ) ?: executableElement.simpleName.toString()
        map[propName] = doc
    }
    return map
}

private fun TypeElement.nestedType(simpleName: String): TypeElement? =
    enclosedElements.firstNotNullOfOrNull { element ->
        val typeElement = element as? TypeElement ?: return@firstNotNullOfOrNull null
        typeElement.takeIf { simpleName.contentEquals(it.simpleName) }
    }

private fun Element.annotationValue(
    annotationQualifiedName: String,
    valueAttributeName: String,
): String? {
    val annotationMirror = annotationMirror(annotationQualifiedName) ?: return null
    for ((key, value) in annotationMirror.elementValues) {
        if (key.simpleName.contentEquals(valueAttributeName)) {
            return (value.value as? String)
        }
    }
    return null
}

private fun Element.annotationMirror(annotationQualifiedName: String): AnnotationMirror? =
    annotationMirrors.firstOrNull { annotationMirror ->
        val typeElement = annotationMirror.annotationType.asElement() as? TypeElement ?: return@firstOrNull false
        annotationQualifiedName == typeElement.qualifiedName.toString()
    }

private fun propNameOf(methodName: String, isBoolean: Boolean): String? {
    if (methodName.length > 3 &&
        methodName.startsWith("get") &&
        !methodName[3].isLowerCase()
    ) {
        return identifierOf(methodName.substring(3))
    }
    if (isBoolean &&
        methodName.length > 2 &&
        methodName.startsWith("is") &&
        !methodName[2].isLowerCase()
    ) {
        return identifierOf(methodName.substring(2))
    }
    return null
}

private fun identifierOf(text: String): String {
    val builder = StringBuilder()
    val chars = text.toCharArray()
    for (index in chars.indices) {
        val char = chars[index]
        if (char.isLowerCase()) {
            builder.append(chars, index, chars.size - index)
            break
        }
        builder.append(char.lowercaseChar())
        if (index == chars.lastIndex) {
            break
        }
        if (chars[index + 1].isLowerCase()) {
            if (index + 1 <= chars.lastIndex) {
                builder.append(chars, index + 1, chars.size - index - 1)
            }
            break
        }
        if (index == chars.lastIndex - 1) {
            builder.append(chars[index + 1].lowercaseChar())
            break
        }
    }
    if (builder.isEmpty()) {
        for ((index, char) in text.withIndex()) {
            if (index == 0) {
                builder.append(char.lowercaseChar())
            } else {
                builder.append(char)
            }
        }
    }
    return builder.toString()
}
