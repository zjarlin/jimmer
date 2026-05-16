package site.addzero.lsi.apt.resolver

import site.addzero.lsi.apt.clazz.toLsiClass
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.resolver.LsiResolver
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.TypeElement
import javax.lang.model.util.Elements

class AptLsiResolver(
    private val roundEnvironment: RoundEnvironment,
    private val elements: Elements
) : LsiResolver {

    // 覆盖来源：APT 各 processor 中基于 roundEnv.getRootElements() 的全量遍历入口
    override fun allClasses(): Sequence<LsiClass> =
        roundEnvironment
            .rootElements
            .asSequence()
            .filterIsInstance<TypeElement>()
            .map { it.toLsiClass(elements) }

    // 覆盖来源：APT 没有 strict new-files 语义，当前退化为 allClasses()
    override fun newClasses(): Sequence<LsiClass> =
        allClasses()

    // 覆盖来源：TxProcessor / TypedTupleProcessor / ImmutableProcessor 的 getElementsAnnotatedWith(...)
    override fun findClassesAnnotatedWith(annotationQualifiedName: String): Sequence<LsiClass> {
        val annotationType = elements.getTypeElement(annotationQualifiedName) ?: return emptySequence()
        return roundEnvironment
            .getElementsAnnotatedWith(annotationType)
            .asSequence()
            .filterIsInstance<TypeElement>()
            .map { it.toLsiClass(elements) }
    }

    // 覆盖来源：DtoProcessor / ClientProcessor 的 elements.getTypeElement(...)
    override fun findClassByQualifiedName(qualifiedName: String): LsiClass? =
        elements.getTypeElement(qualifiedName)?.toLsiClass(elements)
}
