package site.addzero.lsi.codegen

private fun lsiClassName(
    packageName: String,
    vararg simpleNames: String,
): LsiClassName =
    LsiClassName(
        packageName = packageName,
        simpleNames = simpleNames.toList(),
    )

val TYPED_PROP_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.meta", "TypedProp")
val TYPED_PROP_SCALAR_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.meta", "TypedProp", "Scalar")
val TYPED_PROP_SCALAR_LIST_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.meta", "TypedProp", "ScalarList")
val TYPED_PROP_REFERENCE_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.meta", "TypedProp", "Reference")
val TYPED_PROP_REFERENCE_LIST_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.meta", "TypedProp", "ReferenceList")

val NON_SHARED_LIST_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.runtime", "NonSharedList")

val IMMUTABLE_CREATOR_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.kt", "ImmutableCreator")
val DSL_SCOPE_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.kt", "DslScope")

val ONE_TO_ONE_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql", "OneToOne")
val MANY_TO_ONE_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql", "ManyToOne")
val ONE_TO_MANY_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql", "OneToMany")
val MANY_TO_MANY_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql", "ManyToMany")
val MANY_TO_MANY_VIEW_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql", "ManyToManyView")

val K_PROPS_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.kt.ast.table", "KProps")
val K_NON_NULL_PROPS_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.kt.ast.table", "KNonNullProps")
val K_NULLABLE_PROPS_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.kt.ast.table", "KNullableProps")
val K_NON_NULL_TABLE_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.kt.ast.table", "KNonNullTable")
val K_NULLABLE_TABLE_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.kt.ast.table", "KNullableTable")
val K_NON_NULL_REMOTE_REF_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.kt.ast.table", "KRemoteRef", "NonNull")
val K_NULLABLE_REMOTE_REF_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.kt.ast.table", "KRemoteRef", "Nullable")
val K_REMOTE_REF_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.kt.ast.table", "KRemoteRef")
val K_REMOTE_REF_IMPLEMENTOR_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.kt.ast.table.impl", "KRemoteRefImplementor")
val K_NON_NULL_TABLE_EX_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.kt.ast.table", "KNonNullTableEx")
val K_NULLABLE_TABLE_EX_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.kt.ast.table", "KNullableTableEx")
val K_IMPLICIT_SUB_QUERY_TABLE_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.kt.ast.table", "KImplicitSubQueryTable")
val K_NONNULL_EXPRESSION_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.kt.ast.expression", "KNonNullExpression")
val K_TABLE_EX_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.kt.ast.table", "KTableEx")
val K_NON_NULL_PROP_EXPRESSION_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.kt.ast.expression", "KNonNullPropExpression")
val K_NULLABLE_PROP_EXPRESSION_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.kt.ast.expression", "KNullablePropExpression")
val K_NON_NULL_EMBEDDED_PROP_EXPRESSION_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.kt.ast.expression", "KNonNullEmbeddedPropExpression")
val K_NULLABLE_EMBEDDED_PROP_EXPRESSION_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.kt.ast.expression", "KNullableEmbeddedPropExpression")
val K_EMBEDDED_PROP_EXPRESSION_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.kt.ast.expression", "KEmbeddedPropExpression")

val FETCHER_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.fetcher", "Fetcher")
val ABSTRACT_TYPED_FETCHER_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.fetcher.spi", "AbstractTypedFetcher")
val FETCHER_IMPL_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.fetcher.impl", "FetcherImpl")
val FIELD_CONFIG_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.fetcher", "FieldConfig")
val REFERENCE_FIELD_CONFIG_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.fetcher", "ReferenceFieldConfig")
val LIST_FIELD_CONFIG_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.fetcher", "ListFieldConfig")
val RECURSIVE_REFERENCE_FIELD_CONFIG_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.fetcher", "RecursiveReferenceFieldConfig")
val RECURSIVE_LIST_FIELD_CONFIG_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.fetcher", "RecursiveListFieldConfig")
val JAVA_FIELD_CONFIG_UTILS_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.kt.fetcher.impl", "JavaFieldConfigUtils")
val K_FIELD_DSL_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.kt.fetcher", "KFieldDsl")
val K_REFERENCE_FIELD_DSL_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.kt.fetcher", "KReferenceFieldDsl")
val K_LIST_FIELD_DSL_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.kt.fetcher", "KListFieldDsl")
val K_RECURSIVE_REFERENCE_FIELD_DSL_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.kt.fetcher", "KRecursiveReferenceFieldDsl")
val K_RECURSIVE_LIST_FIELD_DSL_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.kt.fetcher", "KRecursiveListFieldDsl")
val FETCHER_CREATOR_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.kt.fetcher", "FetcherCreator")
val ID_ONLY_FETCH_TYPE_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.fetcher", "IdOnlyFetchType")
val REFERENCE_FETCH_TYPE_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.fetcher", "ReferenceFetchType")
val NEW_CHAIN_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.lang", "NewChain")
val IMMUTABLE_TYPE_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.meta", "ImmutableType")
val IMMUTABLE_PROP_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.meta", "ImmutableProp")
val IMMUTABLE_PROP_CATEGORY_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.meta", "ImmutablePropCategory")
val IMMUTABLE_SPI_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.runtime", "ImmutableSpi")
val IMMUTABLE_OBJECTS_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer", "ImmutableObjects")
val PROP_ID_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.meta", "PropId")
val DRAFT_CLASS_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer", "Draft")
val DRAFT_CONSUMER_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer", "DraftConsumer")
val DRAFT_SPI_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.runtime", "DraftSpi")
val DRAFT_CONTEXT_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.runtime", "DraftContext")
val INTERNAL_TYPE_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.runtime", "Internal")
val VISIBILITY_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.runtime", "Visibility")
val CIRCULAR_REFERENCE_EXCEPTION_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer", "CircularReferenceException")
val JAVA_STRING_LSI_CLASS_NAME =
    lsiClassName("java.lang", "String")
val JAVA_CLASS_LSI_CLASS_NAME =
    lsiClassName("java.lang", "Class")
val JAVA_SUPPRESS_WARNINGS_LSI_CLASS_NAME =
    lsiClassName("java.lang", "SuppressWarnings")
val SELECTION_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.ast", "Selection")
val JOIN_TYPE_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql", "JoinType")
val PREDICATE_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.ast", "Predicate")
val FUNCTION_LSI_CLASS_NAME =
    lsiClassName("java.util.function", "Function")
val CONSUMER_LSI_CLASS_NAME =
    lsiClassName("java.util.function", "Consumer")
val TABLE_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.ast.table", "Table")
val BASE_TABLE_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.ast.table", "BaseTable")
val TABLE_EX_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.ast.table", "TableEx")
val TABLE_LIKE_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.ast.table.spi", "TableLike")
val TABLE_IMPLEMENTOR_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.ast.impl.table", "TableImplementor")
val TABLE_EX_PROXY_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.ast.table.spi", "TableExProxy")
val ABSTRACT_TYPED_TABLE_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.ast.table.spi", "AbstractTypedTable")
val DELAYED_OPERATION_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.ast.table.spi", "AbstractTypedTable", "DelayedOperation")
val WEAK_JOIN_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.ast.table", "WeakJoin")
val WEAK_JOIN_HANDLE_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.ast.impl.table", "WeakJoinHandle")
val WEAK_JOIN_LAMBDA_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.ast.impl.table", "WeakJoinLambda")
val J_WEAK_JOIN_LAMBDA_FACTORY_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.ast.impl.table", "JWeakJoinLambdaFactory")
val BASE_TABLE_SYMBOL_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.ast.impl.base", "BaseTableSymbol")
val BASE_TABLE_SYMBOLS_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.ast.impl.base", "BaseTableSymbols")
val TABLE_PROXIES_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.ast.impl.table", "TableProxies")
val PROP_EXPRESSION_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.ast", "PropExpression")
val PROP_STRING_EXPRESSION_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.ast", "PropExpression", "Str")
val PROP_NUMERIC_EXPRESSION_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.ast", "PropExpression", "Num")
val PROP_DATE_EXPRESSION_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.ast", "PropExpression", "Dt")
val PROP_TEMPORAL_EXPRESSION_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.ast", "PropExpression", "Tp")
val PROP_COMPARABLE_EXPRESSION_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.ast", "PropExpression", "Cmp")
val EMBEDDED_PROP_EXPRESSION_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.ast", "PropExpression", "Embedded")
val ABSTRACT_TYPED_EMBEDDED_PROP_EXPRESSION_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.ast.embedded", "AbstractTypedEmbeddedPropExpression")
val BASE_TABLE_OWNER_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.ast.impl.base", "BaseTableOwner")
val NEW_FETCHER_FUN_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.kt.fetcher", "newFetcher")
val VALIDATOR_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.impl.validation", "Validator")
val MUTABLE_ID_VIEW_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.collection", "MutableIdViewList")
val ENTITY_MANAGER_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.sql.runtime", "EntityManager")

val KOTLIN_BOOLEAN_LSI_CLASS_NAME =
    lsiClassName("kotlin", "Boolean")
val KOTLIN_BYTE_LSI_CLASS_NAME =
    lsiClassName("kotlin", "Byte")
val KOTLIN_SHORT_LSI_CLASS_NAME =
    lsiClassName("kotlin", "Short")
val KOTLIN_INT_LSI_CLASS_NAME =
    lsiClassName("kotlin", "Int")
val KOTLIN_LONG_LSI_CLASS_NAME =
    lsiClassName("kotlin", "Long")
val KOTLIN_FLOAT_LSI_CLASS_NAME =
    lsiClassName("kotlin", "Float")
val KOTLIN_DOUBLE_LSI_CLASS_NAME =
    lsiClassName("kotlin", "Double")
val KOTLIN_CHAR_LSI_CLASS_NAME =
    lsiClassName("kotlin", "Char")
val KOTLIN_STRING_LSI_CLASS_NAME =
    lsiClassName("kotlin", "String")
val KOTLIN_ANY_LSI_CLASS_NAME =
    lsiClassName("kotlin", "Any")
val KOTLIN_CLONEABLE_LSI_CLASS_NAME =
    lsiClassName("kotlin", "Cloneable")
val KOTLIN_UNIT_LSI_CLASS_NAME =
    lsiClassName("kotlin", "Unit")
val KOTLIN_LIST_LSI_CLASS_NAME =
    lsiClassName("kotlin.collections", "List")
val JAVA_ARRAY_LIST_LSI_CLASS_NAME =
    lsiClassName("java.util", "ArrayList")
val JAVA_SYSTEM_LSI_CLASS_NAME =
    lsiClassName("java.lang", "System")
val JAVA_SERIALIZABLE_LSI_CLASS_NAME =
    lsiClassName("java.io", "Serializable")
val JAVA_ILLEGAL_ARGUMENT_EXCEPTION_LSI_CLASS_NAME =
    lsiClassName("java.lang", "IllegalArgumentException")
val JAVA_ILLEGAL_STATE_EXCEPTION_LSI_CLASS_NAME =
    lsiClassName("java.lang", "IllegalStateException")
val IMMUTABLE_MODULE_REQUIRED_EXCEPTION_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer.jackson", "ImmutableModuleRequiredException")
val UNLOADED_EXCEPTION_LSI_CLASS_NAME =
    lsiClassName("org.babyfish.jimmer", "UnloadedException")
val JAVA_PATTERN_LSI_CLASS_NAME =
    lsiClassName("java.util.regex", "Pattern")

const val JIMMER_MODULE = "JimmerModule"
const val EMAIL_PATTERN = "^[^@]+@[^@]+$"
const val FROZEN_EXCEPTION_MESSAGE = "The current draft has been resolved so it cannot be modified"
