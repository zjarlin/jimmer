package site.addzero.lsi.apt.anno

import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.apt.clazz.AptLsiClass
import site.addzero.lsi.apt.context.AptLsiContext
import site.addzero.lsi.apt.type.AptLsiType
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeMirror

class AptLsiAnnotation(private val annotationMirror: AnnotationMirror) : LsiAnnotation {

    private val elements
        get() = AptLsiContext.elements

    override val qualifiedName: String? by lazy {
        annotationMirror.annotationType.toString()
    }

    override val simpleName: String? by lazy {
        annotationMirror.annotationType.asElement().simpleName.toString()
    }

    override val attributes: Map<String, Any?> by lazy {
        annotationMirror.elementValues.entries.associate { (key, value) ->
            key.simpleName.toString() to extractValue(value)
        }
    }

    override fun getAttribute(name: String): Any? = attributes[name]

    override fun hasAttribute(name: String): Boolean = attributes.containsKey(name)

    override val annotations: List<LsiAnnotation> by lazy {
        annotationMirror.annotationType
            .asElement()
            .annotationMirrors
            .map(::AptLsiAnnotation)
    }

    private fun extractValue(value: AnnotationValue): Any? {
        val v = value.value
        return when (v) {
            is AnnotationMirror -> AptLsiAnnotation(v)
            is TypeMirror -> {
                val typeElement = (v as? DeclaredType)?.asElement() as? TypeElement
                if (typeElement != null) {
                    AptLsiClass(typeElement)
                } else {
                    AptLsiType(elements, v)
                }
            }
            is List<*> -> v.map { nested ->
                if (nested is AnnotationValue) {
                    extractValue(nested)
                } else {
                    nested
                }
            }
            else -> v
        }
    }
}

fun AnnotationMirror.toLsiAnnotation(): LsiAnnotation =
    _root_ide_package_.site.addzero.lsi.apt.anno.AptLsiAnnotation(this)

fun List<AnnotationMirror>.toLsiAnnotations(): List<LsiAnnotation> = map { it.toLsiAnnotation() }
