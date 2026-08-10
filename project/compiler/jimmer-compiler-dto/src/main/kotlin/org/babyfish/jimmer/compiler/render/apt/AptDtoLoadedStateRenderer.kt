package org.babyfish.jimmer.compiler.render.apt

import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.FieldSpec
import javax.lang.model.element.Modifier
import org.babyfish.jimmer.compiler.dto.toLoadedStateStoragePoetFieldOrNull
import org.babyfish.jimmer.compiler.dto.toBaseLoadedStateInitializerPoetCodeBlock
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.model.LsiModifier
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer

internal object AptDtoLoadedStateRenderer {

    /** 将冻结的加载状态语义渲染为 Java DTO 字段。 */
    @JvmStatic
    fun renderStorageField(
        prop: DtoProp,
        graph: DtoGraph,
        visibility: Modifier,
    ): FieldSpec? {
        val field = prop.toLoadedStateStoragePoetFieldOrNull(
            graph = graph,
            visibility = visibility.toLsiPoetVisibility(),
        ) ?: return null
        return LsiJavaPoetRenderer().renderField(field, emptyList())
    }

    @JvmStatic
    fun renderBaseInitializer(
        prop: DtoBaseProp,
        graph: DtoGraph,
        accessorName: String,
        baseParameterName: String,
    ): CodeBlock? {
        val codeBlock = prop.toBaseLoadedStateInitializerPoetCodeBlock(
            graph = graph,
            targetLanguage = LsiLanguage.JAVA,
            accessorName = accessorName,
            baseParameterName = baseParameterName,
        ) ?: return null
        return LsiJavaPoetRenderer().renderCodeBlock(codeBlock, emptyList())
    }
}

private fun Modifier.toLsiPoetVisibility(): LsiModifier {
    return when (this) {
        Modifier.PUBLIC -> LsiModifier.PUBLIC
        Modifier.PROTECTED -> LsiModifier.PROTECTED
        Modifier.PRIVATE -> LsiModifier.PRIVATE
        else -> error("APT DTO field visibility must be public, protected or private: $this")
    }
}
