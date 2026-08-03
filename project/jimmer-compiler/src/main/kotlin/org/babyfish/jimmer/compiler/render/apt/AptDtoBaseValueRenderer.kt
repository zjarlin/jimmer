package org.babyfish.jimmer.compiler.render.apt

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.CodeBlock
import org.babyfish.jimmer.compiler.dto.toBaseValuePoetCodeBlock
import org.babyfish.jimmer.compiler.dto.dtoBaseValuePoetTypeNames
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.LsiPoetTypeName
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer

/** 将 immutable-to-DTO Java 属性读取语义渲染为 JavaPoet 表达式。 */
internal object AptDtoBaseValueRenderer {

    @JvmStatic
    fun render(
        prop: DtoBaseProp,
        graph: DtoGraph,
        immutableSchema: ImmutableSchema,
        workspace: LsiWorkspace,
        accessorName: String,
        baseParameterName: String,
        baseValueAccessorName: String,
        baseProducerClassName: ClassName,
        baseSlotName: String,
        conversionErrorMessage: String,
        generatedTargetType: (DtoProp) -> LsiDeclaredType,
        generatedTypeNames: Collection<LsiPoetTypeName>,
    ): CodeBlock {
        val producerTypeName = baseProducerClassName.toLsiPoetTypeName()
        val initializer = prop.toBaseValuePoetCodeBlock(
            graph = graph,
            immutableSchema = immutableSchema,
            targetLanguage = LsiLanguage.JAVA,
            generatedTargetType = generatedTargetType,
            baseParameterName = baseParameterName,
            accessorName = accessorName,
            baseValueAccessorName = baseValueAccessorName,
            conversionErrorMessage = conversionErrorMessage,
            javaBaseProducerType = LsiDeclaredType(producerTypeName.typeId),
            javaBaseSlotName = baseSlotName,
        )
        return LsiJavaPoetRenderer().renderCodeBlock(
            codeBlock = initializer,
            typeNames = workspace.dtoBaseValuePoetTypeNames(
                codeBlock = initializer,
                additional = listOf(producerTypeName) + generatedTypeNames,
            ),
        )
    }
}

private fun ClassName.toLsiPoetTypeName(): LsiPoetTypeName {
    return LsiPoetTypeName(
        typeId = LsiSymbolId.type(canonicalName()),
        packageName = packageName(),
        simpleNames = simpleNames(),
    )
}
