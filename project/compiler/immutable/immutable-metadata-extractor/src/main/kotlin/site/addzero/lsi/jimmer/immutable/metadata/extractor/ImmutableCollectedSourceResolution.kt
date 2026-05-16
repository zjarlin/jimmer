package site.addzero.lsi.jimmer.immutable.metadata.extractor

import site.addzero.lsi.clazz.LsiClass

data class ImmutableCollectedSourceResolution(
    val sources: List<ImmutableResolvedSource>,
    val lsiClasses: List<LsiClass>,
)
