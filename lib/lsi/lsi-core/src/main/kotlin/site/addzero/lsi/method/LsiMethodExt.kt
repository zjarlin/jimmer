package site.addzero.lsi.method

import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.clazz.LsiClass
import kotlin.reflect.KClass

fun LsiMethod.annotation(annotationType: KClass<out Annotation>): LsiAnnotation? =
    annotation(annotationType.qualifiedName!!)

fun LsiMethod.annotation(qualifiedName: String): LsiAnnotation? =
    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor / LsiClientSchemaTraversal 方法注解判定
    // 覆盖来源：project/compiler/transactional/jimmer-ksp-transactional/.../TxGenerator 方法注解复制
    // 迁移说明：方法注解查询收敛到 lsi-core，后续 APT/KSP 共用，不再经由旧 `org.babyfish.jimmer.ksp.annotation`
    annotations.firstOrNull { it.qualifiedName == qualifiedName }

fun LsiParameter.annotation(annotationType: KClass<out Annotation>): LsiAnnotation? =
    annotation(annotationType.qualifiedName!!)

fun LsiParameter.annotation(qualifiedName: String): LsiAnnotation? =
    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../LsiClientSchemaTraversal parameter @ApiIgnore 判定
    // 迁移说明：参数注解查询迁移到 lsi-core，移除 client traversal 对旧 KSP 工具包的依赖
    annotations.firstOrNull { it.qualifiedName == qualifiedName }

/**
 * 检查方法是否具有指定的注解
 * @param annotationNames 注解全限定名数组
 * @return 如果方法具有其中任何一个注解，则返回true，否则返回false
 */
fun LsiMethod.hasAnnotation(vararg annotationNames: String): Boolean {
    return annotationNames.any { annotationName ->
        annotations.any { annotation ->
            annotation.qualifiedName == annotationName
        }
    }
}

val LsiMethod.isSuspend: Boolean
    get() = hasAnnotation("kotlin.coroutines.Suspend")

val LsiMethod.isComposable: Boolean
    get() = hasAnnotation("androidx.compose.runtime.Composable")

/**
 * 检查方法是否没有必需参数
 * @return 如果方法没有参数或所有参数都有默认值，则返回true，否则返回false
 */
val LsiMethod.hasNoRequiredParameters: Boolean
    get() = parameters.isEmpty() || parameters.all { it.hasDefault }

/**
 * 获取声明此方法的父类
 * @return 声明此方法的类，如果不存在则返回null
 */
val LsiMethod.parentClass: LsiClass?
    get() = declaringClass
