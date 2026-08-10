package org.babyfish.jimmer.compiler.dto

import site.addzero.lsi.jimmer.dto.DtoPolymorphicBranch
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.generatedPolymorphicDtoBranchAnnotation
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiSourceAnnotationArgument
import site.addzero.lsi.model.LsiAnnotationArgumentNameStyle
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.model.toSourceAnnotation

/** 将完整的多态 DTO 分支标记 LSI 注解降低为源码注解。 */
internal fun DtoPolymorphicBranch.toGeneratedPolymorphicDtoBranchPoetAnnotation(
    rootType: DtoType,
    generatedRootTypeName: LsiClass,
): LsiAnnotation {
    val annotation = generatedPolymorphicDtoBranchAnnotation(
        rootType = rootType,
        generatedRootTypeId = generatedRootTypeName.id,
    )
    val poetAnnotation = annotation.toSourceAnnotation()
    val argumentsByName = poetAnnotation.sourceArguments
        .filterIsInstance<LsiSourceAnnotationArgument.Named>()
        .associateBy(LsiSourceAnnotationArgument.Named::name)
    val orderedArguments = annotation.explicitArgumentNamesInSourceOrder.map { name ->
        requireNotNull(argumentsByName[name]) {
            "Generated polymorphic DTO branch argument '$name' is absent"
        }.copy(nameStyle = LsiAnnotationArgumentNameStyle.VERBATIM)
    }
    return poetAnnotation.copy(sourceArguments = orderedArguments)
}

/** 返回多态 DTO 分支标记及根 DTO 类字面量需要的精确源码类型名。 */
internal fun polymorphicDtoBranchPoetTypeNames(
    generatedRootTypeName: LsiClass,
): List<LsiClass> {
    return listOf(GENERATED_POLYMORPHIC_DTO_BRANCH_TYPE_NAME, generatedRootTypeName)
}

private val GENERATED_POLYMORPHIC_DTO_BRANCH_TYPE_NAME = JimmerDtoPoetTypeNames.create(
    "org.babyfish.jimmer.internal",
    listOf("GeneratedPolymorphicDtoBranch"),
)
