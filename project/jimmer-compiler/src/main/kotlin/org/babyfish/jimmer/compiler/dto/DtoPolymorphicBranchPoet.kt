package org.babyfish.jimmer.compiler.dto

import site.addzero.lsi.jimmer.dto.DtoPolymorphicBranch
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.generatedPolymorphicDtoBranchAnnotation
import site.addzero.lsi.poet.LsiPoetAnnotation
import site.addzero.lsi.poet.LsiPoetAnnotationArgument
import site.addzero.lsi.poet.LsiPoetAnnotationArgumentNameStyle
import site.addzero.lsi.poet.LsiPoetTypeName
import site.addzero.lsi.poet.toLsiPoetAnnotation

/** 将完整的多态 DTO 分支标记 LSI 注解降低为源码注解。 */
internal fun DtoPolymorphicBranch.toGeneratedPolymorphicDtoBranchPoetAnnotation(
    rootType: DtoType,
    generatedRootTypeName: LsiPoetTypeName,
): LsiPoetAnnotation {
    val annotation = generatedPolymorphicDtoBranchAnnotation(
        rootType = rootType,
        generatedRootTypeId = generatedRootTypeName.typeId,
    )
    val poetAnnotation = annotation.toLsiPoetAnnotation()
    val argumentsByName = poetAnnotation.arguments
        .filterIsInstance<LsiPoetAnnotationArgument.Named>()
        .associateBy(LsiPoetAnnotationArgument.Named::name)
    val orderedArguments = annotation.explicitArgumentNamesInSourceOrder.map { name ->
        requireNotNull(argumentsByName[name]) {
            "Generated polymorphic DTO branch argument '$name' is absent"
        }.copy(nameStyle = LsiPoetAnnotationArgumentNameStyle.VERBATIM)
    }
    return poetAnnotation.copy(arguments = orderedArguments)
}

/** 返回多态 DTO 分支标记及根 DTO 类字面量需要的精确源码类型名。 */
internal fun polymorphicDtoBranchPoetTypeNames(
    generatedRootTypeName: LsiPoetTypeName,
): List<LsiPoetTypeName> {
    return listOf(GENERATED_POLYMORPHIC_DTO_BRANCH_TYPE_NAME, generatedRootTypeName)
}

private val GENERATED_POLYMORPHIC_DTO_BRANCH_TYPE_NAME = JimmerDtoPoetTypeNames.create(
    "org.babyfish.jimmer.internal",
    listOf("GeneratedPolymorphicDtoBranch"),
)
