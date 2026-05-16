package site.addzero.lsi.codegen

import site.addzero.lsi.diagnostic.MetaException
import site.addzero.lsi.jimmer.meta.ImmutableProp
import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.anno.fullName
import site.addzero.lsi.anno.get
import site.addzero.lsi.anno.getClassListArgument
import site.addzero.lsi.anno.getEnumListArgument
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.field.LsiField
import site.addzero.lsi.method.LsiMethod
import site.addzero.lsi.poet.LsiAnnotationSpec
import site.addzero.lsi.poet.toLsiPoet
import kotlin.math.abs

/**
 * 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/generator/utils.kt
 * 迁移说明：这组校验消息解析与 KotlinPoet/Jimmer 辅助函数不依赖 KSP 符号，迁移到中立 `site.addzero.lsi.codegen` 包，减少 `org.babyfish.jimmer.ksp` 命名空间泄漏。
 */

fun regexpPatternFieldName(prop: ImmutableProp, index: Int): String =
    regexpPatternFieldName(prop.name, index)

fun regexpPatternFieldName(propName: String, index: Int): String =
    "__" + upper(propName) + "_PATTERN" + if (index == 0) "" else "_$index"

fun validatorFieldName(annotationType: LsiClassName): String =
    "__" + "_VALIDATOR_" + "_" + upper(annotationType.simpleName) + "_" + abs(annotationType.hashCode())

fun validatorFieldName(prop: ImmutableProp, annotationType: LsiClassName): String =
    validatorFieldName(prop.name, annotationType)

fun validatorFieldName(propName: String, annotationType: LsiClassName): String =
    "__" + upper(propName) + "_VALIDATOR_" + "_" + upper(annotationType.simpleName) + "_" + abs(annotationType.hashCode())

internal fun upper(text: String): String? {
    var prevUpper = true
    val builder = StringBuilder()
    val size = text.length
    for (i in 0 until size) {
        val c = text[i]
        val upper = Character.isUpperCase(c)
        if (upper) {
            if (!prevUpper) {
                builder.append('_')
            }
            builder.append(c)
        } else {
            builder.append(c.uppercaseChar())
        }
        prevUpper = upper
    }
    return builder.toString()
}

fun parseValidationMessages(
    annotations: List<LsiAnnotation>,
    sourceDeclaration: Any
): Map<LsiClassName, String> {
    val map = mutableMapOf<LsiClassName, String>()
    for (annotation in annotations) {
        val constraint = annotation.annotations.firstOrNull {
            it.fullName == "jakarta.validation.Constraint" ||
                it.fullName == "javax.validation.Constraint"
        } ?: continue
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/generator/utils.parseValidationMessages Constraint.validatedBy 读取
        // 迁移说明：校验器列表读取改为 LSI 类参数数组语义，避免生成层继续直读 annotation attributes
        val validatedBy = constraint.getClassListArgument("validatedBy")
            .takeIf { it.isNotEmpty() }
            ?: continue
        if (validatedBy.isEmpty()) {
            continue
        }
        val annotationName = annotation.fullName
            .takeIf { it.isNotEmpty() }
            ?: continue
        val className = LsiClassName.bestGuess(annotationName)
        if (map.containsKey(className)) {
            // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/generator/utils.parseValidationMessages 重复校验注解冲突报错
            // 迁移说明：移除 KSDeclaration 分支，生成层仅保留 LSI 锚点（LsiClass/LsiField/LsiMethod）
            when (sourceDeclaration) {
                is LsiClass -> throw MetaException(sourceDeclaration, "duplicated annotation $className")
                is LsiField -> throw MetaException(sourceDeclaration, "duplicated annotation $className")
                is LsiMethod -> throw MetaException(sourceDeclaration, "duplicated annotation $className")
                else -> throw IllegalStateException(
                    "duplicated annotation $className: sourceDeclaration must be LSI declaration"
                )
            }
        }
        map[className] = annotation["message"] as? String ?: ""
    }
    return map
}

fun ImmutableProp.nonJimmerMethodAnnotations(
    excludedUserTypePrefixes: List<String>
): List<LsiAnnotationSpec> {
    // 覆盖来源：project/compiler/jimmer-ksp-ext/.../immutable/generator/utils.copyNonJimmerMethodAnnotations
    // 迁移说明：将“筛出可复制的非 Jimmer 方法注解”下沉为可投影 helper，便于 Builder 等 metadata 层先完成注解抽取，再交给 generator 排版
    val addedTypeNames = mutableSetOf<String>()
    return buildList {
        for (annotation in methodAllLsiAnnotations()) {
            val annoTypeName = annotation.fullName
            if (excludedUserTypePrefixes.any { annoTypeName.startsWith(it) }) {
                continue
            }
            if (!annotation.forFun()) {
                continue
            }
            if (!addedTypeNames.add(annoTypeName)) {
                continue
            }
            if (!annoTypeName.startsWith("org.babyfish.jimmer.")) {
                add(annotation.toLsiPoet().copy(useSiteTarget = null))
            }
        }
    }
}

private fun LsiAnnotation.forFun(): Boolean {
    val kotlinTarget = annotations
        .firstOrNull { it.fullName == Target::class.qualifiedName }
        ?.getEnumListArgument(Target::allowedTargets)
        ?.contains(AnnotationTarget.FUNCTION)
        ?: false
    if (kotlinTarget) {
        return true
    }
    val javaTarget = annotations
        .firstOrNull { it.fullName == java.lang.annotation.Target::class.qualifiedName }
        ?.getEnumListArgument(java.lang.annotation.Target::value)
        ?.contains(java.lang.annotation.ElementType.METHOD)
        ?: false
    return javaTarget
}
