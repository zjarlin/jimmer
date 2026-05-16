package site.addzero.lsi.jimmer.immutable.metadata.extractor

import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableFetcherTypeMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutablePropsTypeMetadata
import site.addzero.lsi.jimmer.immutable.metadata.model.ImmutableSourceMetadata
import site.addzero.lsi.jimmer.meta.ImmutableType

data class ImmutableResolvedSource(
    val metadata: ImmutableSourceMetadata,
    val immutableTypes: List<ImmutableType>,
    val propsTypeMetadata: ImmutablePropsTypeMetadata?,
    val fetcherTypeMetadata: ImmutableFetcherTypeMetadata?,
)
