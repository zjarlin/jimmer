package site.addzero.lsi.ksp.anno

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.ksp.clazz.KspLsiClass
import site.addzero.lsi.ksp.context.KspLsiContext
import site.addzero.lsi.ksp.type.KspLsiType
import site.addzero.lsi.poet.LsiAnnotationUseSiteTarget

class KspLsiAnnotation(
  internal val ksAnnotation: KSAnnotation,
  private val resolverProvider: (() -> Resolver)? = null,
) : LsiAnnotation {

  override val qualifiedName: String? by lazy {
    ksAnnotation.annotationType.resolve().declaration.qualifiedName?.asString()
  }

  override val simpleName: String? by lazy {
    ksAnnotation.annotationType.resolve().declaration.simpleName.asString()
  }

  override val attributes: Map<String, Any?> by lazy {
    ksAnnotation.arguments.associate { argument ->
      val argument1 = argument
      (argument1.name?.asString() ?: "") to convertAttributeValue(argument1.value)
    }
  }

  override fun getAttribute(name: String): Any? {
    return attributes[name]
  }

  override fun hasAttribute(name: String): Boolean {
    return attributes.containsKey(name)
  }

  override val useSiteTarget: LsiAnnotationUseSiteTarget? by lazy {
    ksAnnotation.useSiteTarget
      ?.name
      ?.let { runCatching { LsiAnnotationUseSiteTarget.valueOf(it) }.getOrNull() }
  }

  override val annotations: List<LsiAnnotation> by lazy {
    ksAnnotation.annotationType.annotations
      .map { KspLsiAnnotation(it, resolverProvider) }
      .toList()
  }

  private fun convertAttributeValue(value: Any?): Any? =
    when (value) {
      is KSAnnotation ->
        KspLsiAnnotation(value, resolverProvider)

      is KSClassDeclaration ->
        resolverOrNull?.let { KspLsiClass(it, value) } ?: value

      is KSType ->
        convertKSType(value)

      is KSTypeReference ->
        convertKSType(value.resolve())

      is List<*> ->
        value.map { convertAttributeValue(it) }

      else -> value
    }

  private fun convertKSType(type: KSType): Any? {
    val resolver = resolverOrNull ?: return type
    val declaration = type.declaration
    return if (declaration is KSClassDeclaration) {
      KspLsiClass(resolver, declaration)
    } else {
      KspLsiType(resolver, type)
    }
  }

  private val resolverOrNull: Resolver?
    get() = resolverProvider?.invoke()
      ?: runCatching { KspLsiContext.resolver }.getOrNull()
}

fun KSAnnotation.toLsiAnnotation(): LsiAnnotation = KspLsiAnnotation(this)

fun KSAnnotation.toLsiAnnotation(resolver: Resolver): LsiAnnotation =
  KspLsiAnnotation(this) { resolver }
