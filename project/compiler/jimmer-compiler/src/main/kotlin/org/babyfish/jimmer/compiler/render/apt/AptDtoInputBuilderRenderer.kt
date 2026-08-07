package org.babyfish.jimmer.compiler.render.apt

import com.squareup.javapoet.TypeSpec
import org.babyfish.jimmer.compiler.dto.JimmerDtoJacksonVersion
import org.babyfish.jimmer.compiler.dto.JimmerDtoPoetTypeNames
import org.babyfish.jimmer.compiler.dto.inputBuilderJsonNamingAnnotationTypeId
import org.babyfish.jimmer.compiler.dto.inputBuilderJsonPojoBuilderAnnotationTypeId
import org.babyfish.jimmer.compiler.dto.inputBuilderPoetTypeNames
import org.babyfish.jimmer.compiler.dto.toInputBuilderPoetType
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.jimmer.ImmutableSchema
import site.addzero.lsi.jimmer.dto.DtoAnnotationContract
import site.addzero.lsi.jimmer.dto.DtoGraph
import site.addzero.lsi.jimmer.dto.DtoType
import site.addzero.lsi.jimmer.dto.DtoTypeId
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.poet.LsiPoetTypeName
import site.addzero.lsi.poet.javapoet.LsiJavaPoetRenderer

/** 将共享 InputBuilder 模型渲染为可嵌入 APT DTO 的 JavaPoet 类型。 */
internal object AptDtoInputBuilderRenderer {

    @JvmStatic
    fun render(
        dtoType: DtoType,
        graph: DtoGraph,
        immutableSchema: ImmutableSchema,
        annotationContract: DtoAnnotationContract,
        workspace: LsiWorkspace,
        jacksonVersion: JimmerDtoJacksonVersion,
        generatedDtoPackageName: String,
        generatedDtoSimpleNames: List<String>,
        generatedDtoTypeNamesByTypeId: Map<DtoTypeId, LsiPoetTypeName>,
        batchRootDtoTypeNames: Collection<LsiPoetTypeName>,
    ): TypeSpec {
        val currentDtoTypeName = JimmerDtoPoetTypeNames.create(
            generatedDtoPackageName,
            generatedDtoSimpleNames,
        )
        val generatedDtoTypes = generatedDtoTypeNamesByTypeId.mapValues { (_, typeName) ->
            LsiDeclaredType(typeName.typeId)
        }
        val inputBuilderType = dtoType.toInputBuilderPoetType(
            graph = graph,
            immutableSchema = immutableSchema,
            annotationContract = annotationContract,
            targetLanguage = LsiLanguage.JAVA,
            currentDtoType = LsiDeclaredType(currentDtoTypeName.typeId),
            generatedDtoTypes = generatedDtoTypes,
            jsonPojoBuilderAnnotationTypeId = jacksonVersion.inputBuilderJsonPojoBuilderAnnotationTypeId(),
            jsonNamingAnnotationTypeId = jacksonVersion.inputBuilderJsonNamingAnnotationTypeId(),
        )
        return LsiJavaPoetRenderer().renderType(
            inputBuilderType,
            typeNames = workspace.inputBuilderPoetTypeNames(
                inputBuilderType = inputBuilderType,
                currentDtoTypeName = currentDtoTypeName,
                generatedDtoTypeNames = generatedDtoTypeNamesByTypeId.values + batchRootDtoTypeNames,
                jacksonVersion = jacksonVersion,
            ),
        )
    }
}
