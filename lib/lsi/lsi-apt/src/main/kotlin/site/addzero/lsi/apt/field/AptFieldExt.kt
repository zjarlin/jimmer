package site.addzero.lsi.apt.field

import site.addzero.lsi.apt.element.isField
import site.addzero.lsi.apt.element.isRecordComponent
import site.addzero.lsi.field.LsiField
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.util.Elements

fun VariableElement.isEnum(): Boolean {
  // 核心判断逻辑
  val fieldType = asType()
  val isEnum = fieldType.kind == TypeKind.DECLARED
    && (fieldType as DeclaredType).asElement().let { element ->
    element is TypeElement && element.kind == ElementKind.ENUM
  }
  return isEnum
}

/**
 * 获取VariableElement的文档注释
 * 需要传入Elements实例来获取文档注释
 */
fun VariableElement.getDocComment(elements: Elements): String? {
  val enclosingElement1 = this.enclosingElement
  val docComment = elements.getDocComment(this)
  return docComment
}

/**
 * 批量转换VariableElement列表
 */
fun Collection<VariableElement>.toLsiFields(elements: Elements): List<LsiField> {
  val map = map {
    val toLsiField = it.toLsiField(elements)
    toLsiField
  }
  return map
}

fun VariableElement.toLsiField(elements: Elements): LsiField {
  val aptLsiField = _root_ide_package_.site.addzero.lsi.apt.field.AptLsiField(elements, this)
  return aptLsiField
}

fun Element.toLsiFieldOrNull(elements: Elements): LsiField? =
  when {
    isField() && this is VariableElement -> this.toLsiField(elements)
    isRecordComponent() -> this.toRecordComponentLsiFieldOrNull(elements)
    else -> null
  }

private fun Element.toRecordComponentLsiFieldOrNull(elements: Elements): LsiField? {
  val accessor = recordComponentAccessor() ?: return null
  val enclosingType = enclosingElement as? TypeElement ?: return null
  return AptLsiRecordComponentField(
    elements = elements,
    recordComponentElement = this,
    accessor = accessor,
    declaringType = enclosingType,
  )
}

private fun Element.recordComponentAccessor(): ExecutableElement? {
  // 覆盖来源：project/jimmer-apt/.../client/ClientProcessor.fillDefinition 的 RECORD_COMPONENT -> accessor 反射桥接
  // 迁移说明：Java record component 的 accessor 解析统一下沉到 apt field 适配层，避免后续 shared helper 再重复直连平台反射
  val method = RECORD_COMPONENT_ELEMENT_GET_ACCESSOR ?: return null
  return runCatching { method.invoke(this) as? ExecutableElement }.getOrNull()
}

private val RECORD_COMPONENT_ELEMENT_GET_ACCESSOR by lazy {
  val recordComponentKindExists = javax.lang.model.element.ElementKind
    .values()
    .any { it.name == "RECORD_COMPONENT" }
  if (!recordComponentKindExists) {
    return@lazy null
  }
  runCatching {
    val recordComponentElementClass = Class.forName("javax.lang.model.element.RecordComponentElement")
    recordComponentElementClass.getMethod("getAccessor")
  }.getOrNull()
}

//fun RoundEnvironment.toKldResolver(processingEnv: ProcessingEnvironment): Unit
