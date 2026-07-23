package site.addzero.lsi.jimmer

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationTarget
import site.addzero.lsi.model.LsiAnnotationUseSiteTarget
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.annotationTargetPolicy

/** 已冻结、可投影到 Draft 方法的有效属性注解。 */
data class ImmutableDraftAnnotationProjection(
    val builderMethodAnnotations: List<LsiAnnotation>,
    val methodAnnotations: List<LsiAnnotation>,
) {
    init {
        require(builderMethodAnnotations.all { annotation -> annotation.useSiteTarget == null }) {
            "Immutable draft builder annotations must not retain use-site targets"
        }
        require(methodAnnotations.all { annotation -> annotation.useSiteTarget == null }) {
            "Immutable draft method annotations must not retain use-site targets"
        }
        require(builderMethodAnnotations.none { annotation -> annotation.isGloballyExcluded() }) {
            "Immutable draft builder annotations cannot contain Jimmer or override annotations"
        }
        require(methodAnnotations.none { annotation -> annotation.isGloballyExcluded() }) {
            "Immutable draft method annotations cannot contain Jimmer or override annotations"
        }
        require(builderMethodAnnotations.none { annotation -> annotation.isNullableMarker() }) {
            "Immutable draft builder annotations cannot contain nullable markers"
        }
    }
}

/**
 * 将属性覆盖合并后的有效注解冻结为 Draft 方法投影。
 *
 * 注解目标策略在此处完成解析，后续 renderer 不再访问 [LsiWorkspace]。
 */
fun ImmutableProp.toDraftAnnotationProjection(
    workspace: LsiWorkspace,
    excludedUserAnnotationPrefixes: Collection<String> = emptyList(),
): ImmutableDraftAnnotationProjection {
    val excludedPrefixes = excludedUserAnnotationPrefixes
        .asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .toList()
    val methodAnnotations = annotations
        .asSequence()
        .filter(LsiAnnotation::isMethodProjectionSource)
        .filterNot(LsiAnnotation::isGloballyExcluded)
        .filterNot { annotation ->
            val qualifiedName = annotation.type.requireTypeQualifiedName()
            excludedPrefixes.any(qualifiedName::startsWith)
        }
        .filter { annotation -> workspace.allowsMethodTarget(annotation.type) }
        .sortedBy(LsiAnnotation::methodProjectionPriority)
        .map { annotation -> annotation.copy(useSiteTarget = null) }
        .distinct()
        .toList()
    return ImmutableDraftAnnotationProjection(
        builderMethodAnnotations = methodAnnotations.filterNot(LsiAnnotation::isNullableMarker),
        methodAnnotations = methodAnnotations,
    )
}

private fun LsiAnnotation.isMethodProjectionSource(): Boolean {
    return useSiteTarget == null || useSiteTarget in METHOD_PROJECTION_SOURCE_TARGETS
}

private fun LsiAnnotation.methodProjectionPriority(): Int {
    return when (useSiteTarget) {
        LsiAnnotationUseSiteTarget.GETTER -> 0
        LsiAnnotationUseSiteTarget.METHOD -> 1
        LsiAnnotationUseSiteTarget.PROPERTY -> 2
        LsiAnnotationUseSiteTarget.ALL -> 3
        null -> 4
        else -> 5
    }
}

private fun LsiAnnotation.isGloballyExcluded(): Boolean {
    val qualifiedName = type.requireTypeQualifiedName()
    return qualifiedName.startsWith(JIMMER_PACKAGE_PREFIX) || qualifiedName in OVERRIDE_ANNOTATIONS
}

private fun LsiAnnotation.isNullableMarker(): Boolean {
    val qualifiedName = type.requireTypeQualifiedName()
    return qualifiedName == T_NULLABLE_ANNOTATION ||
        qualifiedName.substringAfterLast('.') in NULLABLE_MARKER_SIMPLE_NAMES
}

private fun LsiWorkspace.allowsMethodTarget(annotationTypeId: LsiSymbolId): Boolean {
    val declaration = this[annotationTypeId] as? LsiTypeDeclaration ?: return false
    return declaration.annotationTargetPolicy().allows(LsiAnnotationTarget.METHOD)
}

private val METHOD_PROJECTION_SOURCE_TARGETS = setOf(
    LsiAnnotationUseSiteTarget.METHOD,
    LsiAnnotationUseSiteTarget.PROPERTY,
    LsiAnnotationUseSiteTarget.GETTER,
    LsiAnnotationUseSiteTarget.ALL,
)

private val OVERRIDE_ANNOTATIONS = setOf(
    "java.lang.Override",
    "kotlin.Override",
)

private val NULLABLE_MARKER_SIMPLE_NAMES = setOf(
    "Null",
    "Nullable",
)

private const val JIMMER_PACKAGE_PREFIX = "org.babyfish.jimmer."

private const val T_NULLABLE_ANNOTATION = "org.babyfish.jimmer.client.TNullable"
