@file:Suppress("warnings")

package demo

import com.fasterxml.jackson.`annotation`.JsonIgnore
import com.fasterxml.jackson.`annotation`.JsonPropertyOrder
import java.io.Serializable
import java.lang.IllegalStateException
import java.lang.System
import kotlin.Any
import kotlin.Boolean
import kotlin.Cloneable
import kotlin.Int
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
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
import org.babyfish.jimmer.runtime.NonSharedList
import org.babyfish.jimmer.runtime.Visibility

@DslScope
@GeneratedBy(type = ReferenceModel::class)
public interface ReferenceModelDraft : ReferenceModel, Draft {
    override var address: Address

    override var contact: Contact

    override var previousContacts: List<Contact>

    override var aliases: List<String?>

    public fun address(): AddressDraft

    public fun address(block: AddressDraft.() -> Unit)

    public fun contact(): ContactDraft

    public fun contact(block: ContactDraft.() -> Unit)

    public fun previousContacts(): MutableList<ContactDraft>

    public fun aliases(): MutableList<String?>

    @GeneratedBy(type = ReferenceModel::class)
    public object `$` {
        public const val SLOT_ADDRESS: Int = 0

        public const val SLOT_CONTACT: Int = 1

        public const val SLOT_PREVIOUS_CONTACTS: Int = 2

        public const val SLOT_ALIASES: Int = 3

        public val type: ImmutableType = ImmutableType
            .newBuilder(
                "0.11.2",
                ReferenceModel::class,
                listOf(

                ),
            ) { ctx, base ->
                DraftImpl(ctx, base as ReferenceModel?)
            }
            .add(SLOT_ADDRESS, "address", ImmutablePropCategory.REFERENCE, Address::class.java, false)
            .add(SLOT_CONTACT, "contact", ImmutablePropCategory.REFERENCE, Contact::class.java, false)
            .add(SLOT_PREVIOUS_CONTACTS, "previousContacts", ImmutablePropCategory.REFERENCE_LIST, Contact::class.java, false)
            .add(SLOT_ALIASES, "aliases", ImmutablePropCategory.SCALAR_LIST, String::class.java, false)
            .build()

        public fun produce(base: ReferenceModel? = null, resolveImmediately: Boolean = false): ReferenceModel {
            val consumer = DraftConsumer<ReferenceModelDraft> {}
            return Internal.produce(type, base, resolveImmediately, consumer) as ReferenceModel
        }

        public fun produce(
            base: ReferenceModel? = null,
            resolveImmediately: Boolean = false,
            block: ReferenceModelDraft.() -> Unit,
        ): ReferenceModel {
            val consumer = DraftConsumer<ReferenceModelDraft> { block(it) }
            return Internal.produce(type, base, resolveImmediately, consumer) as ReferenceModel
        }

        @GeneratedBy(type = ReferenceModel::class)
        @JsonPropertyOrder("dummyPropForJacksonError__", "address", "contact", "previousContacts", "aliases")
        private abstract interface Implementor : ReferenceModel, ImmutableSpi {
            public val dummyPropForJacksonError__: Int
                get() = throw ImmutableModuleRequiredException()

            override fun __get(prop: PropId): Any? = when (prop.asIndex()) {
                -1 ->
                	__get(prop.asName())
                SLOT_ADDRESS ->
                	address
                SLOT_CONTACT ->
                	contact
                SLOT_PREVIOUS_CONTACTS ->
                	previousContacts
                SLOT_ALIASES ->
                	aliases
                else -> throw IllegalArgumentException(
                    "Illegal property name" + 
                    " for \"demo.ReferenceModel\": " + 
                    prop
                )

            }

            override fun __get(prop: String): Any? = when (prop) {
                "address" ->
                	address
                "contact" ->
                	contact
                "previousContacts" ->
                	previousContacts
                "aliases" ->
                	aliases
                else -> throw IllegalArgumentException(
                    "Illegal property name" + 
                    " for \"demo.ReferenceModel\": " + 
                    prop
                )

            }

            override fun __type(): ImmutableType = `$`.type
        }

        @GeneratedBy(type = ReferenceModel::class)
        private class Impl : Implementor, Cloneable, Serializable {
            @get:JsonIgnore
            internal var __visibility: Visibility? = null

            @get:JsonIgnore
            internal var __addressValue: Address? = null

            @get:JsonIgnore
            internal var __contactValue: Contact? = null

            @get:JsonIgnore
            internal var __previousContactsValue: NonSharedList<Contact>? = null

            @get:JsonIgnore
            internal var __aliasesValue: NonSharedList<String?>? = null

            override val address: Address
                get() {
                    val __addressValue = this.__addressValue
                    if (__addressValue === null) {
                        throw UnloadedException(ReferenceModel::class.java, "address")
                    }
                    return __addressValue
                }

            override val contact: Contact
                get() {
                    val __contactValue = this.__contactValue
                    if (__contactValue === null) {
                        throw UnloadedException(ReferenceModel::class.java, "contact")
                    }
                    return __contactValue
                }

            override val previousContacts: List<Contact>
                get() {
                    val __previousContactsValue = this.__previousContactsValue
                    if (__previousContactsValue === null) {
                        throw UnloadedException(ReferenceModel::class.java, "previousContacts")
                    }
                    return __previousContactsValue
                }

            override val aliases: List<String?>
                get() {
                    val __aliasesValue = this.__aliasesValue
                    if (__aliasesValue === null) {
                        throw UnloadedException(ReferenceModel::class.java, "aliases")
                    }
                    return __aliasesValue
                }

            public override fun clone(): Impl {
                val copy = super.clone() as Impl
                val originalVisibility = this.__visibility
                if (originalVisibility != null) {
                    val newVisibility = Visibility.of(4)
                    for (propId in 0 until 4) {
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
                SLOT_ADDRESS ->
                	__addressValue !== null
                SLOT_CONTACT ->
                	__contactValue !== null
                SLOT_PREVIOUS_CONTACTS ->
                	__previousContactsValue !== null
                SLOT_ALIASES ->
                	__aliasesValue !== null
                else -> throw IllegalArgumentException(
                    "Illegal property name" + 
                    " for \"demo.ReferenceModel\": " + 
                    prop
                )

            }

            override fun __isLoaded(prop: String): Boolean = when (prop) {
                "address" ->
                	__addressValue !== null
                "contact" ->
                	__contactValue !== null
                "previousContacts" ->
                	__previousContactsValue !== null
                "aliases" ->
                	__aliasesValue !== null
                else -> throw IllegalArgumentException(
                    "Illegal property name" + 
                    " for \"demo.ReferenceModel\": " + 
                    prop
                )

            }

            override fun __isVisible(prop: PropId): Boolean {
                val __visibility = this.__visibility ?: return true
                return when (prop.asIndex()) {
                    -1 ->
                    	__isVisible(prop.asName())
                    SLOT_ADDRESS ->
                    	__visibility.visible(SLOT_ADDRESS)
                    SLOT_CONTACT ->
                    	__visibility.visible(SLOT_CONTACT)
                    SLOT_PREVIOUS_CONTACTS ->
                    	__visibility.visible(SLOT_PREVIOUS_CONTACTS)
                    SLOT_ALIASES ->
                    	__visibility.visible(SLOT_ALIASES)
                    else -> true
                }
            }

            override fun __isVisible(prop: String): Boolean {
                val __visibility = this.__visibility ?: return true
                return when (prop) {
                    "address" ->
                    	__visibility.visible(SLOT_ADDRESS)
                    "contact" ->
                    	__visibility.visible(SLOT_CONTACT)
                    "previousContacts" ->
                    	__visibility.visible(SLOT_PREVIOUS_CONTACTS)
                    "aliases" ->
                    	__visibility.visible(SLOT_ALIASES)
                    else -> true
                }
            }

            public fun __shallowHashCode(): Int {
                var hash = __visibility?.hashCode() ?: 0
                if (__addressValue !== null) {
                    hash = 31 * hash + System.identityHashCode(__addressValue)
                }
                if (__contactValue !== null) {
                    hash = 31 * hash + System.identityHashCode(__contactValue)
                }
                if (__previousContactsValue !== null) {
                    hash = 31 * hash + System.identityHashCode(__previousContactsValue)
                }
                if (__aliasesValue !== null) {
                    hash = 31 * hash + __aliasesValue.hashCode()
                }
                return hash
            }

            override fun hashCode(): Int {
                var hash = __visibility?.hashCode() ?: 0
                if (__addressValue !== null) {
                    hash = 31 * hash + __addressValue.hashCode()
                }
                if (__contactValue !== null) {
                    hash = 31 * hash + __contactValue.hashCode()
                }
                if (__previousContactsValue !== null) {
                    hash = 31 * hash + __previousContactsValue.hashCode()
                }
                if (__aliasesValue !== null) {
                    hash = 31 * hash + __aliasesValue.hashCode()
                }
                return hash
            }

            override fun __hashCode(shallow: Boolean): Int = if (shallow) __shallowHashCode() else hashCode()

            public fun __shallowEquals(other: Any?): Boolean {
                val __other = other as? Implementor
                if (__other === null) {
                    return false
                }
                if (__isVisible(PropId.byIndex(SLOT_ADDRESS)) != __other.__isVisible(PropId.byIndex(SLOT_ADDRESS))) {
                    return false
                }
                val __addressLoaded = 
                    this.__addressValue !== null
                if (__addressLoaded != (__other.__isLoaded(PropId.byIndex(SLOT_ADDRESS)))) {
                    return false
                }
                if (__addressLoaded && this.__addressValue !== __other.address) {
                    return false
                }
                if (__isVisible(PropId.byIndex(SLOT_CONTACT)) != __other.__isVisible(PropId.byIndex(SLOT_CONTACT))) {
                    return false
                }
                val __contactLoaded = 
                    this.__contactValue !== null
                if (__contactLoaded != (__other.__isLoaded(PropId.byIndex(SLOT_CONTACT)))) {
                    return false
                }
                if (__contactLoaded && this.__contactValue !== __other.contact) {
                    return false
                }
                if (__isVisible(PropId.byIndex(SLOT_PREVIOUS_CONTACTS)) != __other.__isVisible(PropId.byIndex(SLOT_PREVIOUS_CONTACTS))) {
                    return false
                }
                val __previousContactsLoaded = 
                    this.__previousContactsValue !== null
                if (__previousContactsLoaded != (__other.__isLoaded(PropId.byIndex(SLOT_PREVIOUS_CONTACTS)))) {
                    return false
                }
                if (__previousContactsLoaded && this.__previousContactsValue !== __other.previousContacts) {
                    return false
                }
                if (__isVisible(PropId.byIndex(SLOT_ALIASES)) != __other.__isVisible(PropId.byIndex(SLOT_ALIASES))) {
                    return false
                }
                val __aliasesLoaded = 
                    this.__aliasesValue !== null
                if (__aliasesLoaded != (__other.__isLoaded(PropId.byIndex(SLOT_ALIASES)))) {
                    return false
                }
                if (__aliasesLoaded && this.__aliasesValue != __other.aliases) {
                    return false
                }
                return true
            }

            override fun equals(other: Any?): Boolean {
                val __other = other as? Implementor
                if (__other === null) {
                    return false
                }
                if (__isVisible(PropId.byIndex(SLOT_ADDRESS)) != __other.__isVisible(PropId.byIndex(SLOT_ADDRESS))) {
                    return false
                }
                val __addressLoaded = 
                    this.__addressValue !== null
                if (__addressLoaded != (__other.__isLoaded(PropId.byIndex(SLOT_ADDRESS)))) {
                    return false
                }
                if (__addressLoaded && this.__addressValue != __other.address) {
                    return false
                }
                if (__isVisible(PropId.byIndex(SLOT_CONTACT)) != __other.__isVisible(PropId.byIndex(SLOT_CONTACT))) {
                    return false
                }
                val __contactLoaded = 
                    this.__contactValue !== null
                if (__contactLoaded != (__other.__isLoaded(PropId.byIndex(SLOT_CONTACT)))) {
                    return false
                }
                if (__contactLoaded && this.__contactValue != __other.contact) {
                    return false
                }
                if (__isVisible(PropId.byIndex(SLOT_PREVIOUS_CONTACTS)) != __other.__isVisible(PropId.byIndex(SLOT_PREVIOUS_CONTACTS))) {
                    return false
                }
                val __previousContactsLoaded = 
                    this.__previousContactsValue !== null
                if (__previousContactsLoaded != (__other.__isLoaded(PropId.byIndex(SLOT_PREVIOUS_CONTACTS)))) {
                    return false
                }
                if (__previousContactsLoaded && this.__previousContactsValue != __other.previousContacts) {
                    return false
                }
                if (__isVisible(PropId.byIndex(SLOT_ALIASES)) != __other.__isVisible(PropId.byIndex(SLOT_ALIASES))) {
                    return false
                }
                val __aliasesLoaded = 
                    this.__aliasesValue !== null
                if (__aliasesLoaded != (__other.__isLoaded(PropId.byIndex(SLOT_ALIASES)))) {
                    return false
                }
                if (__aliasesLoaded && this.__aliasesValue != __other.aliases) {
                    return false
                }
                return true
            }

            override fun __equals(obj: Any?, shallow: Boolean): Boolean = if (shallow) __shallowEquals(obj) else equals(obj)

            override fun toString(): String = ImmutableObjects.toString(this)
        }

        @GeneratedBy(type = ReferenceModel::class)
        internal class DraftImpl(
            ctx: DraftContext?,
            base: ReferenceModel?,
        ) : Implementor,
            ReferenceModelDraft,
            DraftSpi {
            private val __ctx: DraftContext? = ctx

            private val __base: Impl? = base as Impl?

            private var __modified: Impl? = if (base === null) Impl() else null

            private var __resolving: Boolean = false

            private var __resolved: ReferenceModel? = null

            override var address: Address
                get() = __ctx().toDraftObject((__modified ?: __base!!).address)
                set(address) {
                    if (__resolved != null) {
                        throw IllegalStateException("The current draft has been resolved so it cannot be modified")
                    }
                    val __tmpModified = (__modified ?: __base!!.clone())
                            .also { __modified = it }
                    __tmpModified.__addressValue = address
                }

            override var contact: Contact
                get() = __ctx().toDraftObject((__modified ?: __base!!).contact)
                set(contact) {
                    if (__resolved != null) {
                        throw IllegalStateException("The current draft has been resolved so it cannot be modified")
                    }
                    val __tmpModified = (__modified ?: __base!!.clone())
                            .also { __modified = it }
                    __tmpModified.__contactValue = contact
                }

            override var previousContacts: List<Contact>
                get() = __ctx().toDraftList((__modified ?: __base!!).previousContacts, Contact::class.java, true)
                set(previousContacts) {
                    if (__resolved != null) {
                        throw IllegalStateException("The current draft has been resolved so it cannot be modified")
                    }
                    val __tmpModified = (__modified ?: __base!!.clone())
                            .also { __modified = it }
                    __tmpModified.__previousContactsValue = NonSharedList.of(__tmpModified.__previousContactsValue, previousContacts)
                }

            override var aliases: List<String?>
                get() = __ctx().toDraftList((__modified ?: __base!!).aliases, String::class.java, false)
                set(aliases) {
                    if (__resolved != null) {
                        throw IllegalStateException("The current draft has been resolved so it cannot be modified")
                    }
                    val __tmpModified = (__modified ?: __base!!.clone())
                            .also { __modified = it }
                    __tmpModified.__aliasesValue = NonSharedList.of(__tmpModified.__aliasesValue, aliases)
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

            override fun address(): AddressDraft {
                if (!__isLoaded(PropId.byIndex(SLOT_ADDRESS))) {
                    address = AddressDraft.`$`.produce()
                }
                return address as AddressDraft
            }

            override fun address(block: AddressDraft.() -> Unit) {
                address().apply(block)
            }

            override fun contact(): ContactDraft {
                if (!__isLoaded(PropId.byIndex(SLOT_CONTACT))) {
                    contact = ContactDraft.`$`.produce()
                }
                return contact as ContactDraft
            }

            override fun contact(block: ContactDraft.() -> Unit) {
                contact().apply(block)
            }

            override fun previousContacts(): MutableList<ContactDraft> {
                if (!__isLoaded(PropId.byIndex(SLOT_PREVIOUS_CONTACTS))) {
                    previousContacts = mutableListOf()
                }
                return previousContacts as MutableList<ContactDraft>
            }

            override fun aliases(): MutableList<String?> {
                if (!__isLoaded(PropId.byIndex(SLOT_ALIASES))) {
                    aliases = mutableListOf()
                }
                return aliases as MutableList<String?>
            }

            override fun __unload(prop: PropId) {
                if (__resolved != null) {
                    throw IllegalStateException("The current draft has been resolved so it cannot be modified")
                }
                when (prop.asIndex()) {
                    -1 ->
                    	__unload(prop.asName())
                    SLOT_ADDRESS ->
                    	(__modified ?: __base!!.clone())
                                .also { __modified = it }
                                .__addressValue = null
                    SLOT_CONTACT ->
                    	(__modified ?: __base!!.clone())
                                .also { __modified = it }
                                .__contactValue = null
                    SLOT_PREVIOUS_CONTACTS ->
                    	(__modified ?: __base!!.clone())
                                .also { __modified = it }
                                .__previousContactsValue = null
                    SLOT_ALIASES ->
                    	(__modified ?: __base!!.clone())
                                .also { __modified = it }
                                .__aliasesValue = null
                    else -> throw IllegalArgumentException(
                        "Illegal property name" + 
                        " for \"demo.ReferenceModel\": " + 
                        prop
                    )

                }
            }

            override fun __unload(prop: String) {
                if (__resolved != null) {
                    throw IllegalStateException("The current draft has been resolved so it cannot be modified")
                }
                when (prop) {
                    "address" ->
                    	(__modified ?: __base!!.clone())
                                .also { __modified = it }
                                .__addressValue = null
                    "contact" ->
                    	(__modified ?: __base!!.clone())
                                .also { __modified = it }
                                .__contactValue = null
                    "previousContacts" ->
                    	(__modified ?: __base!!.clone())
                                .also { __modified = it }
                                .__previousContactsValue = null
                    "aliases" ->
                    	(__modified ?: __base!!.clone())
                                .also { __modified = it }
                                .__aliasesValue = null
                    else -> throw IllegalArgumentException(
                        "Illegal property name" + 
                        " for \"demo.ReferenceModel\": " + 
                        prop
                    )

                }
            }

            override fun __set(prop: PropId, `value`: Any?) {
                when (prop.asIndex()) {
                    -1 ->
                    	__set(prop.asName(), value)
                    SLOT_ADDRESS ->
                    	this.address = value as Address?
                    	?: throw IllegalArgumentException("'address cannot be null")
                    SLOT_CONTACT ->
                    	this.contact = value as Contact?
                    	?: throw IllegalArgumentException("'contact cannot be null")
                    SLOT_PREVIOUS_CONTACTS ->
                    	this.previousContacts = value as List<Contact>?
                    	?: throw IllegalArgumentException("'previousContacts cannot be null")
                    SLOT_ALIASES ->
                    	this.aliases = value as List<String?>?
                    	?: throw IllegalArgumentException("'aliases cannot be null")
                    else -> throw IllegalArgumentException(
                        "Illegal property name" + 
                        " for \"demo.ReferenceModel\": " + 
                        prop
                    )

                }
            }

            override fun __set(prop: String, `value`: Any?) {
                when (prop) {
                    "address" ->
                    	this.address = value as Address?
                    	?: throw IllegalArgumentException("'address cannot be null")
                    "contact" ->
                    	this.contact = value as Contact?
                    	?: throw IllegalArgumentException("'contact cannot be null")
                    "previousContacts" ->
                    	this.previousContacts = value as List<Contact>?
                    	?: throw IllegalArgumentException("'previousContacts cannot be null")
                    "aliases" ->
                    	this.aliases = value as List<String?>?
                    	?: throw IllegalArgumentException("'aliases cannot be null")
                    else -> throw IllegalArgumentException(
                        "Illegal property name" + 
                        " for \"demo.ReferenceModel\": " + 
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
                        Visibility.of(4).also{
                            (__modified ?: __base!!.clone())
                            .also { __modified = it }.__visibility = it}
                    }
                    ?: return
                when (prop.asIndex()) {
                    -1 ->
                    	__show(prop.asName(), visible)
                    SLOT_ADDRESS ->
                    	__visibility.show(SLOT_ADDRESS, visible)
                    SLOT_CONTACT ->
                    	__visibility.show(SLOT_CONTACT, visible)
                    SLOT_PREVIOUS_CONTACTS ->
                    	__visibility.show(SLOT_PREVIOUS_CONTACTS, visible)
                    SLOT_ALIASES ->
                    	__visibility.show(SLOT_ALIASES, visible)
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
                        Visibility.of(4).also{
                            (__modified ?: __base!!.clone())
                            .also { __modified = it }.__visibility = it}
                    }
                    ?: return
                when (prop) {
                    "address" ->
                    	__visibility.show(SLOT_ADDRESS, visible)
                    "contact" ->
                    	__visibility.show(SLOT_CONTACT, visible)
                    "previousContacts" ->
                    	__visibility.show(SLOT_PREVIOUS_CONTACTS, visible)
                    "aliases" ->
                    	__visibility.show(SLOT_ALIASES, visible)
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
                    if (__tmpModified === null) {
                        if (__isLoaded(PropId.byIndex(SLOT_ADDRESS))) {
                            val oldValue = base!!.address
                            val newValue = __ctx.resolveObject(oldValue)
                            if (oldValue !== newValue) {
                                this@DraftImpl.address = newValue
                            }
                        }
                        if (__isLoaded(PropId.byIndex(SLOT_CONTACT))) {
                            val oldValue = base!!.contact
                            val newValue = __ctx.resolveObject(oldValue)
                            if (oldValue !== newValue) {
                                this@DraftImpl.contact = newValue
                            }
                        }
                        if (__isLoaded(PropId.byIndex(SLOT_PREVIOUS_CONTACTS))) {
                            val oldValue = base!!.previousContacts
                            val newValue = __ctx.resolveList(oldValue)
                            if (oldValue !== newValue) {
                                this@DraftImpl.previousContacts = newValue
                            }
                        }
                        if (__isLoaded(PropId.byIndex(SLOT_ALIASES))) {
                            val oldValue = base!!.aliases
                            val newValue = __ctx.resolveList(oldValue)
                            if (oldValue !== newValue) {
                                this@DraftImpl.aliases = newValue
                            }
                        }
                        __tmpModified = __modified
                    } else {
                        __tmpModified.__addressValue = __ctx.resolveObject(__tmpModified.__addressValue)
                        __tmpModified.__contactValue = __ctx.resolveObject(__tmpModified.__contactValue)
                        __tmpModified.__previousContactsValue = NonSharedList.of(__tmpModified.__previousContactsValue, __ctx.resolveList(__tmpModified.__previousContactsValue))
                        __tmpModified.__aliasesValue = NonSharedList.of(__tmpModified.__aliasesValue, __ctx.resolveList(__tmpModified.__aliasesValue))
                    }
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

    @GeneratedBy(type = ReferenceModel::class)
    public class Builder {
        private val __draft: `$`.DraftImpl

        public constructor(base: ReferenceModel?) {
            __draft = `$`.DraftImpl(null, base)
        }

        public constructor() : this(null)

        public fun address(address: Address?): Builder {
            if (address !== null) {
                __draft.address = address
                __draft.__show(PropId.byIndex(`$`.SLOT_ADDRESS), true)
            }
            return this
        }

        public fun contact(contact: Contact?): Builder {
            if (contact !== null) {
                __draft.contact = contact
                __draft.__show(PropId.byIndex(`$`.SLOT_CONTACT), true)
            }
            return this
        }

        public fun previousContacts(previousContacts: List<Contact>?): Builder {
            if (previousContacts !== null) {
                __draft.previousContacts = previousContacts
                __draft.__show(PropId.byIndex(`$`.SLOT_PREVIOUS_CONTACTS), true)
            }
            return this
        }

        public fun aliases(aliases: List<String?>?): Builder {
            if (aliases !== null) {
                __draft.aliases = aliases
                __draft.__show(PropId.byIndex(`$`.SLOT_ALIASES), true)
            }
            return this
        }

        public fun build(): ReferenceModel = __draft.__unwrap() as ReferenceModel
    }
}

@GeneratedBy(type = ReferenceModel::class)
public fun ImmutableCreator<ReferenceModel>.`by`(resolveImmediately: Boolean = false, block: ReferenceModelDraft.() -> Unit): ReferenceModel = ReferenceModelDraft.`$`.produce(null, resolveImmediately, block)

@GeneratedBy(type = ReferenceModel::class)
public fun ImmutableCreator<ReferenceModel>.`by`(base: ReferenceModel?, resolveImmediately: Boolean = false): ReferenceModel = ReferenceModelDraft.`$`.produce(base, resolveImmediately)

@GeneratedBy(type = ReferenceModel::class)
public fun ImmutableCreator<ReferenceModel>.`by`(
    base: ReferenceModel?,
    resolveImmediately: Boolean = false,
    block: ReferenceModelDraft.() -> Unit,
): ReferenceModel = ReferenceModelDraft.`$`.produce(base, resolveImmediately, block)

@GeneratedBy(type = ReferenceModel::class)
public fun ReferenceModel(resolveImmediately: Boolean = false, block: ReferenceModelDraft.() -> Unit): ReferenceModel = ReferenceModelDraft.`$`.produce(null, resolveImmediately, block)

@GeneratedBy(type = ReferenceModel::class)
public fun ReferenceModel(
    base: ReferenceModel?,
    resolveImmediately: Boolean = false,
    block: ReferenceModelDraft.() -> Unit,
): ReferenceModel = ReferenceModelDraft.`$`.produce(base, resolveImmediately, block)

@GeneratedBy(type = ReferenceModel::class)
public fun MutableList<ReferenceModelDraft>.addBy(resolveImmediately: Boolean = false, block: ReferenceModelDraft.() -> Unit): MutableList<ReferenceModelDraft> {
    add(ReferenceModelDraft.`$`.produce(null, resolveImmediately, block) as ReferenceModelDraft)
    return this
}

@GeneratedBy(type = ReferenceModel::class)
public fun MutableList<ReferenceModelDraft>.addBy(base: ReferenceModel?, resolveImmediately: Boolean = false): MutableList<ReferenceModelDraft> {
    add(ReferenceModelDraft.`$`.produce(base, resolveImmediately) as ReferenceModelDraft)
    return this
}

@GeneratedBy(type = ReferenceModel::class)
public fun MutableList<ReferenceModelDraft>.addBy(
    base: ReferenceModel?,
    resolveImmediately: Boolean = false,
    block: ReferenceModelDraft.() -> Unit,
): MutableList<ReferenceModelDraft> {
    add(ReferenceModelDraft.`$`.produce(base, resolveImmediately, block) as ReferenceModelDraft)
    return this
}

@GeneratedBy(type = ReferenceModel::class)
public fun ReferenceModel.copy(resolveImmediately: Boolean = false, block: ReferenceModelDraft.() -> Unit): ReferenceModel = ReferenceModelDraft.`$`.produce(this, resolveImmediately, block)
