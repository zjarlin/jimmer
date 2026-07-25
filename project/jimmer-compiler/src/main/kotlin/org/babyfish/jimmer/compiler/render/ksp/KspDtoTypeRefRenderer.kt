package org.babyfish.jimmer.compiler.render.ksp

import com.squareup.kotlinpoet.TypeName
import org.babyfish.jimmer.compiler.dto.dtoTypeRefPoetTypeNames
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.dto.DtoReusableTypeReference
import site.addzero.lsi.jimmer.dto.DtoTypeRef
import site.addzero.lsi.jimmer.dto.toLsiType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.LsiPoetTypeName
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

/** 将冻结的 DTO 类型引用渲染为 KotlinPoet 类型。 */
internal object KspDtoTypeRefRenderer {

    fun render(typeRef: DtoTypeRef, workspace: LsiWorkspace): TypeName {
        return render(typeRef.toLsiType(LsiLanguage.KOTLIN), workspace)
    }

    fun render(type: LsiTypeRef, workspace: LsiWorkspace): TypeName {
        return render(type, workspace, emptyList())
    }

    fun render(
        typeRef: DtoReusableTypeReference,
        workspace: LsiWorkspace,
        generatedTypeName: LsiPoetTypeName?,
    ): TypeName {
        return render(typeRef.toLsiType(), workspace, listOfNotNull(generatedTypeName))
    }

    /** 将已注册的 DTO 生成类型名渲染为 KotlinPoet 类型。 */
    fun render(typeName: LsiPoetTypeName, workspace: LsiWorkspace): TypeName {
        return render(LsiDeclaredType(typeName.typeId), workspace, listOf(typeName))
    }

    private fun render(
        type: LsiTypeRef,
        workspace: LsiWorkspace,
        generatedTypeNames: Collection<LsiPoetTypeName>,
    ): TypeName {
        return LsiKotlinPoetRenderer().renderTypeName(
            type = type,
            typeNames = workspace.dtoTypeRefPoetTypeNames(type, generatedTypeNames),
        )
    }
}
