package site.addzero.lsi.apt.clazz

import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.apt.anno.toLsiAnnotations
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.clazz.LsiEnumConstant
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.util.Elements

class AptLsiEnumConstant(
    private val elements: Elements,
    private val variableElement: VariableElement
) : LsiEnumConstant {

    override val name: String? by lazy {
        variableElement.simpleName.toString()
    }

    override val comment: String? by lazy {
        elements.getDocComment(variableElement)
    }

    override val annotations: List<LsiAnnotation> by lazy {
        variableElement.annotationMirrors.toLsiAnnotations()
    }

    override val declaringClass: LsiClass? by lazy {
        (variableElement.enclosingElement as? TypeElement)?.let {
            AptLsiClass(elements, it)
        }
    }
}
