package org.babyfish.jimmer.compiler.render.ksp

import com.squareup.kotlinpoet.TypeName
import org.babyfish.jimmer.compiler.dto.dtoBaseContractPoetTypeNames
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.generatedBaseContractType
import site.addzero.lsi.jimmer.dto.immutableBaseType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

/** 将冻结的 DTO Kotlin 基础契约渲染为 KotlinPoet 类型。 */
internal object KspDtoBaseContractRenderer {

    fun render(
        dtoType: DtoType,
        immutableSchema: ImmutableSchema,
        workspace: LsiWorkspace,
    ): TypeName {
        val contractType = requireNotNull(
            dtoType.generatedBaseContractType(immutableSchema, LsiLanguage.KOTLIN)
        ) {
            "Frozen DTO type has no Kotlin base contract: ${dtoType.id.value}"
        }
        val baseType = dtoType.immutableBaseType(immutableSchema)
        return LsiKotlinPoetRenderer().renderTypeName(
            type = contractType,
            typeNames = workspace.dtoBaseContractPoetTypeNames(contractType, baseType),
        )
    }
}
