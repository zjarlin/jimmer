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
import kotlin.Long
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
@GeneratedBy(type = BasicBook::class)
public interface BasicBookDraft : BasicBook, Draft {
    override var id: Long

    override var name: String

    @GeneratedBy(type = BasicBook::class)
    public object `$` {
        public const val SLOT_ID: Int = 0

        public const val SLOT_NAME: Int = 1

        public val type: ImmutableType = ImmutableType
            .newBuilder(
                "0.11.2",
                BasicBook::class,
                listOf(

                ),
            ) { ctx, base ->
                DraftImpl(ctx, base as BasicBook?)
            }
            .id(SLOT_ID, "id", Long::class.java)
            .add(SLOT_NAME, "name", ImmutablePropCategory.SCALAR, String::class.java, false)
            .build()

        public fun produce(base: BasicBook? = null, resolveImmediately: Boolean = false): BasicBook {
            val consumer = DraftConsumer<BasicBookDraft> {}
            return Internal.produce(type, base, resolveImmediately, consumer) as BasicBook
        }

        public fun produce(
            base: BasicBook? = null,
            resolveImmediately: Boolean = false,
            block: BasicBookDraft.() -> Unit,
        ): BasicBook {
            val consumer = DraftConsumer<BasicBookDraft> { block(it) }
            return Internal.produce(type, base, resolveImmediately, consumer) as BasicBook
        }

        @GeneratedBy(type = BasicBook::class)
        @JsonPropertyOrder("dummyPropForJacksonError__", "id", "name")
        private abstract interface Implementor : BasicBook, ImmutableSpi {
            public val dummyPropForJacksonError__: Int
                get() = throw ImmutableModuleRequiredException()

            override fun __get(prop: PropId): Any? = when (prop.asIndex()) {
                -1 ->
                	__get(prop.asName())
                SLOT_ID ->
                	id
                SLOT_NAME ->
                	name
                else -> throw IllegalArgumentException(
                    "Illegal property name" + 
                    " for \"demo.BasicBook\": " + 
                    prop
                )

            }

            override fun __get(prop: String): Any? = when (prop) {
                "id" ->
                	id
                "name" ->
                	name
                else -> throw IllegalArgumentException(
                    "Illegal property name" + 
                    " for \"demo.BasicBook\": " + 
                    prop
                )

            }

            override fun __type(): ImmutableType = `$`.type
        }

        @GeneratedBy(type = BasicBook::class)
        private class Impl : Implementor, Cloneable, Serializable {
            @get:JsonIgnore
            internal var __visibility: Visibility? = null

            @get:JsonIgnore
            internal var __idValue: Long = 0

            @get:JsonIgnore
            internal var __idLoaded: Boolean = false

            @get:JsonIgnore
            internal var __nameValue: String? = null

            override val id: Long
                get() {
                    if (!__idLoaded) {
                        throw UnloadedException(BasicBook::class.java, "id")
                    }
                    return __idValue
                }

            override val name: String
                get() {
                    val __nameValue = this.__nameValue
                    if (__nameValue === null) {
                        throw UnloadedException(BasicBook::class.java, "name")
                    }
                    return __nameValue
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
                SLOT_ID ->
                	__idLoaded
                SLOT_NAME ->
                	__nameValue !== null
                else -> throw IllegalArgumentException(
                    "Illegal property name" + 
                    " for \"demo.BasicBook\": " + 
                    prop
                )

            }

            override fun __isLoaded(prop: String): Boolean = when (prop) {
                "id" ->
                	__idLoaded
                "name" ->
                	__nameValue !== null
                else -> throw IllegalArgumentException(
                    "Illegal property name" + 
                    " for \"demo.BasicBook\": " + 
                    prop
                )

            }

            override fun __isVisible(prop: PropId): Boolean {
                val __visibility = this.__visibility ?: return true
                return when (prop.asIndex()) {
                    -1 ->
                    	__isVisible(prop.asName())
                    SLOT_ID ->
                    	__visibility.visible(SLOT_ID)
                    SLOT_NAME ->
                    	__visibility.visible(SLOT_NAME)
                    else -> true
                }
            }

            override fun __isVisible(prop: String): Boolean {
                val __visibility = this.__visibility ?: return true
                return when (prop) {
                    "id" ->
                    	__visibility.visible(SLOT_ID)
                    "name" ->
                    	__visibility.visible(SLOT_NAME)
                    else -> true
                }
            }

            public fun __shallowHashCode(): Int {
                var hash = __visibility?.hashCode() ?: 0
                if (__idLoaded) {
                    hash = 31 * hash + __idValue.hashCode()
                }
                if (__nameValue !== null) {
                    hash = 31 * hash + __nameValue.hashCode()
                }
                return hash
            }

            override fun hashCode(): Int {
                var hash = __visibility?.hashCode() ?: 0
                if (__idLoaded) {
                    hash = 31 * hash + __idValue.hashCode()
                    return hash
                }
                if (__nameValue !== null) {
                    hash = 31 * hash + __nameValue.hashCode()
                }
                return hash
            }

            override fun __hashCode(shallow: Boolean): Int = if (shallow) __shallowHashCode() else hashCode()

            public fun __shallowEquals(other: Any?): Boolean {
                val __other = other as? Implementor
                if (__other === null) {
                    return false
                }
                if (__isVisible(PropId.byIndex(SLOT_ID)) != __other.__isVisible(PropId.byIndex(SLOT_ID))) {
                    return false
                }
                val __idLoaded = 
                    this.__idLoaded
                if (__idLoaded != (__other.__isLoaded(PropId.byIndex(SLOT_ID)))) {
                    return false
                }
                if (__idLoaded && this.__idValue != __other.id) {
                    return false
                }
                if (__isVisible(PropId.byIndex(SLOT_NAME)) != __other.__isVisible(PropId.byIndex(SLOT_NAME))) {
                    return false
                }
                val __nameLoaded = 
                    this.__nameValue !== null
                if (__nameLoaded != (__other.__isLoaded(PropId.byIndex(SLOT_NAME)))) {
                    return false
                }
                if (__nameLoaded && this.__nameValue != __other.name) {
                    return false
                }
                return true
            }

            override fun equals(other: Any?): Boolean {
                val __other = other as? Implementor
                if (__other === null) {
                    return false
                }
                if (__isVisible(PropId.byIndex(SLOT_ID)) != __other.__isVisible(PropId.byIndex(SLOT_ID))) {
                    return false
                }
                val __idLoaded = 
                    this.__idLoaded
                if (__idLoaded != (__other.__isLoaded(PropId.byIndex(SLOT_ID)))) {
                    return false
                }
                if (__idLoaded) {
                    return this.__idValue == __other.id
                }
                if (__isVisible(PropId.byIndex(SLOT_NAME)) != __other.__isVisible(PropId.byIndex(SLOT_NAME))) {
                    return false
                }
                val __nameLoaded = 
                    this.__nameValue !== null
                if (__nameLoaded != (__other.__isLoaded(PropId.byIndex(SLOT_NAME)))) {
                    return false
                }
                if (__nameLoaded && this.__nameValue != __other.name) {
                    return false
                }
                return true
            }

            override fun __equals(obj: Any?, shallow: Boolean): Boolean = if (shallow) __shallowEquals(obj) else equals(obj)

            override fun toString(): String = ImmutableObjects.toString(this)
        }

        @GeneratedBy(type = BasicBook::class)
        internal class DraftImpl(
            ctx: DraftContext?,
            base: BasicBook?,
        ) : Implementor,
            BasicBookDraft,
            DraftSpi {
            private val __ctx: DraftContext? = ctx

            private val __base: Impl? = base as Impl?

            private var __modified: Impl? = if (base === null) Impl() else null

            private var __resolving: Boolean = false

            private var __resolved: BasicBook? = null

            override var id: Long
                get() = (__modified ?: __base!!).id
                set(id) {
                    if (__resolved != null) {
                        throw IllegalStateException("The current draft has been resolved so it cannot be modified")
                    }
                    val __tmpModified = (__modified ?: __base!!.clone())
                            .also { __modified = it }
                    __tmpModified.__idValue = id
                    __tmpModified.__idLoaded = true
                }

            override var name: String
                get() = (__modified ?: __base!!).name
                set(name) {
                    if (__resolved != null) {
                        throw IllegalStateException("The current draft has been resolved so it cannot be modified")
                    }
                    val __tmpModified = (__modified ?: __base!!.clone())
                            .also { __modified = it }
                    __tmpModified.__nameValue = name
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
                    SLOT_ID ->
                    	 {
                            (__modified ?: __base!!.clone())
                                    .also { __modified = it }
                                    .__idValue = 0
                            (__modified ?: __base!!.clone())
                                    .also { __modified = it }
                                    .__idLoaded = false
                        }
                    SLOT_NAME ->
                    	(__modified ?: __base!!.clone())
                                .also { __modified = it }
                                .__nameValue = null
                    else -> throw IllegalArgumentException(
                        "Illegal property name" + 
                        " for \"demo.BasicBook\": " + 
                        prop
                    )

                }
            }

            override fun __unload(prop: String) {
                if (__resolved != null) {
                    throw IllegalStateException("The current draft has been resolved so it cannot be modified")
                }
                when (prop) {
                    "id" ->
                    	 {
                            (__modified ?: __base!!.clone())
                                    .also { __modified = it }
                                    .__idValue = 0
                            (__modified ?: __base!!.clone())
                                    .also { __modified = it }
                                    .__idLoaded = false
                        }
                    "name" ->
                    	(__modified ?: __base!!.clone())
                                .also { __modified = it }
                                .__nameValue = null
                    else -> throw IllegalArgumentException(
                        "Illegal property name" + 
                        " for \"demo.BasicBook\": " + 
                        prop
                    )

                }
            }

            override fun __set(prop: PropId, `value`: Any?) {
                when (prop.asIndex()) {
                    -1 ->
                    	__set(prop.asName(), value)
                    SLOT_ID ->
                    	this.id = value as Long?
                    	?: throw IllegalArgumentException("'id cannot be null")
                    SLOT_NAME ->
                    	this.name = value as String?
                    	?: throw IllegalArgumentException("'name cannot be null")
                    else -> throw IllegalArgumentException(
                        "Illegal property name" + 
                        " for \"demo.BasicBook\": " + 
                        prop
                    )

                }
            }

            override fun __set(prop: String, `value`: Any?) {
                when (prop) {
                    "id" ->
                    	this.id = value as Long?
                    	?: throw IllegalArgumentException("'id cannot be null")
                    "name" ->
                    	this.name = value as String?
                    	?: throw IllegalArgumentException("'name cannot be null")
                    else -> throw IllegalArgumentException(
                        "Illegal property name" + 
                        " for \"demo.BasicBook\": " + 
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
                    SLOT_ID ->
                    	__visibility.show(SLOT_ID, visible)
                    SLOT_NAME ->
                    	__visibility.show(SLOT_NAME, visible)
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
                    "id" ->
                    	__visibility.show(SLOT_ID, visible)
                    "name" ->
                    	__visibility.show(SLOT_NAME, visible)
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

    @GeneratedBy(type = BasicBook::class)
    public class Builder {
        private val __draft: `$`.DraftImpl

        public constructor(base: BasicBook?) {
            __draft = `$`.DraftImpl(null, base)
        }

        public constructor() : this(null)

        public fun id(id: Long?): Builder {
            if (id !== null) {
                __draft.id = id
                __draft.__show(PropId.byIndex(`$`.SLOT_ID), true)
            }
            return this
        }

        public fun name(name: String?): Builder {
            if (name !== null) {
                __draft.name = name
                __draft.__show(PropId.byIndex(`$`.SLOT_NAME), true)
            }
            return this
        }

        public fun build(): BasicBook = __draft.__unwrap() as BasicBook
    }
}

@GeneratedBy(type = BasicBook::class)
public fun ImmutableCreator<BasicBook>.`by`(resolveImmediately: Boolean = false, block: BasicBookDraft.() -> Unit): BasicBook = BasicBookDraft.`$`.produce(null, resolveImmediately, block)

@GeneratedBy(type = BasicBook::class)
public fun ImmutableCreator<BasicBook>.`by`(base: BasicBook?, resolveImmediately: Boolean = false): BasicBook = BasicBookDraft.`$`.produce(base, resolveImmediately)

@GeneratedBy(type = BasicBook::class)
public fun ImmutableCreator<BasicBook>.`by`(
    base: BasicBook?,
    resolveImmediately: Boolean = false,
    block: BasicBookDraft.() -> Unit,
): BasicBook = BasicBookDraft.`$`.produce(base, resolveImmediately, block)

@GeneratedBy(type = BasicBook::class)
public fun BasicBook(resolveImmediately: Boolean = false, block: BasicBookDraft.() -> Unit): BasicBook = BasicBookDraft.`$`.produce(null, resolveImmediately, block)

@GeneratedBy(type = BasicBook::class)
public fun BasicBook(
    base: BasicBook?,
    resolveImmediately: Boolean = false,
    block: BasicBookDraft.() -> Unit,
): BasicBook = BasicBookDraft.`$`.produce(base, resolveImmediately, block)

@GeneratedBy(type = BasicBook::class)
public fun MutableList<BasicBookDraft>.addBy(resolveImmediately: Boolean = false, block: BasicBookDraft.() -> Unit): MutableList<BasicBookDraft> {
    add(BasicBookDraft.`$`.produce(null, resolveImmediately, block) as BasicBookDraft)
    return this
}

@GeneratedBy(type = BasicBook::class)
public fun MutableList<BasicBookDraft>.addBy(base: BasicBook?, resolveImmediately: Boolean = false): MutableList<BasicBookDraft> {
    add(BasicBookDraft.`$`.produce(base, resolveImmediately) as BasicBookDraft)
    return this
}

@GeneratedBy(type = BasicBook::class)
public fun MutableList<BasicBookDraft>.addBy(
    base: BasicBook?,
    resolveImmediately: Boolean = false,
    block: BasicBookDraft.() -> Unit,
): MutableList<BasicBookDraft> {
    add(BasicBookDraft.`$`.produce(base, resolveImmediately, block) as BasicBookDraft)
    return this
}

@GeneratedBy(type = BasicBook::class)
public fun BasicBook.copy(resolveImmediately: Boolean = false, block: BasicBookDraft.() -> Unit): BasicBook = BasicBookDraft.`$`.produce(this, resolveImmediately, block)
