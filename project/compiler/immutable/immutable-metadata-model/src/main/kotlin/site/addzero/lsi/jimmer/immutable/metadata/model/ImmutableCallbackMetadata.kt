package site.addzero.lsi.jimmer.immutable.metadata.model

import site.addzero.lsi.poet.LsiTypeName

data class ImmutableCallbackMetadata(
    val receiverTypeName: LsiTypeName,
    val returnTypeName: LsiTypeName,
    val nullable: Boolean = false,
)
