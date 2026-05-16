package site.addzero.lsi.apt.method

import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.method.LsiMethod
import site.addzero.lsi.method.LsiParameter
import site.addzero.lsi.type.LsiType
import site.addzero.lsi.apt.anno.methodComment
import site.addzero.lsi.apt.anno.toLsiAnnotations
import site.addzero.lsi.apt.clazz.AptLsiClass
import site.addzero.lsi.apt.type.AptLsiType
import site.addzero.util.str.firstNotBlank
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.util.Elements

class AptLsiMethod(
    private val elements: Elements,
    private val method: ExecutableElement
) : LsiMethod {

    override val name: String? by lazy {
        method.simpleName.toString()
    }

    override val returnType: LsiType? by lazy {
        _root_ide_package_.site.addzero.lsi.apt.type.AptLsiType(elements, method.returnType)
    }

    override val returnTypeName: String? by lazy {
        method.returnType.toString()
    }

    override val comment: String? by lazy {
        firstNotBlank(
            method.annotationMirrors.methodComment(),
            elements.getDocComment(method)
        )
    }

    override val annotations: List<LsiAnnotation> by lazy {
        // 覆盖来源：project/jimmer-apt/.../client/ClientProcessor.setNullityByJetBrainsAnnotation
        // 迁移说明：`LsiMethod.annotations` 统一承接方法本体 + returnType 两处注解语义，
        // 避免后续 shared helper 继续直连 `ExecutableElement.getReturnType().getAnnotationMirrors()`
        buildList {
            addAll(method.annotationMirrors.toLsiAnnotations())
            addAll(method.returnType.annotationMirrors.toLsiAnnotations())
        }
    }

    override val isStatic: Boolean by lazy {
        method.modifiers.contains(Modifier.STATIC)
    }

    override val isAbstract: Boolean by lazy {
        method.modifiers.contains(Modifier.ABSTRACT)
    }

    override val isPublic: Boolean by lazy {
        method.modifiers.contains(Modifier.PUBLIC)
    }

    override val isProtected: Boolean by lazy {
        method.modifiers.contains(Modifier.PROTECTED)
    }

    override val isInternal: Boolean
        get() = false

    override val isPrivate: Boolean by lazy {
        method.modifiers.contains(Modifier.PRIVATE)
    }

    override val isOpen: Boolean by lazy {
        !method.modifiers.contains(Modifier.PRIVATE) &&
            !method.modifiers.contains(Modifier.FINAL) &&
            !method.modifiers.contains(Modifier.STATIC) &&
            method.kind != ElementKind.CONSTRUCTOR
    }

    override val typeParameterCount: Int by lazy {
        method.typeParameters.size
    }

    override val isConstructor: Boolean
        get() = method.kind == ElementKind.CONSTRUCTOR

    override val parameters: List<LsiParameter> by lazy {
        method.parameters.map { _root_ide_package_.site.addzero.lsi.apt.method.AptLsiParameter(elements, it) }
    }

    override val thrownTypes: List<LsiType> by lazy {
        method.thrownTypes.map { AptLsiType(elements, it) }
    }

    override val declaringClass: LsiClass? by lazy {
        (method.enclosingElement as? TypeElement)?.let {
            _root_ide_package_.site.addzero.lsi.apt.clazz.AptLsiClass(
                elements,
                it
            )
        }
    }
}

class AptLsiParameter(private val elements: Elements, private val param: VariableElement) : LsiParameter {

    override val name: String? by lazy {
        param.simpleName.toString()
    }

    override val type: LsiType? by lazy {
        _root_ide_package_.site.addzero.lsi.apt.type.AptLsiType(elements, param.asType())
    }

    override val typeName: String? by lazy {
        param.asType().toString()
    }

    override val annotations: List<LsiAnnotation> by lazy {
        param.annotationMirrors.toLsiAnnotations()
    }

    override val isVararg: Boolean by lazy {
        val owner = param.enclosingElement as? ExecutableElement ?: return@lazy false
        owner.isVarArgs && owner.parameters.lastOrNull() == param
    }
}
