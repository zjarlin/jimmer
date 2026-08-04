@file:Suppress("warnings")

package demo

import com.fasterxml.jackson.`annotation`.JsonIgnore
import com.fasterxml.jackson.`annotation`.JsonPropertyOrder
import java.io.Serializable
import java.lang.IllegalStateException
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
@GeneratedBy(type = Contact::class)
public interface ContactDraft : Contact, Draft {
    override var label: String

    @GeneratedBy(type = Contact::class)
    public object `$` {
        public const val SLOT_LABEL: Int = 0

        public val type: ImmutableType = ImmutableType
            .newBuilder(
                "0.11.6",
                Contact::class,
                listOf(

                ),
            ) { ctx, base ->
                DraftImpl(ctx, base as Contact?)
            }
            .add(SLOT_LABEL, "label", ImmutablePropCategory.SCALAR, String::class.java, false)
            .build()

        public fun produce(base: Contact? = null, resolveImmediately: Boolean = false): Contact {
            val consumer = DraftConsumer<ContactDraft> {}
            return Internal.produce(type, base, resolveImmediately, consumer) as Contact
        }

        public fun produce(
            base: Contact? = null,
            resolveImmediately: Boolean = false,
            block: ContactDraft.() -> Unit,
        ): Contact {
            val consumer = DraftConsumer<ContactDraft> { block(it) }
            return Internal.produce(type, base, resolveImmediately, consumer) as Contact
        }

        @GeneratedBy(type = Contact::class)
        @JsonPropertyOrder("dummyPropForJacksonError__", "label")
        private abstract interface Implementor : Contact, ImmutableSpi {
            public val dummyPropForJacksonError__: Int
                get() = throw ImmutableModuleRequiredException()

            override fun __get(prop: PropId): Any? = when (prop.asIndex()) {
                -1 ->
                	__get(prop.asName())
                SLOT_LABEL ->
                	label
                else -> throw IllegalArgumentException(
                    "Illegal property name" + 
                    " for \"demo.Contact\": " + 
                    prop
                )

            }

            override fun __get(prop: String): Any? = when (prop) {
                "label" ->
                	label
                else -> throw IllegalArgumentException(
                    "Illegal property name" + 
                    " for \"demo.Contact\": " + 
                    prop
                )

            }

            override fun __type(): ImmutableType = `$`.type
        }

        @GeneratedBy(type = Contact::class)
        private class Impl : Implementor, Cloneable, Serializable {
            @get:JsonIgnore
            internal var __visibility: Visibility? = null

            @get:JsonIgnore
            internal var __labelValue: String? = null

            override val label: String
                get() {
                    val __labelValue = this.__labelValue
                    if (__labelValue === null) {
                        throw UnloadedException(Contact::class.java, "label")
                    }
                    return __labelValue
                }

            public override fun clone(): Impl {
                val copy = super.clone() as Impl
                val originalVisibility = this.__visibility
                if (originalVisibility != null) {
                    val newVisibility = Visibility.of(1)
                    for (propId in 0 until 1) {
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
                SLOT_LABEL ->
                	__labelValue !== null
                else -> throw IllegalArgumentException(
                    "Illegal property name" + 
                    " for \"demo.Contact\": " + 
                    prop
                )

            }

            override fun __isLoaded(prop: String): Boolean = when (prop) {
                "label" ->
                	__labelValue !== null
                else -> throw IllegalArgumentException(
                    "Illegal property name" + 
                    " for \"demo.Contact\": " + 
                    prop
                )

            }

            override fun __isVisible(prop: PropId): Boolean {
                val __visibility = this.__visibility ?: return true
                return when (prop.asIndex()) {
                    -1 ->
                    	__isVisible(prop.asName())
                    SLOT_LABEL ->
                    	__visibility.visible(SLOT_LABEL)
                    else -> true
                }
            }

            override fun __isVisible(prop: String): Boolean {
                val __visibility = this.__visibility ?: return true
                return when (prop) {
                    "label" ->
                    	__visibility.visible(SLOT_LABEL)
                    else -> true
                }
            }

            public fun __shallowHashCode(): Int {
                var hash = __visibility?.hashCode() ?: 0
                if (__labelValue !== null) {
                    hash = 31 * hash + __labelValue.hashCode()
                }
                return hash
            }

            override fun hashCode(): Int {
                var hash = __visibility?.hashCode() ?: 0
                if (__labelValue !== null) {
                    hash = 31 * hash + __labelValue.hashCode()
                }
                return hash
            }

            override fun __hashCode(shallow: Boolean): Int = if (shallow) __shallowHashCode() else hashCode()

            public fun __shallowEquals(other: Any?): Boolean {
                val __other = other as? Implementor
                if (__other === null) {
                    return false
                }
                if (__isVisible(PropId.byIndex(SLOT_LABEL)) != __other.__isVisible(PropId.byIndex(SLOT_LABEL))) {
                    return false
                }
                val __labelLoaded = 
                    this.__labelValue !== null
                if (__labelLoaded != (__other.__isLoaded(PropId.byIndex(SLOT_LABEL)))) {
                    return false
                }
                if (__labelLoaded && this.__labelValue != __other.label) {
                    return false
                }
                return true
            }

            override fun equals(other: Any?): Boolean {
                val __other = other as? Implementor
                if (__other === null) {
                    return false
                }
                if (__isVisible(PropId.byIndex(SLOT_LABEL)) != __other.__isVisible(PropId.byIndex(SLOT_LABEL))) {
                    return false
                }
                val __labelLoaded = 
                    this.__labelValue !== null
                if (__labelLoaded != (__other.__isLoaded(PropId.byIndex(SLOT_LABEL)))) {
                    return false
                }
                if (__labelLoaded && this.__labelValue != __other.label) {
                    return false
                }
                return true
            }

            override fun __equals(obj: Any?, shallow: Boolean): Boolean = if (shallow) __shallowEquals(obj) else equals(obj)

            override fun toString(): String = ImmutableObjects.toString(this)
        }

        @GeneratedBy(type = Contact::class)
        internal class DraftImpl(
            ctx: DraftContext?,
            base: Contact?,
        ) : Implementor,
            ContactDraft,
            DraftSpi {
            private val __ctx: DraftContext? = ctx

            private val __base: Impl? = base as Impl?

            private var __modified: Impl? = if (base === null) Impl() else null

            private var __resolving: Boolean = false

            private var __resolved: Contact? = null

            override var label: String
                get() = (__modified ?: __base!!).label
                set(label) {
                    if (__resolved != null) {
                        throw IllegalStateException("The current draft has been resolved so it cannot be modified")
                    }
                    val __tmpModified = (__modified ?: __base!!.clone())
                            .also { __modified = it }
                    __tmpModified.__labelValue = label
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
                    SLOT_LABEL ->
                    	(__modified ?: __base!!.clone())
                                .also { __modified = it }
                                .__labelValue = null
                    else -> throw IllegalArgumentException(
                        "Illegal property name" + 
                        " for \"demo.Contact\": " + 
                        prop
                    )

                }
            }

            override fun __unload(prop: String) {
                if (__resolved != null) {
                    throw IllegalStateException("The current draft has been resolved so it cannot be modified")
                }
                when (prop) {
                    "label" ->
                    	(__modified ?: __base!!.clone())
                                .also { __modified = it }
                                .__labelValue = null
                    else -> throw IllegalArgumentException(
                        "Illegal property name" + 
                        " for \"demo.Contact\": " + 
                        prop
                    )

                }
            }

            override fun __set(prop: PropId, `value`: Any?) {
                when (prop.asIndex()) {
                    -1 ->
                    	__set(prop.asName(), value)
                    SLOT_LABEL ->
                    	this.label = value as String?
                    	?: throw IllegalArgumentException("'label cannot be null")
                    else -> throw IllegalArgumentException(
                        "Illegal property name" + 
                        " for \"demo.Contact\": " + 
                        prop
                    )

                }
            }

            override fun __set(prop: String, `value`: Any?) {
                when (prop) {
                    "label" ->
                    	this.label = value as String?
                    	?: throw IllegalArgumentException("'label cannot be null")
                    else -> throw IllegalArgumentException(
                        "Illegal property name" + 
                        " for \"demo.Contact\": " + 
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
                        Visibility.of(1).also{
                            (__modified ?: __base!!.clone())
                            .also { __modified = it }.__visibility = it}
                    }
                    ?: return
                when (prop.asIndex()) {
                    -1 ->
                    	__show(prop.asName(), visible)
                    SLOT_LABEL ->
                    	__visibility.show(SLOT_LABEL, visible)
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
                        Visibility.of(1).also{
                            (__modified ?: __base!!.clone())
                            .also { __modified = it }.__visibility = it}
                    }
                    ?: return
                when (prop) {
                    "label" ->
                    	__visibility.show(SLOT_LABEL, visible)
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
        }
    }

    @GeneratedBy(type = Contact::class)
    public class Builder {
        private val __draft: `$`.DraftImpl

        public constructor(base: Contact?) {
            __draft = `$`.DraftImpl(null, base)
        }

        public constructor() : this(null)

        public fun label(label: String?): Builder {
            if (label !== null) {
                __draft.label = label
                __draft.__show(PropId.byIndex(`$`.SLOT_LABEL), true)
            }
            return this
        }

        public fun build(): Contact = __draft.__unwrap() as Contact
    }
}

@GeneratedBy(type = Contact::class)
public fun ImmutableCreator<Contact>.`by`(resolveImmediately: Boolean = false, block: ContactDraft.() -> Unit): Contact = ContactDraft.`$`.produce(null, resolveImmediately, block)

@GeneratedBy(type = Contact::class)
public fun ImmutableCreator<Contact>.`by`(base: Contact?, resolveImmediately: Boolean = false): Contact = ContactDraft.`$`.produce(base, resolveImmediately)

@GeneratedBy(type = Contact::class)
public fun ImmutableCreator<Contact>.`by`(
    base: Contact?,
    resolveImmediately: Boolean = false,
    block: ContactDraft.() -> Unit,
): Contact = ContactDraft.`$`.produce(base, resolveImmediately, block)

@GeneratedBy(type = Contact::class)
public fun Contact(resolveImmediately: Boolean = false, block: ContactDraft.() -> Unit): Contact = ContactDraft.`$`.produce(null, resolveImmediately, block)

@GeneratedBy(type = Contact::class)
public fun Contact(
    base: Contact?,
    resolveImmediately: Boolean = false,
    block: ContactDraft.() -> Unit,
): Contact = ContactDraft.`$`.produce(base, resolveImmediately, block)

@GeneratedBy(type = Contact::class)
public fun MutableList<ContactDraft>.addBy(resolveImmediately: Boolean = false, block: ContactDraft.() -> Unit): MutableList<ContactDraft> {
    add(ContactDraft.`$`.produce(null, resolveImmediately, block) as ContactDraft)
    return this
}

@GeneratedBy(type = Contact::class)
public fun MutableList<ContactDraft>.addBy(base: Contact?, resolveImmediately: Boolean = false): MutableList<ContactDraft> {
    add(ContactDraft.`$`.produce(base, resolveImmediately) as ContactDraft)
    return this
}

@GeneratedBy(type = Contact::class)
public fun MutableList<ContactDraft>.addBy(
    base: Contact?,
    resolveImmediately: Boolean = false,
    block: ContactDraft.() -> Unit,
): MutableList<ContactDraft> {
    add(ContactDraft.`$`.produce(base, resolveImmediately, block) as ContactDraft)
    return this
}

@GeneratedBy(type = Contact::class)
public fun Contact.copy(resolveImmediately: Boolean = false, block: ContactDraft.() -> Unit): Contact = ContactDraft.`$`.produce(this, resolveImmediately, block)
