package org.babyfish.jimmer.ksp.immutable.generator

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.asClassName
import org.babyfish.jimmer.*
import org.babyfish.jimmer.client.Description
import org.babyfish.jimmer.impl.util.DtoPropAccessor
import org.babyfish.jimmer.impl.validation.Validator
import org.babyfish.jimmer.internal.FixedInputField
import org.babyfish.jimmer.internal.GeneratedBy
import org.babyfish.jimmer.meta.ImmutablePropCategory
import org.babyfish.jimmer.meta.ImmutableType
import org.babyfish.jimmer.meta.PropId
import org.babyfish.jimmer.meta.TypedProp
import org.babyfish.jimmer.runtime.*
import org.babyfish.jimmer.sql.*
import org.babyfish.jimmer.sql.collection.IdViewList
import org.babyfish.jimmer.sql.collection.ManyToManyViewList
import org.babyfish.jimmer.sql.collection.MutableIdViewList
import java.io.Serializable
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Arrays
import java.util.Collections
import java.util.regex.Pattern

const val DRAFT = "Draft"
const val PRODUCER = "$"
const val IMPLEMENTOR = "Implementor"
const val IMPL = "Impl"
const val DRAFT_IMPL = "DraftImpl"
const val DRAFT_FIELD_EMAIL_PATTERN = "__email_pattern"
const val PROPS = "Props"
const val FETCHER = "Fetcher"
const val FETCHER_DSL = "FetcherDsl"

val CLASS_CLASS_NAME = Class::class.asClassName()
internal val CLONEABLE_CLASS_NAME = Cloneable::class.asClassName()
internal val SERIALIZABLE_CLASS_NAME = Serializable::class.asClassName()
val DESCRIPTION_CLASS_NAME = Description::class.asClassName()
val JVM_STATIC_CLASS_NAME = JvmStatic::class.asClassName()
val COLLECTIONS_CLASS_NAME = Collections::class.asClassName()
val JSON_IGNORE_CLASS_NAME = JsonIgnore::class.asClassName()
val JSON_PROPERTY_CLASS_NAME = JsonProperty::class.asClassName()
internal val JSON_PROPERTY_ORDER_CLASS_NAME = JsonPropertyOrder::class.asClassName()
val JSON_CREATOR_CLASS_NAME = JsonCreator::class.asClassName()
val JSON_SERIALIZE_CLASS_NAME = JsonSerialize::class.asClassName()
val JSON_DESERIALIZE_CLASS_NAME = JsonDeserialize::class.asClassName()
val JSON_POJO_BUILDER_CLASS_NAME = JsonPOJOBuilder::class.asClassName()
val JSON_NAMING_CLASS_NAME = JsonNaming::class.asClassName()
val GENERATED_BY_CLASS_NAME = GeneratedBy::class.asClassName()
val FIXED_INPUT_FIELD_CLASS_NAME = FixedInputField::class.asClassName()
 val CLIENT_EXCEPTION_CLASS_NAME = ClientException::class.asClassName()
val VIEW_CLASS_NAME = View::class.asClassName()
val INPUT_CLASS_NAME = Input::class.asClassName()
val EMBEDDED_DTO_CLASS_NAME = EmbeddableDto::class.asClassName()
val DTO_METADATA_CLASS_NAME = ClassName(
    "org.babyfish.jimmer.sql.fetcher",
    "DtoMetadata"
)
val DTO_PROP_ACCESSOR = DtoPropAccessor::class.asClassName()
internal val INTERNAL_TYPE_CLASS_NAME = Internal::class.asClassName()
internal val IMMUTABLE_PROP_CATEGORY_CLASS_NAME = ImmutablePropCategory::class.asClassName()
internal val IMMUTABLE_TYPE_CLASS_NAME = ImmutableType::class.asClassName()
internal val DRAFT_CONSUMER_CLASS_NAME = DraftConsumer::class.asClassName()
internal val TYPED_PROP_CLASS_NAME = TypedProp::class.asClassName()
internal val TYPED_PROP_SCALAR_CLASS_NAME = TypedProp.Scalar::class.asClassName()
internal val TYPED_PROP_SCALAR_LIST_CLASS_NAME = TypedProp.ScalarList::class.asClassName()
internal val TYPED_PROP_REFERENCE_CLASS_NAME = TypedProp.Reference::class.asClassName()
internal val TYPED_PROP_REFERENCE_LIST_CLASS_NAME = TypedProp.ReferenceList::class.asClassName()
val IMMUTABLE_SPI_CLASS_NAME = ImmutableSpi::class.asClassName()
 val IMMUTABLE_OBJECTS_CLASS_NAME = ImmutableObjects::class.asClassName()
internal val UNLOADED_EXCEPTION_CLASS_NAME = UnloadedException::class.asClassName()
internal val SYSTEM_CLASS_NAME = System::class.asClassName()
val DRAFT_CLASS_NAME = Draft::class.asClassName()
val DRAFT_SPI_CLASS_NAME = DraftSpi::class.asClassName()
val DRAFT_CONTEXT_CLASS_NAME = DraftContext::class.asClassName()
val NON_SHARED_LIST_CLASS_NAME = NonSharedList::class.asClassName()
val VISIBILITY_CLASS_NAME = Visibility::class.asClassName()
val PROP_ID_CLASS_NAME = PropId::class.asClassName()
val CIRCULAR_REFERENCE_EXCEPTION_CLASS_NAME = CircularReferenceException::class.asClassName()
val IMMUTABLE_CREATOR_CLASS_NAME = ClassName("org.babyfish.jimmer.kt", "ImmutableCreator")
val DSL_SCOPE_CLASS_NAME = ClassName("org.babyfish.jimmer.kt", "DslScope")
val BIG_DECIMAL_CLASS_NAME = BigDecimal::class.asClassName()
val BIG_INTEGER_CLASS_NAME = BigInteger::class.asClassName()
val PATTERN_CLASS_NAME = Pattern::class.asClassName()
val VALIDATOR_CLASS_NAME = Validator::class.asClassName()
internal val ONE_TO_ONE_CLASS_NAME = OneToOne::class.asClassName()
internal val MANY_TO_ONE_CLASS_NAME = ManyToOne::class.asClassName()
internal val ONE_TO_MANY_CLASS_NAME = OneToMany::class.asClassName()
internal val MANY_TO_MANY_CLASS_NAME = ManyToMany::class.asClassName()
internal val ID_VIEW_CLASS_NAME = IdViewList::class.asClassName()
val MUTABLE_ID_VIEW_CLASS_NAME = MutableIdViewList::class.asClassName()
internal val MANY_TO_MANY_VIEW_CLASS_NAME = ManyToManyView::class.asClassName()
internal val MANY_TO_MANY_VIEW_LIST_CLASS_NAME = ManyToManyViewList::class.asClassName()
val LOCAL_DATE_CLASS_NAME = LocalDate::class.asClassName()
val LOCAL_DATE_TIME_CLASS_NAME = LocalDateTime::class.asClassName()
val LOCAL_TIME_CLASS_NAME = LocalTime::class.asClassName()
val INSTANT_CLASS_NAME = Instant::class.asClassName()
internal val K_PROPS_CLASS_NAME = ClassName(
    "org.babyfish.jimmer.sql.kt.ast.table",
    "KProps"
)
internal val K_NON_NULL_PROPS_CLASS_NAME = ClassName(
    "org.babyfish.jimmer.sql.kt.ast.table",
    "KNonNullProps"
)
internal val K_NULLABLE_PROPS_CLASS_NAME = ClassName(
    "org.babyfish.jimmer.sql.kt.ast.table",
    "KNullableProps"
)
internal val K_NON_NULL_TABLE_CLASS_NAME = ClassName(
    "org.babyfish.jimmer.sql.kt.ast.table",
    "KNonNullTable"
)
internal val K_NULLABLE_TABLE_CLASS_NAME = ClassName(
    "org.babyfish.jimmer.sql.kt.ast.table",
    "KNullableTable"
)
val K_NON_NULL_REMOTE_REF = ClassName(

    "org.babyfish.jimmer.sql.kt.ast.table",
    "KRemoteRef", "NonNull"
)
val K_NULLABLE_REMOTE_REF = ClassName(
    "org.babyfish.jimmer.sql.kt.ast.table",
    "KRemoteRef", "Nullable"
)
internal val K_REMOTE_REF = ClassName(
    "org.babyfish.jimmer.sql.kt.ast.table",
    "KRemoteRef"
)
internal val K_REMOTE_REF_IMPLEMENTOR = ClassName(
    "org.babyfish.jimmer.sql.kt.ast.table.impl",
    "KRemoteRefImplementor"
)
internal val K_NON_NULL_TABLE_EX_CLASS_NAME = ClassName(
    "org.babyfish.jimmer.sql.kt.ast.table",
    "KNonNullTableEx"
)
internal val K_NULLABLE_TABLE_EX_CLASS_NAME = ClassName(
    "org.babyfish.jimmer.sql.kt.ast.table",
    "KNullableTableEx"
)
internal val K_IMPLICIT_SUB_QUERY_TABLE_CLASS_NAME = ClassName(
    "org.babyfish.jimmer.sql.kt.ast.table",
    "KImplicitSubQueryTable"
)
internal val K_NONNULL_EXPRESSION = ClassName(
    "org.babyfish.jimmer.sql.kt.ast.expression",
    "KNonNullExpression"
)
internal val K_TABLE_EX_CLASS_NAME = ClassName(
    "org.babyfish.jimmer.sql.kt.ast.table",
    "KTableEx"
)
val K_NON_NULL_PROP_EXPRESSION = ClassName(
    "org.babyfish.jimmer.sql.kt.ast.expression",
    "KNonNullPropExpression"
)
val K_NULLABLE_PROP_EXPRESSION = ClassName(
    "org.babyfish.jimmer.sql.kt.ast.expression",
    "KNullablePropExpression"
)
val K_NON_NULL_EMBEDDED_PROP_EXPRESSION = ClassName(
    "org.babyfish.jimmer.sql.kt.ast.expression",
    "KNonNullEmbeddedPropExpression"
)
val K_NULLABLE_EMBEDDED_PROP_EXPRESSION = ClassName(
    "org.babyfish.jimmer.sql.kt.ast.expression",
    "KNullableEmbeddedPropExpression"
)
val K_EMBEDDED_PROP_EXPRESSION = ClassName(
    "org.babyfish.jimmer.sql.kt.ast.expression",
    "KEmbeddedPropExpression"
)
val FETCHER_CLASS_NAME = ClassName(
    "org.babyfish.jimmer.sql.fetcher",
    "Fetcher"
)
internal val FETCHER_IMPL_CLASS_NAME = ClassName(
    "org.babyfish.jimmer.sql.fetcher.impl",
    "FetcherImpl"
)
val JAVA_FIELD_CONFIG_UTILS_CLASS_NAME = ClassName(
    "org.babyfish.jimmer.sql.kt.fetcher.impl",
    "JavaFieldConfigUtils"
)
val K_FIELD_DSL = ClassName(
    "org.babyfish.jimmer.sql.kt.fetcher",
    "KFieldDsl"
)
val K_REFERENCE_FIELD_DSL = ClassName(
    "org.babyfish.jimmer.sql.kt.fetcher",
    "KReferenceFieldDsl"
)
val K_LIST_FIELD_DSL = ClassName(
    "org.babyfish.jimmer.sql.kt.fetcher",
    "KListFieldDsl"
)
val K_RECURSIVE_REFERENCE_FIELD_DSL = ClassName(
    "org.babyfish.jimmer.sql.kt.fetcher",
    "KRecursiveReferenceFieldDsl"
)
val K_RECURSIVE_LIST_FIELD_DSL = ClassName(
    "org.babyfish.jimmer.sql.kt.fetcher",
    "KRecursiveListFieldDsl"
)
internal val FETCHER_CREATOR_CLASS_NAME = ClassName(
    "org.babyfish.jimmer.sql.kt.fetcher",
    "FetcherCreator"
)
val ID_ONLY_FETCH_TYPE_CLASS_NAME = ClassName(
    "org.babyfish.jimmer.sql.fetcher",
    "IdOnlyFetchType"
)
val REFERENCE_FETCH_TYPE_CLASS_NAME = ClassName(
    "org.babyfish.jimmer.sql.fetcher",
    "ReferenceFetchType"
)
val SELECTION_CLASS_NAME =
    ClassName(
        "org.babyfish.jimmer.sql.ast",
        "Selection"
    )

val NEW_FETCHER_FUN_CLASS_NAME =
    ClassName(
        "org.babyfish.jimmer.sql.kt.fetcher",
        "newFetcher"
    )

val ENTITY_MANAGER_CLASS_NAME =
    ClassName(
        "org.babyfish.jimmer.sql.runtime",
        "EntityManager"
    )

val K_SPECIFICATION_CLASS_NAME =
    ClassName(
        "org.babyfish.jimmer.sql.kt.ast.query.specification",
        "KSpecification"
    )

val K_SPECIFICATION_ARGS_CLASS_NAME =
    ClassName(
        "org.babyfish.jimmer.sql.kt.ast.query.specification",
        "KSpecificationArgs"
    )

val PREDICATE_APPLIER =
    ClassName(
        "org.babyfish.jimmer.sql.ast.query.specification",
        "PredicateApplier"
    )

val HIBERNATE_VALIDATOR_ENHANCED_BEAN = ClassName(
    "org.hibernate.validator.engine",
    "HibernateValidatorEnhancedBean"
)

internal val PROPAGATION_CLASS_NAME = ClassName(
    "org.babyfish.jimmer.sql.transaction",
    "Propagation"
)

val TUPLE_MAPPER_CLASS_NAME = ClassName(
    "org.babyfish.jimmer.sql.runtime",
    "TupleMapper"
)

const val KEY_FULL_NAME = "org.babyfish.jimmer.sql.Key"
const val JIMMER_MODULE = "JimmerModule"

const val UNMODIFIED = "(__modified ?: __base!!)"
const val MODIFIED = "(__modified ?: __base!!.clone())\n.also { __modified = it }"

const val EMAIL_PATTERN = "^[^@]+@[^@]+\$"
const val FROZEN_EXCEPTION_MESSAGE = "The current draft has been resolved so it cannot be modified"
