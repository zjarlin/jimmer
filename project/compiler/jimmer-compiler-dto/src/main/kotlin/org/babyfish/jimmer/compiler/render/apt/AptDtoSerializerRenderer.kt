package org.babyfish.jimmer.compiler.render.apt

import com.squareup.javapoet.TypeSpec
import org.babyfish.jimmer.compiler.JacksonFamily
import org.babyfish.jimmer.compiler.dto.serializerPoetTypeNames
import org.babyfish.jimmer.compiler.dto.toSerializerPoetType
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.model.LsiTypeName
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer

/** 将共享 DTO Serializer 模型渲染为可嵌入 APT DTO 的 JavaPoet 类型。 */
internal object AptDtoSerializerRenderer {

    @JvmStatic
    fun render(
        dtoType: DtoType,
        graph: DtoGraph,
        immutableSchema: ImmutableSchema,
        jacksonVersion: JacksonFamily,
        generatedDtoPackageName: String,
        generatedDtoSimpleNames: List<String>,
    ): TypeSpec {
        val generatedDtoTypeName = LsiTypeName(
            typeId = LsiSymbolId.type(
                listOf(generatedDtoPackageName, generatedDtoSimpleNames.joinToString("."))
                    .filter(String::isNotEmpty)
                    .joinToString("."),
            ),
            packageName = generatedDtoPackageName,
            simpleNames = generatedDtoSimpleNames,
        )
        return LsiJavaPoetRenderer().renderType(
            dtoType.toSerializerPoetType(
                graph = graph,
                immutableSchema = immutableSchema,
                targetLanguage = LsiLanguage.JAVA,
                jacksonVersion = jacksonVersion,
                dtoType = LsiDeclaredType(generatedDtoTypeName.typeId),
            ),
            typeNames = jacksonVersion.serializerPoetTypeNames(generatedDtoTypeName),
        )
    }
}
