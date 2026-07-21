@file:Suppress("warnings")

package demo

import com.fasterxml.jackson.`annotation`.JsonIgnore
import com.fasterxml.jackson.`annotation`.JsonPropertyOrder
import java.io.Serializable
import java.lang.IllegalStateException
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Pattern
import javax.validation.constraints.Size
import kotlin.Any
import kotlin.Boolean
import kotlin.Cloneable
import kotlin.Int
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.MutableList
import org.babyfish.jimmer.CircularReferenceException
import org.babyfish.jimmer.Draft
import org.babyfish.jimmer.DraftConsumer
import org.babyfish.jimmer.ImmutableObjects
import org.babyfish.jimmer.UnloadedException
import org.babyfish.jimmer.`impl`.validation.Validator
import org.babyfish.jimmer.`internal`.GeneratedBy
import org.babyfish.jimmer.jackson.ImmutableModuleRequiredException
import org.babyfish.jimmer.kt.DslScope
import org.babyfish.jimmer.kt.ImmutableCreator
import org.babyfish.jimmer.meta.ImmutablePropCategory
import org.babyfish.jimmer.meta.ImmutableType
import org.babyfish.jimmer.meta.PropId
import org.babyfish.jimmer.runtime.DraftContext
import org.babyfish.jimmer.runtime.DraftSpi
import org.babyfish.jimmer.runtime.ImmutableSpi
import org.babyfish.jimmer.runtime.Internal
import org.babyfish.jimmer.runtime.Visibility

@DslScope
@GeneratedBy(type = ValidatedDraftModel::class)
public interface ValidatedDraftModelDraft : ValidatedDraftModel, Draft {
    override var requiredName: String

    override var title: String

    @GeneratedBy(type = ValidatedDraftModel::class)
    public object `$` {
        public const val SLOT_REQUIRED_NAME: Int = 0

        public const val SLOT_TITLE: Int = 1

        public val type: ImmutableType = ImmutableType
            .newBuilder(
                "0.11.2",
                ValidatedDraftModel::class,
                listOf(

                ),
            ) { ctx, base ->
                DraftImpl(ctx, base as ValidatedDraftModel?)
            }
            .add(SLOT_REQUIRED_NAME, "requiredName", ImmutablePropCategory.SCALAR, String::class.java, false)
            .add(SLOT_TITLE, "title", ImmutablePropCategory.SCALAR, String::class.java, false)
            .build()

        public fun produce(base: ValidatedDraftModel? = null, resolveImmediately: Boolean = false): ValidatedDraftModel {
            val consumer = DraftConsumer<ValidatedDraftModelDraft> {}
            return Internal.produce(type, base, resolveImmediately, consumer) as ValidatedDraftModel
        }

        public fun produce(
            base: ValidatedDraftModel? = null,
            resolveImmediately: Boolean = false,
            block: ValidatedDraftModelDraft.() -> Unit,
        ): ValidatedDraftModel {
            val consumer = DraftConsumer<ValidatedDraftModelDraft> { block(it) }
            return Internal.produce(type, base, resolveImmediately, consumer) as ValidatedDraftModel
        }

        @GeneratedBy(type = ValidatedDraftModel::class)
        @JsonPropertyOrder("dummyPropForJacksonError__", "requiredName", "title")
        private abstract interface Implementor : ValidatedDraftModel, ImmutableSpi {
            public val dummyPropForJacksonError__: Int
                get() = throw ImmutableModuleRequiredException()

            override fun __get(prop: PropId): Any? = when (prop.asIndex()) {
                -1 ->
                	__get(prop.asName())
                SLOT_REQUIRED_NAME ->
                	requiredName
                SLOT_TITLE ->
                	title
                else -> throw IllegalArgumentException(
                    "Illegal property name" + 
                    " for \"demo.ValidatedDraftModel\": " + 
                    prop
                )

            }

            override fun __get(prop: String): Any? = when (prop) {
                "requiredName" ->
                	requiredName
                "title" ->
                	title
                else -> throw IllegalArgumentException(
                    "Illegal property name" + 
                    " for \"demo.ValidatedDraftModel\": " + 
                    prop
                )

            }

            override fun __type(): ImmutableType = `$`.type
        }

        @GeneratedBy(type = ValidatedDraftModel::class)
        private class Impl : Implementor, Cloneable, Serializable {
            @get:JsonIgnore
            internal var __visibility: Visibility? = null

            @get:JsonIgnore
            internal var __requiredNameValue: String? = null

            @get:JsonIgnore
            internal var __titleValue: String? = null

            override val requiredName: String
                get() {
                    val __requiredNameValue = this.__requiredNameValue
                    if (__requiredNameValue === null) {
                        throw UnloadedException(ValidatedDraftModel::class.java, "requiredName")
                    }
                    return __requiredNameValue
                }

            override val title: String
                get() {
                    val __titleValue = this.__titleValue
                    if (__titleValue === null) {
                        throw UnloadedException(ValidatedDraftModel::class.java, "title")
                    }
                    return __titleValue
                }

            public override fun clone(): Impl {
                val copy = super.clone() as Impl
                val originalVisibility = this.__visibility
                if (originalVisibility != null) {
                    val newVisibility = Visibility.of(2)
                    for (propId in 0 until 2) {
                        newVisibility.show(propId, originalVisibility.visible(propId))
                    }
                    copy.__visibility = newVisibility
                } else {
                    copy.__visibility = null
                }
                return copy
            }

            override fun __isLoaded(prop: PropId): Boolean = when (prop.asIndex()) {
                -1 ->
                	__isLoaded(prop.asName())
                SLOT_REQUIRED_NAME ->
                	__requiredNameValue !== null
                SLOT_TITLE ->
                	__titleValue !== null
                else -> throw IllegalArgumentException(
                    "Illegal property name" + 
                    " for \"demo.ValidatedDraftModel\": " + 
                    prop
                )

            }

            override fun __isLoaded(prop: String): Boolean = when (prop) {
                "requiredName" ->
                	__requiredNameValue !== null
                "title" ->
                	__titleValue !== null
                else -> throw IllegalArgumentException(
                    "Illegal property name" + 
                    " for \"demo.ValidatedDraftModel\": " + 
                    prop
                )

            }

            override fun __isVisible(prop: PropId): Boolean {
                val __visibility = this.__visibility ?: return true
                return when (prop.asIndex()) {
                    -1 ->
                    	__isVisible(prop.asName())
                    SLOT_REQUIRED_NAME ->
                    	__visibility.visible(SLOT_REQUIRED_NAME)
                    SLOT_TITLE ->
                    	__visibility.visible(SLOT_TITLE)
                    else -> true
                }
            }

            override fun __isVisible(prop: String): Boolean {
                val __visibility = this.__visibility ?: return true
                return when (prop) {
                    "requiredName" ->
                    	__visibility.visible(SLOT_REQUIRED_NAME)
                    "title" ->
                    	__visibility.visible(SLOT_TITLE)
                    else -> true
                }
            }

            public fun __shallowHashCode(): Int {
                var hash = __visibility?.hashCode() ?: 0
                if (__requiredNameValue !== null) {
                    hash = 31 * hash + __requiredNameValue.hashCode()
                }
                if (__titleValue !== null) {
                    hash = 31 * hash + __titleValue.hashCode()
                }
                return hash
            }

            override fun hashCode(): Int {
                var hash = __visibility?.hashCode() ?: 0
                if (__requiredNameValue !== null) {
                    hash = 31 * hash + __requiredNameValue.hashCode()
                }
                if (__titleValue !== null) {
                    hash = 31 * hash + __titleValue.hashCode()
                }
                return hash
            }

            override fun __hashCode(shallow: Boolean): Int = if (shallow) __shallowHashCode() else hashCode()

            public fun __shallowEquals(other: Any?): Boolean {
                val __other = other as? Implementor
                if (__other === null) {
                    return false
                }
                if (__isVisible(PropId.byIndex(SLOT_REQUIRED_NAME)) != __other.__isVisible(PropId.byIndex(SLOT_REQUIRED_NAME))) {
                    return false
                }
                val __requiredNameLoaded = 
                    this.__requiredNameValue !== null
                if (__requiredNameLoaded != (__other.__isLoaded(PropId.byIndex(SLOT_REQUIRED_NAME)))) {
                    return false
                }
                if (__requiredNameLoaded && this.__requiredNameValue != __other.requiredName) {
                    return false
                }
                if (__isVisible(PropId.byIndex(SLOT_TITLE)) != __other.__isVisible(PropId.byIndex(SLOT_TITLE))) {
                    return false
                }
                val __titleLoaded = 
                    this.__titleValue !== null
                if (__titleLoaded != (__other.__isLoaded(PropId.byIndex(SLOT_TITLE)))) {
                    return false
                }
                if (__titleLoaded && this.__titleValue != __other.title) {
                    return false
                }
                return true
            }

            override fun equals(other: Any?): Boolean {
                val __other = other as? Implementor
                if (__other === null) {
                    return false
                }
                if (__isVisible(PropId.byIndex(SLOT_REQUIRED_NAME)) != __other.__isVisible(PropId.byIndex(SLOT_REQUIRED_NAME))) {
                    return false
                }
                val __requiredNameLoaded = 
                    this.__requiredNameValue !== null
                if (__requiredNameLoaded != (__other.__isLoaded(PropId.byIndex(SLOT_REQUIRED_NAME)))) {
                    return false
                }
                if (__requiredNameLoaded && this.__requiredNameValue != __other.requiredName) {
                    return false
                }
                if (__isVisible(PropId.byIndex(SLOT_TITLE)) != __other.__isVisible(PropId.byIndex(SLOT_TITLE))) {
                    return false
                }
                val __titleLoaded = 
                    this.__titleValue !== null
                if (__titleLoaded != (__other.__isLoaded(PropId.byIndex(SLOT_TITLE)))) {
                    return false
                }
                if (__titleLoaded && this.__titleValue != __other.title) {
                    return false
                }
                return true
            }

            override fun __equals(obj: Any?, shallow: Boolean): Boolean = if (shallow) __shallowEquals(obj) else equals(obj)

            override fun toString(): String = ImmutableObjects.toString(this)
        }

        @GeneratedBy(type = ValidatedDraftModel::class)
        internal class DraftImpl(
            ctx: DraftContext?,
            base: ValidatedDraftModel?,
        ) : Implementor,
            ValidatedDraftModelDraft,
            DraftSpi {
            private val __ctx: DraftContext? = ctx

            private val __base: Impl? = base as Impl?

            private var __modified: Impl? = if (base === null) Impl() else null

            private var __resolving: Boolean = false

            private var __resolved: ValidatedDraftModel? = null

            override var requiredName: String
                get() = (__modified ?: __base!!).requiredName
                set(requiredName) {
                    if (__resolved != null) {
                        throw IllegalStateException("The current draft has been resolved so it cannot be modified")
                    }
                    val __tmpModified = (__modified ?: __base!!.clone())
                            .also { __modified = it }
                    __tmpModified.__requiredNameValue = requiredName
                }

            override var title: String
                get() = (__modified ?: __base!!).title
                set(title) {
                    if (__resolved != null) {
                        throw IllegalStateException("The current draft has been resolved so it cannot be modified")
                    }
                    __TITLE_VALIDATOR__ID_CARD_2013935395.validate(title)
                    val __tmpModified = (__modified ?: __base!!.clone())
                            .also { __modified = it }
                    __tmpModified.__titleValue = title
                }

            override fun __isLoaded(prop: PropId): Boolean = (__modified ?: __base!!).__isLoaded(prop)

            override fun __isLoaded(prop: String): Boolean = (__modified ?: __base!!).__isLoaded(prop)

            override fun __isVisible(prop: PropId): Boolean = (__modified ?: __base!!).__isVisible(prop)

            override fun __isVisible(prop: String): Boolean = (__modified ?: __base!!).__isVisible(prop)

            override fun hashCode(): Int = (__modified ?: __base!!).hashCode()

            override fun __hashCode(shallow: Boolean): Int = (__modified ?: __base!!).__hashCode(shallow)

            override fun equals(other: Any?): Boolean = (__modified ?: __base!!).equals(other)

            override fun __equals(other: Any?, shallow: Boolean): Boolean = (__modified ?: __base!!).__equals(other, shallow)

            override fun toString(): String = ImmutableObjects.toString(this)

            override fun __unload(prop: PropId) {
                if (__resolved != null) {
                    throw IllegalStateException("The current draft has been resolved so it cannot be modified")
                }
                when (prop.asIndex()) {
                    -1 ->
                    	__unload(prop.asName())
                    SLOT_REQUIRED_NAME ->
                    	(__modified ?: __base!!.clone())
                                .also { __modified = it }
                                .__requiredNameValue = null
                    SLOT_TITLE ->
                    	(__modified ?: __base!!.clone())
                                .also { __modified = it }
                                .__titleValue = null
                    else -> throw IllegalArgumentException(
                        "Illegal property name" + 
                        " for \"demo.ValidatedDraftModel\": " + 
                        prop
                    )

                }
            }

            override fun __unload(prop: String) {
                if (__resolved != null) {
                    throw IllegalStateException("The current draft has been resolved so it cannot be modified")
                }
                when (prop) {
                    "requiredName" ->
                    	(__modified ?: __base!!.clone())
                                .also { __modified = it }
                                .__requiredNameValue = null
                    "title" ->
                    	(__modified ?: __base!!.clone())
                                .also { __modified = it }
                                .__titleValue = null
                    else -> throw IllegalArgumentException(
                        "Illegal property name" + 
                        " for \"demo.ValidatedDraftModel\": " + 
                        prop
                    )

                }
            }

            override fun __set(prop: PropId, `value`: Any?) {
                when (prop.asIndex()) {
                    -1 ->
                    	__set(prop.asName(), value)
                    SLOT_REQUIRED_NAME ->
                    	this.requiredName = value as String?
                    	?: throw IllegalArgumentException("'requiredName cannot be null")
                    SLOT_TITLE ->
                    	this.title = value as String?
                    	?: throw IllegalArgumentException("'title cannot be null")
                    else -> throw IllegalArgumentException(
                        "Illegal property name" + 
                        " for \"demo.ValidatedDraftModel\": " + 
                        prop
                    )

                }
            }

            override fun __set(prop: String, `value`: Any?) {
                when (prop) {
                    "requiredName" ->
                    	this.requiredName = value as String?
                    	?: throw IllegalArgumentException("'requiredName cannot be null")
                    "title" ->
                    	this.title = value as String?
                    	?: throw IllegalArgumentException("'title cannot be null")
                    else -> throw IllegalArgumentException(
                        "Illegal property name" + 
                        " for \"demo.ValidatedDraftModel\": " + 
                        prop
                    )

                }
            }

            override fun __show(prop: PropId, visible: Boolean) {
                if (__resolved != null) {
                    throw IllegalStateException("The current draft has been resolved so it cannot be modified")
                }
                val __visibility = (__modified ?: __base!!).__visibility
                    ?: if (visible) {
                        null
                    } else {
                        Visibility.of(2).also{
                            (__modified ?: __base!!.clone())
                            .also { __modified = it }.__visibility = it}
                    }
                    ?: return
                when (prop.asIndex()) {
                    -1 ->
                    	__show(prop.asName(), visible)
                    SLOT_REQUIRED_NAME ->
                    	__visibility.show(SLOT_REQUIRED_NAME, visible)
                    SLOT_TITLE ->
                    	__visibility.show(SLOT_TITLE, visible)
                    else -> throw IllegalArgumentException(
                        "Illegal property id: \"" + 
                        prop + 
                        "\",it does not exists"
                    )
                }
            }

            override fun __show(prop: String, visible: Boolean) {
                if (__resolved != null) {
                    throw IllegalStateException("The current draft has been resolved so it cannot be modified")
                }
                val __visibility = (__modified ?: __base!!).__visibility
                    ?: if (visible) {
                        null
                    } else {
                        Visibility.of(2).also{
                            (__modified ?: __base!!.clone())
                            .also { __modified = it }.__visibility = it}
                    }
                    ?: return
                when (prop) {
                    "requiredName" ->
                    	__visibility.show(SLOT_REQUIRED_NAME, visible)
                    "title" ->
                    	__visibility.show(SLOT_TITLE, visible)
                    else -> throw IllegalArgumentException(
                        "Illegal property name: \"" + 
                        prop + 
                        "\",it does not exists"
                    )
                }
            }

            override fun __draftContext(): DraftContext = __ctx()

            override fun __resolve(): Any {
                val __resolved = this.__resolved
                if (__resolved != null) {
                    return __resolved
                }
                if (__resolving) {
                    throw CircularReferenceException()
                }
                __resolving = true
                val __ctx = __ctx()
                try {
                    val base = __base
                    var __tmpModified = __modified
                    if (base !== null && __tmpModified === null) {
                        this.__resolved = base
                        return base
                    }
                    this.__resolved = __tmpModified
                    return __tmpModified!!
                } finally {
                    __resolving = false
                }
            }

            override fun __isResolved(): Boolean = __resolved != null

            private fun __ctx(): DraftContext = __ctx ?: error("The current draft object is simple draft which does not support converting nested object to nested draft")

            internal fun __unwrap(): Any = __modified ?: error("Internal bug, draft for builder must have `__modified`")

            public companion object {
                private val __TITLE_VALIDATOR__ID_CARD_2013935395: Validator<String> =
                        Validator(IdCard::class.java, "invalid id card", ValidatedDraftModel::class.java, PropId.byIndex(SLOT_TITLE))
            }
        }
    }

    @GeneratedBy(type = ValidatedDraftModel::class)
    public class Builder {
        private val __draft: `$`.DraftImpl

        public constructor(base: ValidatedDraftModel?) {
            __draft = `$`.DraftImpl(null, base)
        }

        public constructor() : this(null)

        public fun requiredName(requiredName: String?): Builder {
            if (requiredName !== null) {
                __draft.requiredName = requiredName
                __draft.__show(PropId.byIndex(`$`.SLOT_REQUIRED_NAME), true)
            }
            return this
        }

        @NotBlank(
            message = "title must not be blank",
            groups = arrayOf(),
            payload = arrayOf(),
        )
        @Size(
            min = 2,
            max = 8,
            message = "title size",
            groups = arrayOf(),
            payload = arrayOf(),
        )
        @Pattern(
            regexp = "[A-Z][a-z]+",
            message = "title pattern",
            flags = arrayOf(),
            groups = arrayOf(),
            payload = arrayOf(),
        )
        public fun title(title: String?): Builder {
            if (title !== null) {
                __draft.title = title
                __draft.__show(PropId.byIndex(`$`.SLOT_TITLE), true)
            }
            return this
        }

        public fun build(): ValidatedDraftModel = __draft.__unwrap() as ValidatedDraftModel
    }
}

@GeneratedBy(type = ValidatedDraftModel::class)
public fun ImmutableCreator<ValidatedDraftModel>.`by`(resolveImmediately: Boolean = false, block: ValidatedDraftModelDraft.() -> Unit): ValidatedDraftModel = ValidatedDraftModelDraft.`$`.produce(null, resolveImmediately, block)

@GeneratedBy(type = ValidatedDraftModel::class)
public fun ImmutableCreator<ValidatedDraftModel>.`by`(base: ValidatedDraftModel?, resolveImmediately: Boolean = false): ValidatedDraftModel = ValidatedDraftModelDraft.`$`.produce(base, resolveImmediately)

@GeneratedBy(type = ValidatedDraftModel::class)
public fun ImmutableCreator<ValidatedDraftModel>.`by`(
    base: ValidatedDraftModel?,
    resolveImmediately: Boolean = false,
    block: ValidatedDraftModelDraft.() -> Unit,
): ValidatedDraftModel = ValidatedDraftModelDraft.`$`.produce(base, resolveImmediately, block)

@GeneratedBy(type = ValidatedDraftModel::class)
public fun ValidatedDraftModel(resolveImmediately: Boolean = false, block: ValidatedDraftModelDraft.() -> Unit): ValidatedDraftModel = ValidatedDraftModelDraft.`$`.produce(null, resolveImmediately, block)

@GeneratedBy(type = ValidatedDraftModel::class)
public fun ValidatedDraftModel(
    base: ValidatedDraftModel?,
    resolveImmediately: Boolean = false,
    block: ValidatedDraftModelDraft.() -> Unit,
): ValidatedDraftModel = ValidatedDraftModelDraft.`$`.produce(base, resolveImmediately, block)

@GeneratedBy(type = ValidatedDraftModel::class)
public fun MutableList<ValidatedDraftModelDraft>.addBy(resolveImmediately: Boolean = false, block: ValidatedDraftModelDraft.() -> Unit): MutableList<ValidatedDraftModelDraft> {
    add(ValidatedDraftModelDraft.`$`.produce(null, resolveImmediately, block) as ValidatedDraftModelDraft)
    return this
}

@GeneratedBy(type = ValidatedDraftModel::class)
public fun MutableList<ValidatedDraftModelDraft>.addBy(base: ValidatedDraftModel?, resolveImmediately: Boolean = false): MutableList<ValidatedDraftModelDraft> {
    add(ValidatedDraftModelDraft.`$`.produce(base, resolveImmediately) as ValidatedDraftModelDraft)
    return this
}

@GeneratedBy(type = ValidatedDraftModel::class)
public fun MutableList<ValidatedDraftModelDraft>.addBy(
    base: ValidatedDraftModel?,
    resolveImmediately: Boolean = false,
    block: ValidatedDraftModelDraft.() -> Unit,
): MutableList<ValidatedDraftModelDraft> {
    add(ValidatedDraftModelDraft.`$`.produce(base, resolveImmediately, block) as ValidatedDraftModelDraft)
    return this
}

@GeneratedBy(type = ValidatedDraftModel::class)
public fun ValidatedDraftModel.copy(resolveImmediately: Boolean = false, block: ValidatedDraftModelDraft.() -> Unit): ValidatedDraftModel = ValidatedDraftModelDraft.`$`.produce(this, resolveImmediately, block)
