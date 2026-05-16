package site.addzero.lsi.jimmer.transactional.metadata.model

/**
 * Tx 类型元数据。
 *
 * 纯领域模型：
 * - 不暴露 `LsiClass` / `LsiField` / `LsiMethod` / `LsiType`
 * - 不暴露 `KS*` / `TypeElement`
 * - 不暴露 `Context` / `LsiFiler`
 */
data class TxTypeMetadata(
    val id: String,
    val sourceSimpleName: String,
    val sourceQualifiedName: String,
    val packageName: String,
    val generatedSimpleName: String,
    val generatedQualifiedName: String,
    val isInternal: Boolean,
    val isAbstract: Boolean,
    val superTypeQualifiedName: String,
    val copiedAnnotations: List<TxAnnotationMetadata>,
    val targetAnnotationTypeQualifiedName: String?,
    val sqlClientPropertyName: String,
    val primaryConstructor: TxConstructorMetadata?,
    val secondaryConstructors: List<TxConstructorMetadata>,
    val methods: List<TxMethodMetadata>,
)

data class TxConstructorMetadata(
    val id: String,
    val isProtected: Boolean,
    val isInternal: Boolean,
    val annotations: List<TxAnnotationMetadata>,
    val parameters: List<TxParameterMetadata>,
)

data class TxMethodMetadata(
    val id: String,
    val name: String,
    val propagation: String,
    val isProtected: Boolean,
    val isInternal: Boolean,
    val annotations: List<TxAnnotationMetadata>,
    val parameters: List<TxParameterMetadata>,
    val returnType: TxTypeRefMetadata?,
    val thrownTypes: List<TxTypeRefMetadata>,
)

data class TxParameterMetadata(
    val id: String,
    val name: String,
    val type: TxTypeRefMetadata,
)

data class TxTypeRefMetadata(
    val qualifiedName: String?,
    val simpleName: String?,
    val presentableText: String?,
    val nullable: Boolean,
    val primitive: Boolean,
    val array: Boolean,
    val typeArguments: List<TxTypeRefMetadata>,
    val componentType: TxTypeRefMetadata?,
)

data class TxAnnotationMetadata(
    val qualifiedName: String,
    val arguments: List<TxAnnotationArgumentMetadata>,
)

data class TxAnnotationArgumentMetadata(
    val name: String,
    val value: TxAnnotationValueMetadata,
)

sealed interface TxAnnotationValueMetadata {
    data object NullValue : TxAnnotationValueMetadata

    data class StringValue(val value: String) : TxAnnotationValueMetadata

    data class BooleanValue(val value: Boolean) : TxAnnotationValueMetadata

    data class NumberValue(val value: Number) : TxAnnotationValueMetadata

    data class CharValue(val value: Char) : TxAnnotationValueMetadata

    data class EnumValue(
        val typeQualifiedName: String,
        val entryName: String,
    ) : TxAnnotationValueMetadata

    data class ClassValue(
        val type: TxTypeRefMetadata,
    ) : TxAnnotationValueMetadata

    data class TypeValue(
        val type: TxTypeRefMetadata,
    ) : TxAnnotationValueMetadata

    data class AnnotationValue(
        val annotation: TxAnnotationMetadata,
    ) : TxAnnotationValueMetadata

    data class ListValue(
        val values: List<TxAnnotationValueMetadata>,
    ) : TxAnnotationValueMetadata
}
