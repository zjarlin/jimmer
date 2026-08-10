package org.babyfish.jimmer.compiler.render.ksp

import com.squareup.kotlinpoet.TypeSpec
import org.babyfish.jimmer.compiler.JacksonFamily
import org.babyfish.jimmer.compiler.dto.serializerPoetTypeNames
import org.babyfish.jimmer.compiler.dto.toSerializerPoetType
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.poet.kotlinpoet.LsiKotlinPoetRenderer

/** 将共享 DTO Serializer 模型渲染为可嵌入 KSP DTO 的 KotlinPoet 类型。 */
internal object KspDtoSerializerRenderer {

    fun render(
        dtoType: DtoType,
        graph: DtoGraph,
        immutableSchema: ImmutableSchema,
        jacksonVersion: JacksonFamily,
        generatedDtoPackageName: String,
        generatedDtoSimpleNames: List<String>,
    ): TypeSpec {
        val generatedDtoTypeName = LsiClass(
            typeId = LsiSymbolId.type(
                listOf(generatedDtoPackageName, generatedDtoSimpleNames.joinToString("."))
                    .filter(String::isNotEmpty)
                    .joinToString("."),
            ),
            packageName = generatedDtoPackageName,
            simpleNames = generatedDtoSimpleNames,
        )
        return LsiKotlinPoetRenderer().renderType(
            dtoType.toSerializerPoetType(
                graph = graph,
                immutableSchema = immutableSchema,
                targetLanguage = LsiLanguage.KOTLIN,
                jacksonVersion = jacksonVersion,
                dtoType = LsiDeclaredType(generatedDtoTypeName.id),
            ),
            typeNames = jacksonVersion.serializerPoetTypeNames(generatedDtoTypeName),
        )
    }
}
