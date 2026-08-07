package org.babyfish.jimmer.compiler.render.ksp

import com.squareup.kotlinpoet.ClassName
import org.babyfish.jimmer.compiler.immutable.immutableDraftPoetTypeNames
import org.babyfish.jimmer.compiler.immutable.immutableSourcePoetTypeNames
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.generatedDraftType
import site.addzero.lsi.jimmer.sourceTypeRef
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

/** 将冻结的不可变源码与 Draft 类型渲染为 KotlinPoet 名称。 */
internal object KspImmutableTypeNameRenderer {

    fun renderSource(type: ImmutableType, workspace: LsiWorkspace): ClassName {
        return LsiKotlinPoetRenderer().renderTypeName(
            type = type.sourceTypeRef(),
            typeNames = workspace.immutableSourcePoetTypeNames(type),
        ) as? ClassName ?: error("Immutable source type must render as Kotlin ClassName: ${type.id.value}")
    }

    fun renderDraft(type: ImmutableType, workspace: LsiWorkspace): ClassName {
        return LsiKotlinPoetRenderer().renderTypeName(
            type = type.generatedDraftType(),
            typeNames = workspace.immutableDraftPoetTypeNames(type),
        ) as? ClassName ?: error("Immutable Draft type must render as Kotlin ClassName: ${type.id.value}")
    }
}
