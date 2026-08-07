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
@GeneratedBy(type = Address::class)
public interface AddressDraft : Address, Draft {
    override var city: String

    @GeneratedBy(type = Address::class)
    public object `$` {
        public const val SLOT_CITY: Int = 0

        public val type: ImmutableType = ImmutableType
            .newBuilder(
                "0.11.6",
                Address::class,
                listOf(

                ),
            ) { ctx, base ->
                DraftImpl(ctx, base as Address?)
            }
            .add(SLOT_CITY, "city", ImmutablePropCategory.SCALAR, String::class.java, false)
            .build()

        public fun produce(base: Address? = null, resolveImmediately: Boolean = false): Address {
            val consumer = DraftConsumer<AddressDraft> {}
            return Internal.produce(type, base, resolveImmediately, consumer) as Address
        }

        public fun produce(
            base: Address? = null,
            resolveImmediately: Boolean = false,
            block: AddressDraft.() -> Unit,
        ): Address {
            val consumer = DraftConsumer<AddressDraft> { block(it) }
            return Internal.produce(type, base, resolveImmediately, consumer) as Address
        }

        @GeneratedBy(type = Address::class)
        @JsonPropertyOrder("dummyPropForJacksonError__", "city")
        private abstract interface Implementor : Address, ImmutableSpi {
            public val dummyPropForJacksonError__: Int
                get() = throw ImmutableModuleRequiredException()

            override fun __get(prop: PropId): Any? = when (prop.asIndex()) {
                -1 ->
                	__get(prop.asName())
                SLOT_CITY ->
                	city
                else -> throw IllegalArgumentException(
                    "Illegal property name" + 
                    " for \"demo.Address\": " + 
                    prop
                )

            }

            override fun __get(prop: String): Any? = when (prop) {
                "city" ->
                	city
                else -> throw IllegalArgumentException(
                    "Illegal property name" + 
                    " for \"demo.Address\": " + 
                    prop
                )

            }

            override fun __type(): ImmutableType = `$`.type
        }

        @GeneratedBy(type = Address::class)
        private class Impl : Implementor, Cloneable, Serializable {
            @get:JsonIgnore
            internal var __visibility: Visibility? = null

            @get:JsonIgnore
            internal var __cityValue: String? = null

            override val city: String
                get() {
                    val __cityValue = this.__cityValue
                    if (__cityValue === null) {
                        throw UnloadedException(Address::class.java, "city")
                    }
                    return __cityValue
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
                SLOT_CITY ->
                	__cityValue !== null
                else -> throw IllegalArgumentException(
                    "Illegal property name" + 
                    " for \"demo.Address\": " + 
                    prop
                )

            }

            override fun __isLoaded(prop: String): Boolean = when (prop) {
                "city" ->
                	__cityValue !== null
                else -> throw IllegalArgumentException(
                    "Illegal property name" + 
                    " for \"demo.Address\": " + 
                    prop
                )

            }

            override fun __isVisible(prop: PropId): Boolean {
                val __visibility = this.__visibility ?: return true
                return when (prop.asIndex()) {
                    -1 ->
                    	__isVisible(prop.asName())
                    SLOT_CITY ->
                    	__visibility.visible(SLOT_CITY)
                    else -> true
                }
            }

            override fun __isVisible(prop: String): Boolean {
                val __visibility = this.__visibility ?: return true
                return when (prop) {
                    "city" ->
                    	__visibility.visible(SLOT_CITY)
                    else -> true
                }
            }

            public fun __shallowHashCode(): Int {
                var hash = __visibility?.hashCode() ?: 0
                if (__cityValue !== null) {
                    hash = 31 * hash + __cityValue.hashCode()
                }
                return hash
            }

            override fun hashCode(): Int {
                var hash = __visibility?.hashCode() ?: 0
                if (__cityValue !== null) {
                    hash = 31 * hash + __cityValue.hashCode()
                }
                return hash
            }

            override fun __hashCode(shallow: Boolean): Int = if (shallow) __shallowHashCode() else hashCode()

            public fun __shallowEquals(other: Any?): Boolean {
                val __other = other as? Implementor
                if (__other === null) {
                    return false
                }
                if (__isVisible(PropId.byIndex(SLOT_CITY)) != __other.__isVisible(PropId.byIndex(SLOT_CITY))) {
                    return false
                }
                val __cityLoaded = 
                    this.__cityValue !== null
                if (__cityLoaded != (__other.__isLoaded(PropId.byIndex(SLOT_CITY)))) {
                    return false
                }
                if (__cityLoaded && this.__cityValue != __other.city) {
                    return false
                }
                return true
            }

            override fun equals(other: Any?): Boolean {
                val __other = other as? Implementor
                if (__other === null) {
                    return false
                }
                if (__isVisible(PropId.byIndex(SLOT_CITY)) != __other.__isVisible(PropId.byIndex(SLOT_CITY))) {
                    return false
                }
                val __cityLoaded = 
                    this.__cityValue !== null
                if (__cityLoaded != (__other.__isLoaded(PropId.byIndex(SLOT_CITY)))) {
                    return false
                }
                if (__cityLoaded && this.__cityValue != __other.city) {
                    return false
                }
                return true
            }

            override fun __equals(obj: Any?, shallow: Boolean): Boolean = if (shallow) __shallowEquals(obj) else equals(obj)

            override fun toString(): String = ImmutableObjects.toString(this)
        }

        @GeneratedBy(type = Address::class)
        internal class DraftImpl(
            ctx: DraftContext?,
            base: Address?,
        ) : Implementor,
            AddressDraft,
            DraftSpi {
            private val __ctx: DraftContext? = ctx

            private val __base: Impl? = base as Impl?

            private var __modified: Impl? = if (base === null) Impl() else null

            private var __resolving: Boolean = false

            private var __resolved: Address? = null

            override var city: String
                get() = (__modified ?: __base!!).city
                set(city) {
                    if (__resolved != null) {
                        throw IllegalStateException("The current draft has been resolved so it cannot be modified")
                    }
                    val __tmpModified = (__modified ?: __base!!.clone())
                            .also { __modified = it }
                    __tmpModified.__cityValue = city
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
                    SLOT_CITY ->
                    	(__modified ?: __base!!.clone())
                                .also { __modified = it }
                                .__cityValue = null
                    else -> throw IllegalArgumentException(
                        "Illegal property name" + 
                        " for \"demo.Address\": " + 
                        prop
                    )

                }
            }

            override fun __unload(prop: String) {
                if (__resolved != null) {
                    throw IllegalStateException("The current draft has been resolved so it cannot be modified")
                }
                when (prop) {
                    "city" ->
                    	(__modified ?: __base!!.clone())
                                .also { __modified = it }
                                .__cityValue = null
                    else -> throw IllegalArgumentException(
                        "Illegal property name" + 
                        " for \"demo.Address\": " + 
                        prop
                    )

                }
            }

            override fun __set(prop: PropId, `value`: Any?) {
                when (prop.asIndex()) {
                    -1 ->
                    	__set(prop.asName(), value)
                    SLOT_CITY ->
                    	this.city = value as String?
                    	?: throw IllegalArgumentException("'city cannot be null")
                    else -> throw IllegalArgumentException(
                        "Illegal property name" + 
                        " for \"demo.Address\": " + 
                        prop
                    )

                }
            }

            override fun __set(prop: String, `value`: Any?) {
                when (prop) {
                    "city" ->
                    	this.city = value as String?
                    	?: throw IllegalArgumentException("'city cannot be null")
                    else -> throw IllegalArgumentException(
                        "Illegal property name" + 
                        " for \"demo.Address\": " + 
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
                    SLOT_CITY ->
                    	__visibility.show(SLOT_CITY, visible)
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
                    "city" ->
                    	__visibility.show(SLOT_CITY, visible)
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

    @GeneratedBy(type = Address::class)
    public class Builder {
        private val __draft: `$`.DraftImpl

        public constructor(base: Address?) {
            __draft = `$`.DraftImpl(null, base)
        }

        public constructor() : this(null)

        public fun city(city: String?): Builder {
            if (city !== null) {
                __draft.city = city
                __draft.__show(PropId.byIndex(`$`.SLOT_CITY), true)
            }
            return this
        }

        public fun build(): Address = __draft.__unwrap() as Address
    }
}

@GeneratedBy(type = Address::class)
public fun ImmutableCreator<Address>.`by`(resolveImmediately: Boolean = false, block: AddressDraft.() -> Unit): Address = AddressDraft.`$`.produce(null, resolveImmediately, block)

@GeneratedBy(type = Address::class)
public fun ImmutableCreator<Address>.`by`(base: Address?, resolveImmediately: Boolean = false): Address = AddressDraft.`$`.produce(base, resolveImmediately)

@GeneratedBy(type = Address::class)
public fun ImmutableCreator<Address>.`by`(
    base: Address?,
    resolveImmediately: Boolean = false,
    block: AddressDraft.() -> Unit,
): Address = AddressDraft.`$`.produce(base, resolveImmediately, block)

@GeneratedBy(type = Address::class)
public fun Address(resolveImmediately: Boolean = false, block: AddressDraft.() -> Unit): Address = AddressDraft.`$`.produce(null, resolveImmediately, block)

@GeneratedBy(type = Address::class)
public fun Address(
    base: Address?,
    resolveImmediately: Boolean = false,
    block: AddressDraft.() -> Unit,
): Address = AddressDraft.`$`.produce(base, resolveImmediately, block)

@GeneratedBy(type = Address::class)
public fun MutableList<AddressDraft>.addBy(resolveImmediately: Boolean = false, block: AddressDraft.() -> Unit): MutableList<AddressDraft> {
    add(AddressDraft.`$`.produce(null, resolveImmediately, block) as AddressDraft)
    return this
}

@GeneratedBy(type = Address::class)
public fun MutableList<AddressDraft>.addBy(base: Address?, resolveImmediately: Boolean = false): MutableList<AddressDraft> {
    add(AddressDraft.`$`.produce(base, resolveImmediately) as AddressDraft)
    return this
}

@GeneratedBy(type = Address::class)
public fun MutableList<AddressDraft>.addBy(
    base: Address?,
    resolveImmediately: Boolean = false,
    block: AddressDraft.() -> Unit,
): MutableList<AddressDraft> {
    add(AddressDraft.`$`.produce(base, resolveImmediately, block) as AddressDraft)
    return this
}

@GeneratedBy(type = Address::class)
public fun Address.copy(resolveImmediately: Boolean = false, block: AddressDraft.() -> Unit): Address = AddressDraft.`$`.produce(this, resolveImmediately, block)
