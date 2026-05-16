package site.addzero.lsi.ksp.clazz

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.clazz.LsiEnumConstant
import site.addzero.lsi.ksp.anno.KspLsiAnnotation

class KspLsiEnumConstant(
    private val resolver: Resolver,
    internal val ksClassDeclaration: KSClassDeclaration
) : LsiEnumConstant {

    override val name: String? by lazy {
        ksClassDeclaration.simpleName.asString()
    }

    override val comment: String? by lazy {
        ksClassDeclaration.docString
    }

    override val annotations: List<LsiAnnotation> by lazy {
        ksClassDeclaration.annotations
            .map { KspLsiAnnotation(it) { resolver } }
            .toList()
    }

    override val declaringClass: LsiClass? by lazy {
        (ksClassDeclaration.parentDeclaration as? KSClassDeclaration)?.let {
            KspLsiClass(resolver, it)
        }
    }
}
