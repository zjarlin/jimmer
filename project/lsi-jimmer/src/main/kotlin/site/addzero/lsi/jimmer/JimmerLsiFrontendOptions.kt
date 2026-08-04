package site.addzero.lsi.jimmer

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiFrontendDocumentationConvention
import site.addzero.lsi.model.LsiFrontendOptions
import site.addzero.lsi.model.LsiGeneratedPeerDocumentationConvention

const val JIMMER_KEEP_IS_PREFIX_OPTION: String = "jimmer.keepIsPrefix"

val JIMMER_DESCRIPTION_ANNOTATION_TYPE_ID: LsiSymbolId =
    LsiSymbolId.type("org.babyfish.jimmer.client.Description")

val JIMMER_T_NULLABLE_ANNOTATION_TYPE_ID: LsiSymbolId =
    LsiSymbolId.type("org.babyfish.jimmer.client.TNullable")

val JIMMER_IMMUTABLE_ANNOTATION_TYPE_ID: LsiSymbolId =
    LsiSymbolId.type("org.babyfish.jimmer.Immutable")

val JIMMER_ENTITY_ANNOTATION_TYPE_ID: LsiSymbolId =
    LsiSymbolId.type("org.babyfish.jimmer.sql.Entity")

val JIMMER_MAPPED_SUPERCLASS_ANNOTATION_TYPE_ID: LsiSymbolId =
    LsiSymbolId.type("org.babyfish.jimmer.sql.MappedSuperclass")

val JIMMER_EMBEDDABLE_ANNOTATION_TYPE_ID: LsiSymbolId =
    LsiSymbolId.type("org.babyfish.jimmer.sql.Embeddable")

val JIMMER_MANAGED_TYPE_ANNOTATION_TYPE_IDS: Set<LsiSymbolId> = setOf(
    JIMMER_IMMUTABLE_ANNOTATION_TYPE_ID,
    JIMMER_ENTITY_ANNOTATION_TYPE_ID,
    JIMMER_MAPPED_SUPERCLASS_ANNOTATION_TYPE_ID,
    JIMMER_EMBEDDABLE_ANNOTATION_TYPE_ID,
)

/**
 * 将 Jimmer 编译参数和语义标识转换为平台中立的 LSI 前端约定。
 */
fun Map<String, String>.toJimmerLsiFrontendOptions(): LsiFrontendOptions {
    return LsiFrontendOptions(
        keepJavaBooleanGetterIsPrefix = this[JIMMER_KEEP_IS_PREFIX_OPTION] == "true",
        nullableAnnotationTypeIds = setOf(JIMMER_T_NULLABLE_ANNOTATION_TYPE_ID),
        fullExternalDeclarationAnnotationTypeIds = JIMMER_MANAGED_TYPE_ANNOTATION_TYPE_IDS,
        documentationConvention = LsiFrontendDocumentationConvention(
            annotationTypeId = JIMMER_DESCRIPTION_ANNOTATION_TYPE_ID,
            generatedPeer = LsiGeneratedPeerDocumentationConvention(
                ownerAnnotationTypeIds = JIMMER_MANAGED_TYPE_ANNOTATION_TYPE_IDS,
                typeSuffix = "Draft",
            ),
        ),
    )
}
