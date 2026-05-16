package site.addzero.lsi.jimmer.immutable.generator

import site.addzero.lsi.codegen.DRAFT_CONSUMER_LSI_CLASS_NAME
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableCallbackMetadata
import site.addzero.lsi.poet.LsiLambdaTypeName
import site.addzero.lsi.poet.LsiTypeName

internal fun ImmutableCallbackMetadata.toLsiLambdaTypeName(): LsiLambdaTypeName =
    LsiLambdaTypeName(
        receiverType = receiverTypeName,
        returnType = returnTypeName,
        nullable = nullable,
    )

internal fun ImmutableCallbackMetadata.toLsiDraftConsumerTypeName(): LsiTypeName =
    DRAFT_CONSUMER_LSI_CLASS_NAME.parameterizedBy(receiverTypeName)
