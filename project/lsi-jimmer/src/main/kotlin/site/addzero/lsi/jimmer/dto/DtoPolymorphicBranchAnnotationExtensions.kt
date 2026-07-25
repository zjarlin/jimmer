package site.addzero.lsi.jimmer.dto

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationArgument
import site.addzero.lsi.model.LsiAnnotationArgumentOrigin
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiDeclaredType

/** 返回多态 DTO 分支供 Client schema 识别和排序的完整标记注解。 */
fun DtoPolymorphicBranch.generatedPolymorphicDtoBranchAnnotation(
    rootType: DtoType,
    generatedRootTypeId: LsiSymbolId,
): LsiAnnotation {
    val branchOrder = generatedPolymorphicDtoBranchOrder(rootType)
    generatedRootTypeId.requireTypeQualifiedName()
    val arguments = linkedMapOf(
        "value" to LsiAnnotationArgument(
            value = LsiAnnotationValue.ClassValue(LsiDeclaredType(generatedRootTypeId)),
            origin = LsiAnnotationArgumentOrigin.EXPLICIT,
        ),
        "order" to LsiAnnotationArgument(
            value = LsiAnnotationValue.IntValue(branchOrder),
            origin = LsiAnnotationArgumentOrigin.EXPLICIT,
        ),
    )
    return LsiAnnotation(
        type = GENERATED_POLYMORPHIC_DTO_BRANCH_TYPE_ID,
        arguments = arguments,
        explicitArgumentNamesInSourceOrder = arguments.keys.toList(),
    )
}

/** 返回分支在冻结多态根中的唯一稳定序号。 */
fun DtoPolymorphicBranch.generatedPolymorphicDtoBranchOrder(
    rootType: DtoType,
): Int {
    val polymorphism = requireNotNull(rootType.polymorphism) {
        "DTO polymorphic branch root is not polymorphic: ${rootType.id.value}"
    }
    val matchingOrders = polymorphism.branches.mapIndexedNotNull { index, branch ->
        index.takeIf {
            branch.bodyTypeId == bodyTypeId && branch.mergedTypeId == mergedTypeId
        }
    }
    require(matchingOrders.size == 1) {
        "DTO polymorphic branch stable id must belong to root type exactly once: $className"
    }
    return matchingOrders.single()
}

private val GENERATED_POLYMORPHIC_DTO_BRANCH_TYPE_ID =
    LsiSymbolId.type("org.babyfish.jimmer.internal.GeneratedPolymorphicDtoBranch")
