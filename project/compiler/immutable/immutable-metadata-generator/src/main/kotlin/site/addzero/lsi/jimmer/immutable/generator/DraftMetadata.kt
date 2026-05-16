package site.addzero.lsi.jimmer.immutable.generator

import site.addzero.lsi.codegen.KOTLIN_UNIT_LSI_CLASS_NAME
import site.addzero.lsi.codegen.LsiClassName
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableBuilderTypeMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableAssociatedIdMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableCallbackMetadata
import site.addzero.lsi.poet.LsiTypeName

data class ImmutableDraftTypeMetadata(
    val simpleName: String,
    val className: LsiClassName,
    val draftClassName: LsiClassName,
    val superDraftClassNames: List<LsiClassName>,
    val declaredProps: List<ImmutableDraftDeclaredPropMetadata>,
    val producerTypeMetadata: ImmutableProducerTypeMetadata,
    val builderTypeMetadata: ImmutableBuilderTypeMetadata?,
    val addFunMetadatas: List<ImmutableDraftAddFunMetadata>,
    val newFunMetadatas: List<ImmutableDraftNewFunMetadata>,
    val copyFunMetadata: ImmutableDraftCopyFunMetadata?,
)

data class ImmutableDraftDeclaredPropMetadata(
    val name: String,
    val typeName: LsiTypeName,
    val isMutable: Boolean,
    val funReturnTypeName: LsiTypeName?,
    val refBlockMetadata: ImmutableCallbackMetadata?,
    val associatedIdMetadata: ImmutableAssociatedIdMetadata?,
)

data class ImmutableDraftAddFunMetadata(
    val annotationClassName: LsiClassName,
    val receiverTypeName: LsiTypeName,
    val baseParameterTypeName: LsiTypeName?,
    val blockMetadata: ImmutableCallbackMetadata?,
    val returnTypeName: LsiTypeName,
    val producerClassName: LsiClassName,
    val draftClassName: LsiClassName,
)

data class ImmutableDraftNewFunMetadata(
    val name: String,
    val annotationClassName: LsiClassName,
    val receiverTypeName: LsiTypeName?,
    val baseParameterTypeName: LsiTypeName?,
    val blockMetadata: ImmutableCallbackMetadata?,
    val returnTypeName: LsiTypeName,
    val producerClassName: LsiClassName,
)

data class ImmutableDraftCopyFunMetadata(
    val annotationClassName: LsiClassName,
    val receiverTypeName: LsiTypeName,
    val blockMetadata: ImmutableCallbackMetadata,
    val returnTypeName: LsiTypeName,
    val draftClassName: LsiClassName,
)

internal fun draftCallbackMetadata(receiverTypeName: LsiTypeName): ImmutableCallbackMetadata =
    ImmutableCallbackMetadata(
        receiverTypeName = receiverTypeName,
        returnTypeName = KOTLIN_UNIT_LSI_CLASS_NAME,
    )
