package site.addzero.lsi.jimmer.meta

import site.addzero.lsi.jimmer.DEFAULT
import site.addzero.lsi.jimmer.EMBEDDABLE
import site.addzero.lsi.jimmer.ENTITY
import site.addzero.lsi.jimmer.FORMULA as FORMULA_ANNOTATION
import site.addzero.lsi.jimmer.ID as ID_ANNOTATION
import site.addzero.lsi.jimmer.ID_VIEW as ID_VIEW_ANNOTATION
import site.addzero.lsi.jimmer.IMMUTABLE
import site.addzero.lsi.jimmer.LOGICAL_DELETED as LOGICAL_DELETED_ANNOTATION
import site.addzero.lsi.jimmer.MANY_TO_MANY as MANY_TO_MANY_ANNOTATION
import site.addzero.lsi.jimmer.MANY_TO_MANY_VIEW as MANY_TO_MANY_VIEW_ANNOTATION
import site.addzero.lsi.jimmer.MANY_TO_ONE as MANY_TO_ONE_ANNOTATION
import site.addzero.lsi.jimmer.MAPPED_SUPERCLASS
import site.addzero.lsi.jimmer.ONE_TO_MANY as ONE_TO_MANY_ANNOTATION
import site.addzero.lsi.jimmer.ONE_TO_ONE as ONE_TO_ONE_ANNOTATION
import site.addzero.lsi.jimmer.SCALAR
import site.addzero.lsi.jimmer.TRANSIENT as TRANSIENT_ANNOTATION
import site.addzero.lsi.jimmer.VERSION as VERSION_ANNOTATION

/**
 * `ImmutableProp` 仍需沿用 `project/jimmer-core` 中的若干语义校验，
 * 但为了让 `jimmer-ksp-ext` 不再依赖 `:project:jimmer-core:compileJava` 的产物，
 * 先将当前 KSP 编译层真实用到的最小语义等价下沉到本地 Kotlin 实现。
 *
 * 覆盖来源：
 * - `project/jimmer-core/src/main/java/org/babyfish/jimmer/impl/util/Keywords.java`
 * - `project/jimmer-core/src/main/java/org/babyfish/jimmer/meta/impl/Utils.java`
 * - `project/jimmer-core/src/main/java/org/babyfish/jimmer/meta/impl/PropDescriptor.java`
 *
 * 迁移说明：
 * - 这里只保留 `ImmutableProp` 当前实际消费的差量行为，不引入 `LSI -> KSP/APT` 回桥。
 * - 语义输入统一改成注解 FQ 名/LSI 语义字符串，继续服务于 `KSP -> LSI` 单向迁移。
 */

internal val IMMUTABLE_PROP_ILLEGAL_NAMES: Set<String> = setOf(
    "hashCode",
    "equals",
    "toString",
    "__isLoaded",
    "__isVisible",
    "__get",
    "__hashCode",
    "__equals",
    "__type",
    "__unload",
    "__set",
    "__show",
    "__draftContext",
    "__resolve",
    "__isResolved",
    "toImmutable",
    "toEntity",
    "toMergedEntity",
    "unknownNonNullProperty",
    "unknownNullableProperty",
    "applyTo",
    "entityType",
    "immutableType",
    "eq",
    "null",
    "notNull",
    "count",
    "get",
    "associatedId",
    "__getAssociatedId",
    "join",
    "inverseJoin",
    "inverseGetAssociatedId",
    "exists",
    "fetch",
    "asTableEx",
    "__parent",
    "__prop",
    "__weakJoinHandle",
    "__isInverse",
    "__unwrap",
    "__beforeJoin",
    "__disableJoin",
    "joinOperation",
    "__joinType",
    "__refEquals",
    "__baseTableOwner",
    "allScalarFields",
    "allTableFields",
    "allReferenceFields",
    "add",
    "addRecursion",
    "remove",
    "createFetcher",
    "javaClass",
    "fieldMap",
    "__isSimpleFetcher",
    "__contains",
    "joinReference",
    "joinList",
    "outerJoin",
    "outerJoinReference",
    "outerJoinList",
    "inverseJoinReference",
    "inverseJoinList",
    "inverseOuterJoin",
    "inverseOuterJoinReference",
    "inverseOuterJoinList",
    "weakJoin",
    "weakOuterJoin",
)

internal fun immutablePropDefaultViewBasePropName(isList: Boolean, name: String): String? =
    if (!isList &&
        name.length > 2 &&
        !name[name.length - 3].isUpperCase() &&
        name.endsWith("Id")
    ) {
        name.substring(0, name.length - 2)
    } else {
        null
    }

internal class ImmutablePropDescriptor(
    val type: Type,
    val isNullable: Boolean,
) {

    enum class Type(
        val annotationQualifiedName: String?,
        val isAssociation: Boolean,
    ) {
        TRANSIENT(TRANSIENT_ANNOTATION, false),
        ID(ID_ANNOTATION, false),
        VERSION(VERSION_ANNOTATION, false),
        LOGICAL_DELETED(LOGICAL_DELETED_ANNOTATION, false),
        FORMULA(FORMULA_ANNOTATION, false),
        BASIC(null, false),
        ONE_TO_ONE(ONE_TO_ONE_ANNOTATION, true),
        MANY_TO_ONE(MANY_TO_ONE_ANNOTATION, true),
        ONE_TO_MANY(ONE_TO_MANY_ANNOTATION, true),
        MANY_TO_MANY(MANY_TO_MANY_ANNOTATION, true),
        ID_VIEW(ID_VIEW_ANNOTATION, false),
        MANY_TO_MANY_VIEW(MANY_TO_MANY_VIEW_ANNOTATION, true),
        ;

        @Suppress("UNCHECKED_CAST")
        val annotationType: Class<out Annotation>?
            get() = annotationQualifiedName?.let { Class.forName(it) as Class<out Annotation> }

        override fun toString(): String =
            name.lowercase().replace('_', '-')
    }

    class Builder(
        private val isKotlinType: Boolean,
        private val typeText: String,
        private val typeAnnotationQualifiedName: String,
        private val propText: String,
        private val elementText: String,
        private val elementAnnotationQualifiedName: String?,
        private val isList: Boolean,
        private val explicitNullable: Boolean?,
        private val exceptionCreator: (String) -> RuntimeException,
    ) {

        private var annotationTypes: MutableSet<String>? = null

        private var explicitType: Type? = null

        private var implicitMap: MutableMap<Type, MutableSet<String>>? = null

        private var annotationNullity: AnnotationNullity? = null

        private var hasMappedBy: Boolean = false

        fun add(annotationTypeName: String): Builder {
            addAsSqlAnnotation(annotationTypeName)
            addAsNullityAnnotation(annotationTypeName)
            return this
        }

        fun hasMappedBy(): Builder {
            hasMappedBy = true
            return this
        }

        fun build(): ImmutablePropDescriptor {
            val annotationTypes = annotationTypes
            if (annotationTypes == null) {
                validateReturnType(Type.BASIC)
                return ImmutablePropDescriptor(
                    type = Type.BASIC,
                    isNullable = determineNullable(Type.BASIC),
                )
            }
            if (JOIN_COLUMNS_ANNOTATION in annotationTypes && JOIN_TABLE_ANNOTATION in annotationTypes) {
                conflict(JOIN_COLUMNS_ANNOTATION, JOIN_TABLE_ANNOTATION)
            }
            if (JOIN_COLUMN_ANNOTATION in annotationTypes && JOIN_TABLE_ANNOTATION in annotationTypes) {
                conflict(JOIN_COLUMN_ANNOTATION, JOIN_TABLE_ANNOTATION)
            }
            if (JOIN_COLUMNS_ANNOTATION in annotationTypes && JOIN_SQL_ANNOTATION in annotationTypes) {
                conflict(JOIN_COLUMNS_ANNOTATION, JOIN_SQL_ANNOTATION)
            }
            if (JOIN_COLUMN_ANNOTATION in annotationTypes && JOIN_SQL_ANNOTATION in annotationTypes) {
                conflict(JOIN_COLUMN_ANNOTATION, JOIN_SQL_ANNOTATION)
            }
            if (JOIN_TABLE_ANNOTATION in annotationTypes && JOIN_SQL_ANNOTATION in annotationTypes) {
                conflict(JOIN_TABLE_ANNOTATION, JOIN_SQL_ANNOTATION)
            }
            if (KEY_ANNOTATION in annotationTypes && JOIN_TABLE_ANNOTATION in annotationTypes) {
                conflict(KEY_ANNOTATION, JOIN_TABLE_ANNOTATION)
            }
            if (KEY_ANNOTATION in annotationTypes && JOIN_SQL_ANNOTATION in annotationTypes) {
                conflict(KEY_ANNOTATION, JOIN_SQL_ANNOTATION)
            }
            if (PROP_OVERRIDES_ANNOTATION in annotationTypes && COLUMN_ANNOTATION in annotationTypes) {
                conflict(PROP_OVERRIDES_ANNOTATION, COLUMN_ANNOTATION)
            }
            if (PROP_OVERRIDE_ANNOTATION in annotationTypes && COLUMN_ANNOTATION in annotationTypes) {
                conflict(PROP_OVERRIDE_ANNOTATION, COLUMN_ANNOTATION)
            }
            if (elementAnnotationQualifiedName == EMBEDDABLE && COLUMN_ANNOTATION in annotationTypes) {
                throw exceptionCreator(
                    "embedded property cannot be decorated by @$COLUMN_ANNOTATION"
                )
            }
            if (elementAnnotationQualifiedName != EMBEDDABLE && PROP_OVERRIDE_ANNOTATION in annotationTypes) {
                throw exceptionCreator(
                    "only embedded property cannot be decorated by @$PROP_OVERRIDE_ANNOTATION"
                )
            }
            if (elementAnnotationQualifiedName != EMBEDDABLE && PROP_OVERRIDES_ANNOTATION in annotationTypes) {
                throw exceptionCreator(
                    "only embedded property cannot be decorated by @$PROP_OVERRIDES_ANNOTATION"
                )
            }

            val type = when {
                explicitType != null -> explicitType!!
                implicitMap?.size == 1 -> implicitMap!!.keys.first()
                implicitMap?.containsKey(Type.BASIC) == true -> Type.BASIC
                else -> throw exceptionCreator(
                    "there are not enough annotations to determine that " +
                        "the current property belongs to one of the following types: " +
                        (implicitMap?.keys ?: emptySet<Type>())
                )
            }
            val expectedAnnotationTypes = FAMILY_MAP.getValue(type)
            for (annotationType in annotationTypes) {
                if (annotationType != type.annotationQualifiedName && annotationType !in expectedAnnotationTypes) {
                    throw exceptionCreator(
                        "the $type property cannot be decorated by @$annotationType"
                    )
                }
            }
            validateList(type)
            validateReturnType(type)
            val isNullable = determineNullable(type)
            if (hasMappedBy) {
                for (annotationType in annotationTypes) {
                    if (annotationType in ASSOCIATION_STORAGE_ANNOTATION_TYPES) {
                        throw exceptionCreator(
                            "it cannot be decorated by @$annotationType because another annotation " +
                                "@${type.annotationQualifiedName} has the argument `mappedBy`"
                        )
                    }
                }
                if (type == Type.ONE_TO_ONE && !isNullable) {
                    throw exceptionCreator(
                        "its annotation @${type.annotationQualifiedName} has the argument `mappedBy` " +
                            "so that it must be nullable"
                    )
                }
            }
            return ImmutablePropDescriptor(type, isNullable)
        }

        private fun addAsSqlAnnotation(annotationTypeName: String) {
            if (annotationTypeName !in SQL_ANNOTATION_TYPES) {
                return
            }
            val declaringTypes =
                if (annotationTypeName in VALUE_ANNOTATION_TYPES_FOR_DECLARING_CHECK) {
                    VALUE_ANNOTATION_TYPES
                } else {
                    REF_ANNOTATION_TYPES
                }
            if (annotationTypeName != SCALAR && typeAnnotationQualifiedName !in declaringTypes) {
                throw exceptionCreator(
                    "It cannot be decorated by @$annotationTypeName because the declaring type " +
                        "\"$typeText\" is not decorated by ${declaringTypes.toList()}"
                )
            }
            val annotationTypes = (annotationTypes ?: linkedSetOf<String>().also { annotationTypes = it })
            if (!annotationTypes.add(annotationTypeName)) {
                return
            }
            val type = TYPE_MAP[annotationTypeName]
            if (type != null && type in FAMILY_MAP) {
                if (explicitType != null) {
                    conflict(explicitType!!.annotationQualifiedName!!, annotationTypeName)
                }
                explicitType = type
                return
            }
            if (explicitType != null) {
                return
            }
            val candidates = INVERSE_MAP[annotationTypeName]
                ?: throw AssertionError(
                    "Internal bug: Can not determine primary annotation type by @$annotationTypeName"
                )
            val newImplicitMap = linkedMapOf<Type, MutableSet<String>>()
            for (implicitType in candidates) {
                newImplicitMap.getOrPut(implicitType) { linkedSetOf() }.add(annotationTypeName)
            }
            val implicitMap = this.implicitMap
            if (implicitMap == null) {
                this.implicitMap = newImplicitMap
                return
            }
            if (implicitMap.keys.none { it in newImplicitMap.keys }) {
                conflict(implicitMap.values.first().first(), annotationTypeName)
            }
            val iterator = implicitMap.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val newSet = newImplicitMap[entry.key]
                if (newSet == null) {
                    iterator.remove()
                } else {
                    entry.value += newSet
                }
            }
        }

        private fun addAsNullityAnnotation(annotationTypeName: String) {
            when {
                annotationTypeName.endsWith(".Null") ||
                    annotationTypeName.endsWith(".Nullable") ||
                    annotationTypeName == T_NULLABLE_ANNOTATION -> {
                    addNullityAnnotation(annotationTypeName, true)
                }

                annotationTypeName.endsWith(".NotNull") ||
                    annotationTypeName.endsWith(".NonNull") -> {
                    addNullityAnnotation(annotationTypeName, false)
                }
            }
        }

        private fun addNullityAnnotation(annotationTypeName: String, nullable: Boolean) {
            explicitNullable?.let { explicitNullable ->
                if (isKotlinType) {
                    throw exceptionCreator(
                        "it is unnecessary to use \"@$annotationTypeName\" in kotlin"
                    )
                }
                if (explicitNullable != nullable) {
                    throw exceptionCreator(
                        "it cannot be decorated by \"@$annotationTypeName\" which let the property be " +
                            (if (nullable) "nullable" else "nonnull") +
                            " because the property type \"$elementText\" can only be " +
                            (if (explicitNullable) "nullable" else "nonnull")
                    )
                }
            }
            val annotationNullity = annotationNullity
            if (annotationNullity != null) {
                if (annotationNullity.isNullable != nullable) {
                    throw exceptionCreator(
                        "it cannot be decorated by both @${annotationNullity.annotationTypeName} " +
                            "and @$annotationTypeName"
                    )
                }
            } else {
                this.annotationNullity = AnnotationNullity(annotationTypeName, nullable)
            }
        }

        private fun determineNullable(type: Type): Boolean {
            val specifiedNullable = explicitNullable ?: (annotationNullity?.isNullable == true)
            when (type) {
                Type.ID,
                Type.VERSION,
                Type.ONE_TO_MANY,
                Type.MANY_TO_MANY -> if (specifiedNullable) {
                    throw exceptionCreator(
                        "it cannot be nullable because it is $type property"
                    )
                }

                Type.ONE_TO_ONE,
                Type.MANY_TO_ONE -> if (JOIN_TABLE_ANNOTATION in (annotationTypes ?: emptySet())) {
                    if (!specifiedNullable) {
                        throw exceptionCreator(
                            "the $type property decorated by @class $JOIN_TABLE_ANNOTATION must be nullable"
                        )
                    }
                }

                Type.ID_VIEW -> if (isList && specifiedNullable) {
                    throw exceptionCreator(
                        "the list property cannot be nullable"
                    )
                }

                else -> Unit
            }
            return specifiedNullable
        }

        private fun validateList(type: Type) {
            when (type) {
                Type.TRANSIENT,
                Type.ID_VIEW -> Unit

                Type.ONE_TO_MANY,
                Type.MANY_TO_MANY,
                Type.MANY_TO_MANY_VIEW -> if (!isList) {
                    throw exceptionCreator(
                        "it is not list so that it cannot be decorated by @${type.annotationQualifiedName}"
                    )
                }

                else -> if (type.isAssociation && isList) {
                    throw exceptionCreator(
                        "list association property must be decorated by @$ONE_TO_MANY_ANNOTATION, " +
                            "@$MANY_TO_MANY_ANNOTATION or @$MANY_TO_MANY_VIEW_ANNOTATION"
                    )
                }
            }
        }

        private fun validateReturnType(type: Type) {
            if (type.isAssociation && elementAnnotationQualifiedName != ENTITY) {
                throw exceptionCreator(
                    "it is association property so that its target type \"$elementText\" " +
                        "must be decorated by @$ENTITY"
                )
            }
            if (typeAnnotationQualifiedName == IMMUTABLE) {
                return
            }
            if (elementAnnotationQualifiedName == ENTITY && type != Type.TRANSIENT && !type.isAssociation) {
                if (isList) {
                    throw exceptionCreator(
                        "it must be decorated by \"@$ONE_TO_MANY_ANNOTATION\" " +
                            "\"@$MANY_TO_MANY_ANNOTATION\" or \"@$MANY_TO_MANY_VIEW_ANNOTATION\""
                    )
                }
                throw exceptionCreator(
                    "it must be decorated by \"@$MANY_TO_ONE_ANNOTATION\" or \"@$ONE_TO_ONE_ANNOTATION\""
                )
            }
            if (type != Type.TRANSIENT &&
                !type.isAssociation &&
                !isList &&
                elementAnnotationQualifiedName != null &&
                elementAnnotationQualifiedName != EMBEDDABLE
            ) {
                throw exceptionCreator(
                    "it is not association property, its target type \"$elementText\" is immutable type, " +
                        "immutable type is not enough, please use \"@$EMBEDDABLE\""
                )
            }
        }

        private fun conflict(annotationType1: String, annotationType2: String): Nothing {
            throw exceptionCreator(
                "it cannot be decorated by both @$annotationType1 and @$annotationType2"
            )
        }
    }

    companion object {
        val MAPPED_BY_PROVIDER_NAMES: Set<String> = setOf(
            ONE_TO_ONE_ANNOTATION,
            ONE_TO_MANY_ANNOTATION,
            MANY_TO_MANY_ANNOTATION,
        )

        private const val COLUMN_ANNOTATION = "org.babyfish.jimmer.sql.Column"
        private const val PROP_OVERRIDE_ANNOTATION = "org.babyfish.jimmer.sql.PropOverride"
        private const val PROP_OVERRIDES_ANNOTATION = "org.babyfish.jimmer.sql.PropOverrides"
        private const val SERIALIZED_ANNOTATION = "org.babyfish.jimmer.sql.Serialized"
        private const val ON_DISSOCIATE_ANNOTATION = "org.babyfish.jimmer.sql.OnDissociate"
        private const val JOIN_COLUMN_ANNOTATION = "org.babyfish.jimmer.sql.JoinColumn"
        private const val JOIN_COLUMNS_ANNOTATION = "org.babyfish.jimmer.sql.JoinColumns"
        private const val JOIN_TABLE_ANNOTATION = "org.babyfish.jimmer.sql.JoinTable"
        private const val JOIN_SQL_ANNOTATION = "org.babyfish.jimmer.sql.JoinSql"
        private const val KEY_ANNOTATION = "org.babyfish.jimmer.sql.Key"
        private const val EXCLUDE_FROM_ALL_SCALARS_ANNOTATION =
            "org.babyfish.jimmer.sql.ExcludeFromAllScalars"
        private const val T_NULLABLE_ANNOTATION = "org.babyfish.jimmer.client.TNullable"

        private val VALUE_ANNOTATION_TYPES = linkedSetOf(ENTITY, MAPPED_SUPERCLASS, EMBEDDABLE)

        private val REF_ANNOTATION_TYPES = linkedSetOf(ENTITY, MAPPED_SUPERCLASS)

        private val VALUE_ANNOTATION_TYPES_FOR_DECLARING_CHECK = setOf(
            COLUMN_ANNOTATION,
            PROP_OVERRIDES_ANNOTATION,
            PROP_OVERRIDE_ANNOTATION,
            SCALAR,
            SERIALIZED_ANNOTATION,
            FORMULA_ANNOTATION,
        )

        private val ASSOCIATION_STORAGE_ANNOTATION_TYPES = setOf(
            JOIN_COLUMNS_ANNOTATION,
            JOIN_COLUMN_ANNOTATION,
            JOIN_TABLE_ANNOTATION,
            JOIN_SQL_ANNOTATION,
        )

        private val TYPE_MAP = linkedMapOf(
            TRANSIENT_ANNOTATION to Type.TRANSIENT,
            ID_ANNOTATION to Type.ID,
            VERSION_ANNOTATION to Type.VERSION,
            LOGICAL_DELETED_ANNOTATION to Type.LOGICAL_DELETED,
            FORMULA_ANNOTATION to Type.FORMULA,
            ONE_TO_ONE_ANNOTATION to Type.ONE_TO_ONE,
            MANY_TO_ONE_ANNOTATION to Type.MANY_TO_ONE,
            ONE_TO_MANY_ANNOTATION to Type.ONE_TO_MANY,
            MANY_TO_MANY_ANNOTATION to Type.MANY_TO_MANY,
            ID_VIEW_ANNOTATION to Type.ID_VIEW,
            MANY_TO_MANY_VIEW_ANNOTATION to Type.MANY_TO_MANY_VIEW,
        )

        private val FAMILY_MAP = linkedMapOf(
            Type.TRANSIENT to emptySet(),
            Type.ID to setOf(COLUMN_ANNOTATION, PROP_OVERRIDES_ANNOTATION, PROP_OVERRIDE_ANNOTATION),
            Type.VERSION to setOf(COLUMN_ANNOTATION, DEFAULT, EXCLUDE_FROM_ALL_SCALARS_ANNOTATION),
            Type.LOGICAL_DELETED to setOf(LOGICAL_DELETED_ANNOTATION, COLUMN_ANNOTATION, DEFAULT, EXCLUDE_FROM_ALL_SCALARS_ANNOTATION),
            Type.FORMULA to setOf(FORMULA_ANNOTATION),
            Type.BASIC to setOf(
                KEY_ANNOTATION,
                COLUMN_ANNOTATION,
                PROP_OVERRIDES_ANNOTATION,
                PROP_OVERRIDE_ANNOTATION,
                SCALAR,
                SERIALIZED_ANNOTATION,
                DEFAULT,
                EXCLUDE_FROM_ALL_SCALARS_ANNOTATION,
            ),
            Type.ONE_TO_ONE to setOf(
                KEY_ANNOTATION,
                ON_DISSOCIATE_ANNOTATION,
                JOIN_COLUMNS_ANNOTATION,
                JOIN_COLUMN_ANNOTATION,
                JOIN_TABLE_ANNOTATION,
            ),
            Type.MANY_TO_ONE to setOf(
                KEY_ANNOTATION,
                ON_DISSOCIATE_ANNOTATION,
                JOIN_COLUMNS_ANNOTATION,
                JOIN_COLUMN_ANNOTATION,
                JOIN_TABLE_ANNOTATION,
            ),
            Type.ONE_TO_MANY to emptySet(),
            Type.MANY_TO_MANY to setOf(JOIN_TABLE_ANNOTATION, JOIN_SQL_ANNOTATION),
            Type.ID_VIEW to emptySet(),
            Type.MANY_TO_MANY_VIEW to emptySet(),
        )

        private val INVERSE_MAP: Map<String, Set<Type>> = buildMap {
            for ((type, annotations) in FAMILY_MAP) {
                for (annotation in annotations) {
                    put(annotation, (get(annotation) ?: emptySet()) + type)
                }
            }
        }

        private val SQL_ANNOTATION_TYPES: Set<String> =
            buildSet {
                TYPE_MAP.keys.filterNotNullTo(this)
                FAMILY_MAP.values.forEach { addAll(it) }
            }
    }
}

private data class AnnotationNullity(
    val annotationTypeName: String,
    val isNullable: Boolean,
)
