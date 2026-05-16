package site.addzero.lsi.codegen

/**
 * 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../DraftGenerator|FetcherGenerator|FetcherDslGenerator
 * 以及 project/compiler/jimmer-ksp-ext/.../util/GeneratedAnnotation、project/compiler/dto/jimmer-ksp-dto/.../InputBuilderGenerator
 * 迁移说明：这组常量现在只承载 LSI 类名语义，shared compiler core 不再暴露 KotlinPoet `ClassName`。
 */
const val DRAFT = "Draft"
const val PRODUCER = "Producer"
const val IMPLEMENTOR = "Implementor"
const val IMPL = "Impl"
const val DRAFT_IMPL = "DraftImpl"
const val DRAFT_FIELD_EMAIL_PATTERN = "__email_pattern"
const val PROPS = "Props"
const val FETCHER = "Fetcher"
const val FETCHER_DSL = "FetcherDsl"

private fun lsiClassName(
    packageName: String,
    vararg simpleNames: String,
): LsiClassName =
    LsiClassName(
        packageName = packageName,
        simpleNames = simpleNames.toList(),
    )

val CLASS_CLASS_NAME = lsiClassName("kotlin.reflect", "KClass")
val CLONEABLE_CLASS_NAME = lsiClassName("kotlin", "Cloneable")
val SERIALIZABLE_CLASS_NAME = lsiClassName("java.io", "Serializable")
val DESCRIPTION_CLASS_NAME = lsiClassName("org.babyfish.jimmer.client", "Description")
val JVM_STATIC_CLASS_NAME = lsiClassName("kotlin.jvm", "JvmStatic")
val COLLECTIONS_CLASS_NAME = lsiClassName("java.util", "Collections")

/**
 * 覆盖来源：project/compiler/error|dto|immutable|client/... 所有经由 `JSON_*_CLASS_NAME` 取 Jackson 注解类名的生成路径
 * 迁移说明：这里只保留类名语义，不再把 shared 层绑定到 KotlinPoet carrier。
 */
val JSON_IGNORE_CLASS_NAME = lsiClassName("com.fasterxml.jackson.annotation", "JsonIgnore")
val JSON_PROPERTY_CLASS_NAME = lsiClassName("com.fasterxml.jackson.annotation", "JsonProperty")
val JSON_PROPERTY_ORDER_CLASS_NAME = lsiClassName("com.fasterxml.jackson.annotation", "JsonPropertyOrder")
val JSON_CREATOR_CLASS_NAME = lsiClassName("com.fasterxml.jackson.annotation", "JsonCreator")
val JSON_SERIALIZE_CLASS_NAME = lsiClassName("com.fasterxml.jackson.databind.annotation", "JsonSerialize")
val JSON_DESERIALIZE_CLASS_NAME = lsiClassName("com.fasterxml.jackson.databind.annotation", "JsonDeserialize")
val JSON_POJO_BUILDER_CLASS_NAME = lsiClassName("com.fasterxml.jackson.databind.annotation", "JsonPOJOBuilder")
val JSON_NAMING_CLASS_NAME = lsiClassName("com.fasterxml.jackson.databind.annotation", "JsonNaming")
val FIXED_INPUT_FIELD_CLASS_NAME = lsiClassName("org.babyfish.jimmer.internal", "FixedInputField")
val CLIENT_EXCEPTION_CLASS_NAME = lsiClassName("org.babyfish.jimmer", "ClientException")
val VIEW_CLASS_NAME = lsiClassName("org.babyfish.jimmer", "View")
val INPUT_CLASS_NAME = lsiClassName("org.babyfish.jimmer", "Input")
val EMBEDDED_DTO_CLASS_NAME = lsiClassName("org.babyfish.jimmer", "EmbeddableDto")
val DTO_METADATA_CLASS_NAME = lsiClassName("org.babyfish.jimmer.sql.fetcher", "DtoMetadata")
val DTO_PROP_ACCESSOR = lsiClassName("org.babyfish.jimmer.impl.util", "DtoPropAccessor")
val INTERNAL_TYPE_CLASS_NAME = lsiClassName("org.babyfish.jimmer", "Internal")
val IMMUTABLE_PROP_CATEGORY_CLASS_NAME = IMMUTABLE_PROP_CATEGORY_LSI_CLASS_NAME
val IMMUTABLE_TYPE_CLASS_NAME = IMMUTABLE_TYPE_LSI_CLASS_NAME
val DRAFT_CONSUMER_CLASS_NAME = DRAFT_CONSUMER_LSI_CLASS_NAME
val TYPED_PROP_CLASS_NAME = TYPED_PROP_LSI_CLASS_NAME
val TYPED_PROP_SCALAR_CLASS_NAME = TYPED_PROP_SCALAR_LSI_CLASS_NAME
val TYPED_PROP_SCALAR_LIST_CLASS_NAME = TYPED_PROP_SCALAR_LIST_LSI_CLASS_NAME
val TYPED_PROP_REFERENCE_CLASS_NAME = TYPED_PROP_REFERENCE_LSI_CLASS_NAME
val TYPED_PROP_REFERENCE_LIST_CLASS_NAME = TYPED_PROP_REFERENCE_LIST_LSI_CLASS_NAME
val IMMUTABLE_SPI_CLASS_NAME = IMMUTABLE_SPI_LSI_CLASS_NAME
val IMMUTABLE_OBJECTS_CLASS_NAME = IMMUTABLE_OBJECTS_LSI_CLASS_NAME
val UNLOADED_EXCEPTION_CLASS_NAME = lsiClassName("org.babyfish.jimmer", "UnloadedException")
val SYSTEM_CLASS_NAME = lsiClassName("java.lang", "System")
val DRAFT_CLASS_NAME = DRAFT_CLASS_LSI_CLASS_NAME
val DRAFT_SPI_CLASS_NAME = DRAFT_SPI_LSI_CLASS_NAME
val DRAFT_CONTEXT_CLASS_NAME = DRAFT_CONTEXT_LSI_CLASS_NAME
val NON_SHARED_LIST_CLASS_NAME = NON_SHARED_LIST_LSI_CLASS_NAME
val VISIBILITY_CLASS_NAME = VISIBILITY_LSI_CLASS_NAME
val PROP_ID_CLASS_NAME = PROP_ID_LSI_CLASS_NAME
val CIRCULAR_REFERENCE_EXCEPTION_CLASS_NAME = CIRCULAR_REFERENCE_EXCEPTION_LSI_CLASS_NAME
val IMMUTABLE_CREATOR_CLASS_NAME = IMMUTABLE_CREATOR_LSI_CLASS_NAME
val DSL_SCOPE_CLASS_NAME = DSL_SCOPE_LSI_CLASS_NAME
val BIG_DECIMAL_CLASS_NAME = lsiClassName("java.math", "BigDecimal")
val BIG_INTEGER_CLASS_NAME = lsiClassName("java.math", "BigInteger")
val PATTERN_CLASS_NAME = JAVA_PATTERN_LSI_CLASS_NAME
val VALIDATOR_CLASS_NAME = VALIDATOR_LSI_CLASS_NAME
val ONE_TO_ONE_CLASS_NAME = ONE_TO_ONE_LSI_CLASS_NAME
val MANY_TO_ONE_CLASS_NAME = MANY_TO_ONE_LSI_CLASS_NAME
val ONE_TO_MANY_CLASS_NAME = ONE_TO_MANY_LSI_CLASS_NAME
val MANY_TO_MANY_CLASS_NAME = MANY_TO_MANY_LSI_CLASS_NAME
val ID_VIEW_CLASS_NAME = lsiClassName("org.babyfish.jimmer.sql.collection", "IdViewList")
val MUTABLE_ID_VIEW_CLASS_NAME = MUTABLE_ID_VIEW_LSI_CLASS_NAME
val MANY_TO_MANY_VIEW_CLASS_NAME = MANY_TO_MANY_VIEW_LSI_CLASS_NAME
val MANY_TO_MANY_VIEW_LIST_CLASS_NAME = lsiClassName("org.babyfish.jimmer.sql.collection", "ManyToManyViewList")
val LOCAL_DATE_CLASS_NAME = lsiClassName("java.time", "LocalDate")
val LOCAL_DATE_TIME_CLASS_NAME = lsiClassName("java.time", "LocalDateTime")
val LOCAL_TIME_CLASS_NAME = lsiClassName("java.time", "LocalTime")
val INSTANT_CLASS_NAME = lsiClassName("java.time", "Instant")
val K_PROPS_CLASS_NAME = K_PROPS_LSI_CLASS_NAME
val K_NON_NULL_PROPS_CLASS_NAME = K_NON_NULL_PROPS_LSI_CLASS_NAME
val K_NULLABLE_PROPS_CLASS_NAME = K_NULLABLE_PROPS_LSI_CLASS_NAME
val K_NON_NULL_TABLE_CLASS_NAME = K_NON_NULL_TABLE_LSI_CLASS_NAME
val K_NULLABLE_TABLE_CLASS_NAME = K_NULLABLE_TABLE_LSI_CLASS_NAME
val K_NON_NULL_REMOTE_REF = K_NON_NULL_REMOTE_REF_LSI_CLASS_NAME
val K_NULLABLE_REMOTE_REF = K_NULLABLE_REMOTE_REF_LSI_CLASS_NAME
val K_REMOTE_REF = K_REMOTE_REF_LSI_CLASS_NAME
val K_REMOTE_REF_IMPLEMENTOR = K_REMOTE_REF_IMPLEMENTOR_LSI_CLASS_NAME
val K_NON_NULL_TABLE_EX_CLASS_NAME = K_NON_NULL_TABLE_EX_LSI_CLASS_NAME
val K_NULLABLE_TABLE_EX_CLASS_NAME = K_NULLABLE_TABLE_EX_LSI_CLASS_NAME
val K_IMPLICIT_SUB_QUERY_TABLE_CLASS_NAME = K_IMPLICIT_SUB_QUERY_TABLE_LSI_CLASS_NAME
val K_NONNULL_EXPRESSION = K_NONNULL_EXPRESSION_LSI_CLASS_NAME
val K_TABLE_EX_CLASS_NAME = K_TABLE_EX_LSI_CLASS_NAME
val K_NON_NULL_PROP_EXPRESSION = K_NON_NULL_PROP_EXPRESSION_LSI_CLASS_NAME
val K_NULLABLE_PROP_EXPRESSION = K_NULLABLE_PROP_EXPRESSION_LSI_CLASS_NAME
val K_NON_NULL_EMBEDDED_PROP_EXPRESSION = K_NON_NULL_EMBEDDED_PROP_EXPRESSION_LSI_CLASS_NAME
val K_NULLABLE_EMBEDDED_PROP_EXPRESSION = K_NULLABLE_EMBEDDED_PROP_EXPRESSION_LSI_CLASS_NAME
val K_EMBEDDED_PROP_EXPRESSION = K_EMBEDDED_PROP_EXPRESSION_LSI_CLASS_NAME
val FETCHER_CLASS_NAME = FETCHER_LSI_CLASS_NAME
val FETCHER_IMPL_CLASS_NAME = FETCHER_IMPL_LSI_CLASS_NAME
val JAVA_FIELD_CONFIG_UTILS_CLASS_NAME = JAVA_FIELD_CONFIG_UTILS_LSI_CLASS_NAME
val K_FIELD_DSL = K_FIELD_DSL_LSI_CLASS_NAME
val K_REFERENCE_FIELD_DSL = K_REFERENCE_FIELD_DSL_LSI_CLASS_NAME
val K_LIST_FIELD_DSL = K_LIST_FIELD_DSL_LSI_CLASS_NAME
val K_RECURSIVE_REFERENCE_FIELD_DSL = K_RECURSIVE_REFERENCE_FIELD_DSL_LSI_CLASS_NAME
val K_RECURSIVE_LIST_FIELD_DSL = K_RECURSIVE_LIST_FIELD_DSL_LSI_CLASS_NAME
val FETCHER_CREATOR_CLASS_NAME = FETCHER_CREATOR_LSI_CLASS_NAME
val ID_ONLY_FETCH_TYPE_CLASS_NAME = ID_ONLY_FETCH_TYPE_LSI_CLASS_NAME
val REFERENCE_FETCH_TYPE_CLASS_NAME = REFERENCE_FETCH_TYPE_LSI_CLASS_NAME
val SELECTION_CLASS_NAME = SELECTION_LSI_CLASS_NAME
