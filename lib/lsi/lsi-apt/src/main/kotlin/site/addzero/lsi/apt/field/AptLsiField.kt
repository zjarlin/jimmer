package site.addzero.lsi.apt.field

import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.apt.anno.fieldComment
import site.addzero.lsi.apt.anno.toLsiAnnotations
import site.addzero.lsi.apt.element.getDocComment
import site.addzero.lsi.assist.getColumnName
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.field.LsiField
import site.addzero.lsi.type.LsiType
import site.addzero.util.str.toUnderLineCase
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.util.Elements

class AptLsiField(
  private val elements: Elements,
  private val field: VariableElement,
) : LsiField {

  override val name: String? by lazy {
    field.simpleName.toString()
  }

  override val type: LsiType? by lazy {
    val aptLsiType = _root_ide_package_.site.addzero.lsi.apt.type.AptLsiType(elements, field.asType())
    aptLsiType
  }

  override val typeName: String? by lazy {
    val toString = field.asType().toString()
    toString
  }

  override val comment: String? by lazy {
    val annotationMirrors = field.annotationMirrors
    val fieldComment = annotationMirrors.fieldComment()
    val docComment = field.getDocComment(elements)
    val string = fieldComment ?: docComment
    string
  }
  override val annotations: List<LsiAnnotation> by lazy {
    field.annotationMirrors.toLsiAnnotations()
  }

  override val isStatic: Boolean by lazy {
    field.modifiers.contains(Modifier.STATIC)
  }

  override val isPublic: Boolean by lazy {
    field.modifiers.contains(Modifier.PUBLIC)
  }

  override val isPrivate: Boolean by lazy {
    field.modifiers.contains(Modifier.PRIVATE)
  }

  override val isConstant: Boolean by lazy {
    field.modifiers.contains(Modifier.STATIC) && field.modifiers.contains(Modifier.FINAL)
  }
  override val isEnum get() = field.isEnum()

  override val isVar: Boolean by lazy {
    !field.modifiers.contains(Modifier.FINAL)
  }

  override val isLateInit: Boolean
    get() = false

  override val isCollectionType: Boolean by lazy {
    type?.isCollectionType ?: false
  }

  override val defaultValue: String? by lazy {
    field.constantValue?.toString()
  }

  override val columnName: String? by lazy {
    val simpleName = field.simpleName.toString()
    val string = annotations.getColumnName() ?: simpleName
    string.toUnderLineCase()
  }

  override val declaringClass: LsiClass? by lazy {
    (field.enclosingElement as? TypeElement)?.let {
      _root_ide_package_.site.addzero.lsi.apt.clazz.AptLsiClass(
        elements,
        it
      )
    }
  }

  override val fieldTypeClass: LsiClass? by lazy {
    val typeMirror = field.asType()
    if (typeMirror is DeclaredType) {
      val element = typeMirror.asElement()
      if (element is TypeElement) _root_ide_package_.site.addzero.lsi.apt.clazz.AptLsiClass(
        elements,
        element
      ) else null
    } else null
  }

  override val isNestedObject: Boolean by lazy {
    !isCollectionType && fieldTypeClass?.isPojo == true
  }

  override val children: List<LsiField> by lazy {
    if (isNestedObject) fieldTypeClass?.fields ?: emptyList() else emptyList()
  }
}

internal class AptLsiRecordComponentField(
  private val elements: Elements,
  private val recordComponentElement: Element,
  private val accessor: ExecutableElement,
  private val declaringType: TypeElement,
) : LsiField {

  override val name: String? by lazy {
    recordComponentElement.simpleName.toString()
  }

  override val type: LsiType? by lazy {
    _root_ide_package_.site.addzero.lsi.apt.type.AptLsiType(elements, recordComponentElement.asType())
  }

  override val typeName: String? by lazy {
    recordComponentElement.asType().toString()
  }

  override val comment: String? by lazy {
    fieldComment(
      recordComponentElement.annotationMirrors.toLsiAnnotations(),
      recordComponentElement.getDocComment(elements),
      accessor.getDocComment(elements)
    )
  }

  override val annotations: List<LsiAnnotation> by lazy {
    // 覆盖来源：project/jimmer-apt/.../client/ClientProcessor.fillDefinition 的 record component / accessor 双落点读取
    // 迁移说明：Java record component 的属性注解统一承接 component + accessor + returnType 三处语义，
    // 对齐 `LsiField.annotations` 在 KSP 侧“属性多落点合并”的设计
    buildList {
      addAll(recordComponentElement.annotationMirrors.toLsiAnnotations())
      addAll(accessor.annotationMirrors.toLsiAnnotations())
      addAll(accessor.returnType.annotationMirrors.toLsiAnnotations())
    }
  }

  override val isStatic: Boolean
    get() = false

  override val isPublic: Boolean
    get() = true

  override val isPrivate: Boolean
    get() = false

  override val isConstant: Boolean
    get() = false

  override val isEnum: Boolean
    get() = false

  override val isVar: Boolean
    get() = false

  override val isLateInit: Boolean
    get() = false

  override val isCollectionType: Boolean by lazy {
    type?.isCollectionType ?: false
  }

  override val defaultValue: String?
    get() = null

  override val columnName: String? by lazy {
    val simpleName = name ?: return@lazy null
    val value = annotations.getColumnName() ?: simpleName
    value.toUnderLineCase()
  }

  override val declaringClass: LsiClass by lazy {
    _root_ide_package_.site.addzero.lsi.apt.clazz.AptLsiClass(elements, declaringType)
  }

  override val fieldTypeClass: LsiClass? by lazy {
    val typeMirror = recordComponentElement.asType()
    if (typeMirror is DeclaredType) {
      val element = typeMirror.asElement()
      if (element is TypeElement) {
        _root_ide_package_.site.addzero.lsi.apt.clazz.AptLsiClass(elements, element)
      } else {
        null
      }
    } else {
      null
    }
  }

  override val isNestedObject: Boolean by lazy {
    !isCollectionType && fieldTypeClass?.isPojo == true
  }

  override val children: List<LsiField> by lazy {
    if (isNestedObject) fieldTypeClass?.fields ?: emptyList() else emptyList()
  }

  private fun fieldComment(
    annotations: List<LsiAnnotation>,
    componentDoc: String?,
    accessorDoc: String?,
  ): String? {
    val annotationDoc = annotations.firstNotNullOfOrNull { annotation ->
      when (annotation.qualifiedName) {
        "org.babyfish.jimmer.client.Description" -> annotation.getAttribute("value") as? String
        else -> null
      }
    }
    return annotationDoc ?: componentDoc ?: accessorDoc
  }
}
