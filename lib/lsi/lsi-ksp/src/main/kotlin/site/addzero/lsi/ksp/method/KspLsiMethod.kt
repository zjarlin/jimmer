package site.addzero.lsi.ksp.method

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.symbol.*
import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.anno.getClassListArgument
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.method.LsiMethod
import site.addzero.lsi.method.LsiParameter
import site.addzero.lsi.type.LsiType
import site.addzero.lsi.ksp.anno.KspLsiAnnotation
import site.addzero.lsi.ksp.clazz.KspLsiClass
import site.addzero.lsi.ksp.type.KspLsiType

class KspLsiMethod(
    internal val resolver: Resolver,
    internal val ksFunctionDeclaration: KSFunctionDeclaration
) : LsiMethod {

    override val name: String? by lazy {
        ksFunctionDeclaration.simpleName.asString()
    }

    override val returnType: LsiType? by lazy {
        ksFunctionDeclaration.returnType?.let {
            KspLsiType(resolver, it.resolve())
        }
    }

    override val returnTypeName: String? by lazy {
        ksFunctionDeclaration.returnType?.resolve()?.declaration?.simpleName?.asString()
    }

    override val comment: String? by lazy {
        ksFunctionDeclaration.docString
    }

    override val annotations: List<LsiAnnotation> by lazy {
        // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.setNullityByJetBrainsAnnotation
        // 迁移说明：`LsiMethod.annotations` 统一承接函数本体 + returnType 两处注解语义，
        // 避免后续 shared helper 继续直连 `KSFunctionDeclaration.returnType.annotations`
        buildList {
            addAll(
                ksFunctionDeclaration.annotations
                    .map { KspLsiAnnotation(it) { resolver } }
            )
            ksFunctionDeclaration.returnType
                ?.resolve()
                ?.annotations
                ?.mapTo(this) { KspLsiAnnotation(it) { resolver } }
        }
    }

    override val isStatic: Boolean by lazy {
        // Kotlin函数通常不是静态的，除非在companion object中
        val parent = ksFunctionDeclaration.parentDeclaration
        parent is KSClassDeclaration && parent.classKind == ClassKind.OBJECT
    }

    override val isAbstract: Boolean by lazy {
        ksFunctionDeclaration.modifiers.contains(Modifier.ABSTRACT)
    }

    override val isPublic: Boolean by lazy {
        ksFunctionDeclaration.isPublic()
    }

    override val isProtected: Boolean by lazy {
        ksFunctionDeclaration.modifiers.contains(Modifier.PROTECTED)
    }

    override val isInternal: Boolean by lazy {
        ksFunctionDeclaration.modifiers.contains(Modifier.INTERNAL)
    }

    override val isPrivate: Boolean by lazy {
        ksFunctionDeclaration.modifiers.contains(Modifier.PRIVATE)
    }

    override val isOpen: Boolean by lazy {
        ksFunctionDeclaration.modifiers.contains(Modifier.OPEN)
    }

    override val typeParameterCount: Int by lazy {
        ksFunctionDeclaration.typeParameters.size
    }

    override val isConstructor: Boolean by lazy {
        ksFunctionDeclaration.isConstructor()
    }

    override val parameters: List<LsiParameter> by lazy {
        ksFunctionDeclaration.parameters
            .map { KspLsiParameter(resolver, it) }
    }

    override val thrownTypes: List<LsiType> by lazy {
        ksFunctionDeclaration.annotations
            .map { KspLsiAnnotation(it) { resolver } }
            .firstOrNull { annotation ->
                annotation.qualifiedName == "kotlin.Throws" || annotation.qualifiedName == "kotlin.jvm.Throws"
            }
            ?.getClassListArgument("exceptionClasses")
            ?.mapNotNull { lsiClass ->
                val declaration = (lsiClass as? KspLsiClass)?.ksClassDeclaration ?: return@mapNotNull null
                KspLsiType(resolver, declaration.asStarProjectedType())
            }
            ?: emptyList()
    }

    override val declaringClass: LsiClass? by lazy {
        val parent = ksFunctionDeclaration.parentDeclaration
        if (parent is KSClassDeclaration) {
            KspLsiClass(resolver, parent)
        } else null
    }
}

class KspLsiParameter(
    private val resolver: Resolver,
    private val ksValueParameter: KSValueParameter
) : LsiParameter {

    override val name: String? by lazy {
        ksValueParameter.name?.asString()
    }

    override val type: LsiType? by lazy {
        KspLsiType(resolver, ksValueParameter.type.resolve())
    }

    override val typeName: String? by lazy {
        ksValueParameter.type.resolve().declaration.simpleName.asString()
    }

    override val annotations: List<LsiAnnotation> by lazy {
        ksValueParameter.annotations
            .map { KspLsiAnnotation(it) { resolver } }
            .toList()
    }

    override val hasDefault: Boolean by lazy {
        ksValueParameter.hasDefault
    }

    override val isVararg: Boolean by lazy {
        ksValueParameter.isVararg
    }
}

//fun KSFunctionDeclaration.toLsiMethod(resolver: Resolver): LsiMethod = KspLsiMethod(resolver, this)
