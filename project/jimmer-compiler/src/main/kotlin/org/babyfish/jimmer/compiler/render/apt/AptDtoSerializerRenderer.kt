package org.babyfish.jimmer.compiler.render.apt

import com.squareup.javapoet.TypeSpec
import org.babyfish.jimmer.compiler.dto.JimmerDtoJacksonVersion
import org.babyfish.jimmer.compiler.dto.toSerializerPoetType
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer

/** 将共享 DTO Serializer 模型渲染为可嵌入 APT DTO 的 JavaPoet 类型。 */
internal object AptDtoSerializerRenderer {

    @JvmStatic
    fun render(
        dtoType: DtoType,
        graph: DtoGraph,
        immutableSchema: ImmutableSchema,
        jacksonVersion: JimmerDtoJacksonVersion,
        generatedDtoQualifiedName: String,
    ): TypeSpec {
        return LsiJavaPoetRenderer().renderType(
            dtoType.toSerializerPoetType(
                graph = graph,
                immutableSchema = immutableSchema,
                targetLanguage = LsiLanguage.JAVA,
                jacksonVersion = jacksonVersion,
                dtoType = LsiDeclaredType(LsiSymbolId.type(generatedDtoQualifiedName)),
            ),
        )
    }
}
