package org.babyfish.jimmer.compiler.render.apt

import com.squareup.javapoet.ClassName
import org.babyfish.jimmer.compiler.immutable.immutableDraftPoetTypeNames
import org.babyfish.jimmer.compiler.immutable.immutableSourcePoetTypeNames
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.generatedDraftType
import site.addzero.lsi.jimmer.sourceTypeRef
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer

/** 将冻结的不可变源码与 Draft 类型渲染为 JavaPoet 名称。 */
object AptImmutableTypeNameRenderer {

    @JvmStatic
    fun renderSource(type: ImmutableType, workspace: LsiWorkspace): ClassName {
        return LsiJavaPoetRenderer().renderTypeName(
            type = type.sourceTypeRef(),
            typeNames = workspace.immutableSourcePoetTypeNames(type),
        ) as? ClassName ?: error("Immutable source type must render as Java ClassName: ${type.id.value}")
    }

    @JvmStatic
    fun renderDraft(type: ImmutableType, workspace: LsiWorkspace): ClassName {
        return LsiJavaPoetRenderer().renderTypeName(
            type = type.generatedDraftType(),
            typeNames = workspace.immutableDraftPoetTypeNames(type),
        ) as? ClassName ?: error("Immutable Draft type must render as Java ClassName: ${type.id.value}")
    }
}
