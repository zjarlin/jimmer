package site.addzero.lsi.codegen

import site.addzero.lsi.poet.LsiAnnotationSpec
import site.addzero.lsi.poet.LsiClassAnnotationValue
import site.addzero.lsi.poet.LsiClassName
import site.addzero.lsi.poet.LsiStringAnnotationValue

private val GENERATED_BY_CLASS_NAME = LsiClassName.bestGuess("org.babyfish.jimmer.internal.GeneratedBy")

/**
 * 覆盖来源：project/compiler/immutable|dto|error/... 对 `org.babyfish.jimmer.ksp.util.generatedAnnotation()` 的调用
 * 迁移说明：`@GeneratedBy` 装配属于通用 Jimmer codegen 能力，迁移到 `site.addzero.lsi.codegen`，避免业务模块继续依赖 `org.babyfish.jimmer.ksp.util`
 */
fun generatedAnnotation(): LsiAnnotationSpec =
    LsiAnnotationSpec(type = GENERATED_BY_CLASS_NAME)

/**
 * 覆盖来源：project/compiler/immutable|dto|error/... 对 `org.babyfish.jimmer.ksp.util.generatedAnnotation(ClassName)` 的调用
 * 迁移说明：按类型装配 `@GeneratedBy(type = ...)` 改为复用中立 codegen 包，脱离 KSP 命名空间
 */
fun generatedAnnotation(className: LsiClassName): LsiAnnotationSpec =
    LsiAnnotationSpec(
        type = GENERATED_BY_CLASS_NAME,
        members = mapOf(
            "type" to LsiClassAnnotationValue(className.copyNullable(false))
        )
    )

/**
 * 覆盖来源：project/compiler/immutable|transactional/... 对 `org.babyfish.jimmer.ksp.util.suppressAllAnnotation()` 的调用
 * 迁移说明：`@Suppress("warnings")` 装配是通用 KotlinPoet helper，迁移到中立 codegen 包，脱离旧 KSP util 包名
 */
fun suppressAllAnnotation(): LsiAnnotationSpec =
    LsiAnnotationSpec(
        type = LsiClassName.bestGuess(Suppress::class.qualifiedName!!),
        positionalArguments = listOf(LsiStringAnnotationValue("warnings"))
    )

fun suppressWarningsAllAnnotation(): LsiAnnotationSpec =
    LsiAnnotationSpec(
        type = JAVA_SUPPRESS_WARNINGS_LSI_CLASS_NAME,
        positionalArguments = listOf(LsiStringAnnotationValue("all"))
    )
