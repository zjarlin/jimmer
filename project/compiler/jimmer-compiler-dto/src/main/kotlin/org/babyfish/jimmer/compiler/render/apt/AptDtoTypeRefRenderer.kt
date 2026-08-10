package org.babyfish.jimmer.compiler.render.apt

import com.squareup.javapoet.TypeName
import org.babyfish.jimmer.compiler.dto.dtoTypeRefPoetTypeNames
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.dto.DtoReusableTypeReference
import site.addzero.lsi.jimmer.dto.DtoTypeRef
import site.addzero.lsi.jimmer.dto.toLsiType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.LsiTypeName
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer

/** 将冻结的 DTO 类型引用渲染为 JavaPoet 类型。 */
internal object AptDtoTypeRefRenderer {

    @JvmStatic
    fun render(typeRef: DtoTypeRef, workspace: LsiWorkspace): TypeName {
        return render(typeRef.toLsiType(LsiLanguage.JAVA), workspace)
    }

    @JvmStatic
    fun render(type: LsiTypeRef, workspace: LsiWorkspace): TypeName {
        return render(type, workspace, emptyList())
    }

    @JvmStatic
    fun render(
        typeRef: DtoReusableTypeReference,
        workspace: LsiWorkspace,
        generatedTypeName: LsiTypeName?,
    ): TypeName {
        return render(typeRef.toLsiType(), workspace, listOfNotNull(generatedTypeName))
    }

    /** 将已注册的 DTO 生成类型名渲染为 JavaPoet 类型。 */
    @JvmStatic
    fun render(typeName: LsiTypeName, workspace: LsiWorkspace): TypeName {
        return render(LsiDeclaredType(typeName.typeId), workspace, listOf(typeName))
    }

    @JvmStatic
    fun render(
        type: LsiTypeRef,
        workspace: LsiWorkspace,
        generatedTypeNames: Collection<LsiTypeName>,
    ): TypeName {
        return LsiJavaPoetRenderer().renderTypeName(
            type = type,
            typeNames = workspace.dtoTypeRefPoetTypeNames(type, generatedTypeNames),
        )
    }
}
