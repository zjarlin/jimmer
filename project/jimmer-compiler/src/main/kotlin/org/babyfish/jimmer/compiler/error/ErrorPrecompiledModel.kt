package org.babyfish.jimmer.compiler.error

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.model.LsiTypeRef

data class ErrorPrecompiledSchema(
    val families: List<ErrorFamilyModel>,
)

data class ErrorFamilyModel(
    val id: LsiSymbolId,
    val qualifiedName: String,
    val packageName: String,
    val family: String,
    val exceptionTypeId: LsiSymbolId,
    val exceptionSimpleName: String,
    val checkedException: Boolean,
    val documentation: String?,
    val originatingSources: Set<LsiSource> = emptySet(),
    val declaredFields: List<ErrorFieldModel>,
    val codes: List<ErrorCodeModel>,
) {
    init {
        id.requireTypeQualifiedName()
        exceptionTypeId.requireTypeQualifiedName()
    }
}

data class ErrorCodeModel(
    val id: LsiSymbolId,
    val enumEntryName: String,
    val code: String,
    val creatorName: String,
    val exceptionTypeId: LsiSymbolId,
    val exceptionSimpleName: String,
    val documentation: String?,
    val declaredFields: List<ErrorFieldModel>,
    val fields: List<ErrorFieldModel>,
) {
    init {
        exceptionTypeId.requireTypeQualifiedName()
    }
}

data class ErrorFieldModel(
    val name: String,
    val type: LsiTypeRef,
    val list: Boolean,
    val nullable: Boolean,
    val documentation: String?,
    val declaredBy: LsiSymbolId,
)
