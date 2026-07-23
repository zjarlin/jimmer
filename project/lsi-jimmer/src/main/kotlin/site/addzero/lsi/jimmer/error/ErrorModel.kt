package site.addzero.lsi.jimmer.error

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.model.LsiTypeRef

/** 描述工作区内可生成的错误族语义。 */
data class ErrorSchema(
    val families: List<ErrorFamily>,
)

/** 描述错误枚举及其生成异常基类。 */
data class ErrorFamily(
    val id: LsiSymbolId,
    val qualifiedName: String,
    val packageName: String,
    val family: String,
    val exceptionTypeId: LsiSymbolId,
    val exceptionSimpleName: String,
    val checkedException: Boolean,
    val documentation: String?,
    val originatingSources: Set<LsiSource> = emptySet(),
    val declaredFields: List<ErrorField>,
    val codes: List<ErrorCode>,
) {
    init {
        id.requireTypeQualifiedName()
        exceptionTypeId.requireTypeQualifiedName()
    }
}

/** 描述错误枚举项及其生成异常子类。 */
data class ErrorCode(
    val id: LsiSymbolId,
    val enumEntryName: String,
    val code: String,
    val creatorName: String,
    val exceptionTypeId: LsiSymbolId,
    val exceptionSimpleName: String,
    val documentation: String?,
    val declaredFields: List<ErrorField>,
) {
    init {
        exceptionTypeId.requireTypeQualifiedName()
    }
}

/** 描述错误异常携带的领域字段。 */
data class ErrorField(
    val name: String,
    val type: LsiTypeRef,
    val list: Boolean,
    val nullable: Boolean,
    val documentation: String?,
    val declaredBy: LsiSymbolId,
)
