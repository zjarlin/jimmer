package org.babyfish.jimmer.compiler.transactional

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiModality
import site.addzero.lsi.model.LsiTypeParameter
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiVisibility

data class TransactionalPrecompiledSchema(
    val types: List<TransactionalType>,
)

data class TransactionalType(
    val id: LsiSymbolId,
    val qualifiedName: String,
    val packageName: String,
    val simpleName: String,
    val generatedSimpleName: String,
    val visibility: LsiVisibility,
    val modality: LsiModality,
    val copiedAnnotations: List<LsiAnnotation>,
    val targetAnnotationTypeId: LsiSymbolId?,
    val sqlClient: TransactionalSqlClient,
    val constructors: List<TransactionalConstructor>,
    val methods: List<TransactionalMethod>,
)

data class TransactionalSqlClient(
    val logicalId: LsiSymbolId,
    val declarationId: LsiSymbolId,
    val name: String,
    val type: LsiTypeRef,
    val platform: TransactionalPlatform,
)

data class TransactionalConstructor(
    val id: LsiSymbolId,
    val primary: Boolean,
    val visibility: LsiVisibility,
    val parameters: List<TransactionalParameter>,
    val typeParameters: List<LsiTypeParameter>,
    val thrownTypes: List<LsiTypeRef>,
    val documentation: String?,
    val copiedAnnotations: List<LsiAnnotation>,
)

data class TransactionalMethod(
    val id: LsiSymbolId,
    val name: String,
    val sourceKind: TransactionalMethodSourceKind,
    val visibility: LsiVisibility,
    val modality: LsiModality,
    val returnType: LsiTypeRef,
    val parameters: List<TransactionalParameter>,
    val typeParameters: List<LsiTypeParameter>,
    val thrownTypes: List<LsiTypeRef>,
    val documentation: String?,
    val copiedAnnotations: List<LsiAnnotation>,
    val propagation: String,
    val classLevel: Boolean,
)

data class TransactionalParameter(
    val id: LsiSymbolId,
    val name: String,
    val index: Int,
    val type: LsiTypeRef,
    val vararg: Boolean,
    val hasDefault: Boolean,
    val annotations: List<LsiAnnotation>,
)

enum class TransactionalPlatform {
    JAVA,
    KOTLIN,
}

enum class TransactionalMethodSourceKind {
    FUNCTION,
    PROPERTY_GETTER,
}
