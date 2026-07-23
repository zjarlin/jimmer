package site.addzero.lsi.jimmer.transactional

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiModality
import site.addzero.lsi.model.LsiTypeParameter
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiVisibility

/** Transactional 类型的完整共享语义模型。 */
data class TransactionalSchema(
    val types: List<TransactionalType>,
)

/** 单个 Transactional 类型的冻结语义。 */
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

/** Transactional 类型使用的 SQL client 成员。 */
data class TransactionalSqlClient(
    val logicalId: LsiSymbolId,
    val declarationId: LsiSymbolId,
    val name: String,
    val type: LsiTypeRef,
    val platform: TransactionalPlatform,
)

/** Transactional 生成类型需要暴露的构造器。 */
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

/** Transactional 生成类型需要代理的方法。 */
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

/** Transactional 构造器或方法参数。 */
data class TransactionalParameter(
    val id: LsiSymbolId,
    val name: String,
    val index: Int,
    val type: LsiTypeRef,
    val vararg: Boolean,
    val hasDefault: Boolean,
    val annotations: List<LsiAnnotation>,
    val annotationProjectionTypeIds: Set<LsiSymbolId> = emptySet(),
)

/** Transactional 源声明所属的生成平台。 */
enum class TransactionalPlatform {
    JAVA,
    KOTLIN,
}

/** Transactional 方法在 LSI 中的声明形态。 */
enum class TransactionalMethodSourceKind {
    FUNCTION,
    PROPERTY_GETTER,
}
