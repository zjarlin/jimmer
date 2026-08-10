package org.babyfish.jimmer.compiler.render.apt

import com.squareup.javapoet.CodeBlock
import org.babyfish.jimmer.compiler.dto.toBaseValuePoetCodeBlock
import org.babyfish.jimmer.compiler.dto.dtoBaseValuePoetTypeNames
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.ImmutableType
import site.addzero.lsi.jimmer.generatedDraftProducerType
import site.addzero.lsi.jimmer.dto.DtoBaseProp
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoProp
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.clazz.generatedSiblingClass
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
        baseType: ImmutableType,
        baseSlotName: String,
        conversionErrorMessage: String,
        generatedTargetType: (DtoProp) -> LsiDeclaredType,
        generatedTypeNames: Collection<LsiClass>,
    ): CodeBlock {
        val producerType = baseType.generatedDraftProducerType()
        val producerTypeName = workspace.generatedSiblingClass(
            sourceTypeId = baseType.id,
            generatedTypeId = producerType.declarationId,
            simpleNameSuffix = "Draft",
            nestedSimpleNames = listOf("Producer"),
        )
        val initializer = prop.toBaseValuePoetCodeBlock(
            graph = graph,
            immutableSchema = immutableSchema,
            targetLanguage = LsiLanguage.JAVA,
            generatedTargetType = generatedTargetType,
            baseParameterName = baseParameterName,
            accessorName = accessorName,
            baseValueAccessorName = baseValueAccessorName,
            conversionErrorMessage = conversionErrorMessage,
            javaBaseProducerType = producerType,
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
