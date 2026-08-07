package org.babyfish.jimmer.compiler.render.apt

import com.squareup.javapoet.TypeName
import org.babyfish.jimmer.compiler.dto.dtoBaseContractPoetTypeNames
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.generatedBaseContractType
import site.addzero.lsi.jimmer.dto.immutableBaseType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer

/** 将冻结的 DTO Java 基础契约渲染为 JavaPoet 类型。 */
internal object AptDtoBaseContractRenderer {

    @JvmStatic
    fun render(
        dtoType: DtoType,
        immutableSchema: ImmutableSchema,
        workspace: LsiWorkspace,
    ): TypeName {
        val contractType = requireNotNull(
            dtoType.generatedBaseContractType(immutableSchema, LsiLanguage.JAVA)
        ) {
            "Frozen DTO type has no Java base contract: ${dtoType.id.value}"
        }
        val baseType = dtoType.immutableBaseType(immutableSchema)
        return LsiJavaPoetRenderer().renderTypeName(
            type = contractType,
            typeNames = workspace.dtoBaseContractPoetTypeNames(contractType, baseType),
        )
    }
}
