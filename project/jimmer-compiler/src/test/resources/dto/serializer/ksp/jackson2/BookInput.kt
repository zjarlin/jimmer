package demo.dto

import com.fasterxml.jackson.`annotation`.JsonAlias
import com.fasterxml.jackson.`annotation`.JsonIgnore
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.`annotation`.JsonDeserialize
import com.fasterxml.jackson.databind.`annotation`.JsonPOJOBuilder
import com.fasterxml.jackson.databind.`annotation`.JsonSerialize
import demo.Book
import demo.BookDraft
import demo.BookProps
import demo.Point
import demo.PointDraft
import demo.`by`
import java.math.BigDecimal
import java.util.Objects
import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Unit
import kotlin.collections.Iterable
import kotlin.collections.List
import kotlin.jvm.JvmStatic
import org.babyfish.jimmer.EmbeddableDto
import org.babyfish.jimmer.Input
import org.babyfish.jimmer.`impl`.util.DtoPropAccessor
import org.babyfish.jimmer.`internal`.FixedInputField
import org.babyfish.jimmer.`internal`.GeneratedBy
import org.babyfish.jimmer.client.ApiIgnore
import org.babyfish.jimmer.kt.new
import org.babyfish.jimmer.sql.fetcher.DtoMetadata
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import com.fasterxml.jackson.databind.PropertyNamingStrategies as FasterxmlJacksonDatabindPropertyNamingStrategies
import com.fasterxml.jackson.databind.`annotation`.JsonNaming as FasterxmlJacksonDatabindAnnotationJsonNaming
import tools.jackson.databind.PropertyNamingStrategies as ToolsJacksonDatabindPropertyNamingStrategies
import tools.jackson.databind.`annotation`.JsonNaming as ToolsJacksonDatabindAnnotationJsonNaming

@GeneratedBy(file = "fixture/src/main/dto/demo/Book.dto", prompt = "The current DTO type is mutable. If you need to make it immutable, please remove the ksp argument `jimmer.dto.mutable`")
@FasterxmlJacksonDatabindAnnotationJsonNaming(
    `value` = FasterxmlJacksonDatabindPropertyNamingStrategies.SnakeCaseStrategy::class
)
@ToolsJacksonDatabindAnnotationJsonNaming(
    `value` = ToolsJacksonDatabindPropertyNamingStrategies.SnakeCaseStrategy::class
)
@JsonSerialize(using = BookInput.Serializer::class)
@JsonDeserialize(builder = BookInput.Builder::class)
public open class BookInput(
    @FixedInputField
    public var id: Long? = null,
    @FixedInputField
    public var isEnabled: Boolean = false,
    @get:JsonAlias(
        `value` = [
            "dto-name"
        ]
    )
    public var name: String? = null,
    edition: Int? = null,
    @ApiIgnore
    @get:JsonIgnore
    public var isEditionLoaded: Boolean = edition !== null,
    public var price: BigDecimal? = null,
    location: TargetOf_location? = null,
    @ApiIgnore
    @get:JsonIgnore
    public var isLocationLoaded: Boolean = location !== null,
) : Input<Book> {
    @field:JsonAlias(value = "base-edition")
    public var edition: Int? = edition
        set(edition) {
            field = edition
            isEditionLoaded = true
        }

    public var location: TargetOf_location? = location
        set(location) {
            field = location
            isLocationLoaded = true
        }

    public constructor(base: Book) : this(
        ID_ACCESSOR.get<Long?>(base), 
        base.active, 
        NAME_ACCESSOR.get<String?>(base), 
        base.edition,
        BookProps.EDITION.isLoaded(base), 
        PRICE_ACCESSOR.get<BigDecimal?>(base), 
        LOCATION_ACCESSOR.get<TargetOf_location?>(base),
        LOCATION_ACCESSOR.isLoaded(base)
    )

    override fun toEntity(): Book = new(Book::class).by(null, false, this@BookInput::toEntityImpl)

    public fun toEntity(block: BookDraft.() -> Unit): Book = new(Book::class).by {
        toEntityImpl(this)
        block(this)
    }

    internal fun __applyTo(_draft: BookDraft) {
        ID_ACCESSOR.set(_draft, id)
        _draft.active = isEnabled
        NAME_ACCESSOR.set(_draft, name)
        if (isEditionLoaded) {
            _draft.edition = edition
        }
        if (price != null) {
            PRICE_ACCESSOR.set(_draft, price)
        }
        if (isLocationLoaded) {
            LOCATION_ACCESSOR.set(_draft, location)
        }
    }

    /**
     * Avoid anonymous lambda affects coverage of non-kotlin-friendly tools such as jacoco
     */
    private fun toEntityImpl(_draft: BookDraft) {
        this.__applyTo(_draft)
    }

    public fun copy(
        id: Long? = this.id,
        isEnabled: Boolean = this.isEnabled,
        name: String? = this.name,
        edition: Int? = this.edition,
        isEditionLoaded: Boolean = this.isEditionLoaded,
        price: BigDecimal? = this.price,
        location: TargetOf_location? = this.location,
        isLocationLoaded: Boolean = this.isLocationLoaded,
    ): BookInput = BookInput(id, isEnabled, name, edition, isEditionLoaded, price, location, isLocationLoaded)

    public override fun hashCode(): Int {
        var _hash = Objects.hashCode(this.id)
        _hash = _hash * 31 + Objects.hashCode(this.isEnabled)
        _hash = _hash * 31 + Objects.hashCode(this.name)
        _hash = _hash * 31 + (if (this.isEditionLoaded) Objects.hashCode(this.edition) else 0)
        _hash = _hash * 31 + Objects.hashCode(this.isEditionLoaded)
        _hash = _hash * 31 + Objects.hashCode(this.price)
        _hash = _hash * 31 + (if (this.isLocationLoaded) Objects.hashCode(this.location) else 0)
        _hash = _hash * 31 + Objects.hashCode(this.isLocationLoaded)
        return _hash
    }

    public override fun equals(other: Any?): Boolean {
        if (other == null || this::class != other::class) {
            return false
        }
        val _other = other as BookInput
        if (!Objects.equals(this.id, _other.id)) {
            return false
        }
        if (!Objects.equals(this.isEnabled, _other.isEnabled)) {
            return false
        }
        if (!Objects.equals(this.name, _other.name)) {
            return false
        }
        if (this.isEditionLoaded != _other.isEditionLoaded) {
            return false
        }
        if (this.isEditionLoaded && !Objects.equals(this.edition, _other.edition)) {
            return false
        }
        if (!Objects.equals(this.price, _other.price)) {
            return false
        }
        if (this.isLocationLoaded != _other.isLocationLoaded) {
            return false
        }
        if (this.isLocationLoaded && !Objects.equals(this.location, _other.location)) {
            return false
        }
        return true
    }

    public override fun toString(): String {
        val builder = StringBuilder()
        var separator = ""
        builder.append("BookInput").append('(')
        builder.append(separator).append("id=").append(id)
        separator = ", "
        builder.append(separator).append("isEnabled=").append(isEnabled)
        separator = ", "
        builder.append(separator).append("name=").append(name)
        separator = ", "
        if (isEditionLoaded) {
            builder.append(separator).append("edition=").append(edition)
            separator = ", "
        }
        if (price != null) {
            builder.append(separator).append("price=").append(price)
            separator = ", "
        }
        if (isLocationLoaded) {
            builder.append(separator).append("location=").append(location)
            separator = ", "
        }
        builder.append(')')
        return builder.toString()
    }

    @GeneratedBy
    public companion object {
        @JvmStatic
        public val METADATA: DtoMetadata<Book, BookInput> = 
                    DtoMetadata<Book, BookInput>(
                        BookInput::class.java,
                        newFetcher(Book::class).by {
                            active()
                            name()
                            edition()
                            price()
                            location(TargetOf_location.METADATA.fetcher)
                        },
                        ::BookInput
                    )

        private val ID_ACCESSOR: DtoPropAccessor = DtoPropAccessor(
                    false,
                    intArrayOf(BookDraft.`$`.SLOT_ID)
                )

        private val NAME_ACCESSOR: DtoPropAccessor = DtoPropAccessor(
                    false,
                    intArrayOf(BookDraft.`$`.SLOT_NAME)
                )

        private val PRICE_ACCESSOR: DtoPropAccessor = DtoPropAccessor(
                    false,
                    intArrayOf(BookDraft.`$`.SLOT_PRICE)
                )

        private val LOCATION_ACCESSOR: DtoPropAccessor = DtoPropAccessor(
                    true,
                    intArrayOf(BookDraft.`$`.SLOT_LOCATION),
                    DtoPropAccessor.objectReferenceGetter<Point, demo.dto.BookInput.TargetOf_location> {
                        demo.dto.BookInput.TargetOf_location(it)
                    },
                    DtoPropAccessor.objectReferenceSetter<Point, demo.dto.BookInput.TargetOf_location> {
                        it.toImmutable()
                    }
                )
    }

    @GeneratedBy
    @JsonSerialize(using = TargetOf_location.Serializer::class)
    @JsonDeserialize(builder = TargetOf_location.Builder::class)
    public open class TargetOf_location(
        x: Long? = null,
        @ApiIgnore
        @get:JsonIgnore
        public var isXLoaded: Boolean = x !== null,
        y: Long? = null,
        @ApiIgnore
        @get:JsonIgnore
        public var isYLoaded: Boolean = y !== null,
    ) : EmbeddableDto<Point> {
        public var x: Long? = x
            set(x) {
                field = x
                isXLoaded = true
            }

        public var y: Long? = y
            set(y) {
                field = y
                isYLoaded = true
            }

        public constructor(base: Point) : this(
            X_ACCESSOR.get<Long?>(base),
            X_ACCESSOR.isLoaded(base)
        , 
            Y_ACCESSOR.get<Long?>(base),
            Y_ACCESSOR.isLoaded(base)
        )

        override fun toImmutable(): Point = new(Point::class).by(null, false, this@TargetOf_location::toImmutableImpl)

        public fun toImmutable(block: PointDraft.() -> Unit): Point = new(Point::class).by {
            toImmutableImpl(this)
            block(this)
        }

        internal fun __applyTo(_draft: PointDraft) {
            if (isXLoaded) {
                X_ACCESSOR.set(_draft, x)
            }
            if (isYLoaded) {
                Y_ACCESSOR.set(_draft, y)
            }
        }

        /**
         * Avoid anonymous lambda affects coverage of non-kotlin-friendly tools such as jacoco
         */
        private fun toImmutableImpl(_draft: PointDraft) {
            this.__applyTo(_draft)
        }

        public fun copy(
            x: Long? = this.x,
            isXLoaded: Boolean = this.isXLoaded,
            y: Long? = this.y,
            isYLoaded: Boolean = this.isYLoaded,
        ): TargetOf_location = TargetOf_location(x, isXLoaded, y, isYLoaded)

        public override fun hashCode(): Int {
            var _hash = 0
            _hash = _hash * 31 + (if (this.isXLoaded) Objects.hashCode(this.x) else 0)
            _hash = _hash * 31 + Objects.hashCode(this.isXLoaded)
            _hash = _hash * 31 + (if (this.isYLoaded) Objects.hashCode(this.y) else 0)
            _hash = _hash * 31 + Objects.hashCode(this.isYLoaded)
            return _hash
        }

        public override fun equals(other: Any?): Boolean {
            if (other == null || this::class != other::class) {
                return false
            }
            val _other = other as TargetOf_location
            if (this.isXLoaded != _other.isXLoaded) {
                return false
            }
            if (this.isXLoaded && !Objects.equals(this.x, _other.x)) {
                return false
            }
            if (this.isYLoaded != _other.isYLoaded) {
                return false
            }
            if (this.isYLoaded && !Objects.equals(this.y, _other.y)) {
                return false
            }
            return true
        }

        public override fun toString(): String {
            val builder = StringBuilder()
            var separator = ""
            builder.append("BookInput.TargetOf_location").append('(')
            if (isXLoaded) {
                builder.append(separator).append("x=").append(x)
                separator = ", "
            }
            if (isYLoaded) {
                builder.append(separator).append("y=").append(y)
                separator = ", "
            }
            builder.append(')')
            return builder.toString()
        }

        @GeneratedBy
        public companion object {
            @JvmStatic
            public val METADATA: DtoMetadata<Point, TargetOf_location> = 
                        DtoMetadata<Point, TargetOf_location>(
                            TargetOf_location::class.java,
                            newFetcher(Point::class).by {
                                x()
                                y()
                            },
                            ::TargetOf_location
                        )

            private val X_ACCESSOR: DtoPropAccessor = DtoPropAccessor(
                        false,
                        intArrayOf(PointDraft.`$`.SLOT_X)
                    )

            private val Y_ACCESSOR: DtoPropAccessor = DtoPropAccessor(
                        false,
                        intArrayOf(PointDraft.`$`.SLOT_Y)
                    )
        }

        public class Serializer : JsonSerializer<TargetOf_location>() {
            override fun serialize(
                input: TargetOf_location,
                gen: JsonGenerator,
                provider: SerializerProvider,
            ) {
                gen.writeStartObject()
                if (input.isXLoaded) {
                    provider.defaultSerializeField("x", input.x, gen)
                }
                if (input.isYLoaded) {
                    provider.defaultSerializeField("y", input.y, gen)
                }
                gen.writeEndObject()
            }
        }

        @GeneratedBy
        @JsonPOJOBuilder(withPrefix = "")
        public class Builder {
            private var x: Long? = null

            private var isXLoaded: Boolean = false

            private var y: Long? = null

            private var isYLoaded: Boolean = false

            public fun x(x: Long?): Builder {
                this.x = x
                this.isXLoaded = true
                return this
            }

            public fun y(y: Long?): Builder {
                this.y = y
                this.isYLoaded = true
                return this
            }

            public fun build(): TargetOf_location = TargetOf_location(
                // DYNAMIC
                x,
                isXLoaded,
                // DYNAMIC
                y,
                isYLoaded,
            )
        }
    }

    public class Serializer : JsonSerializer<BookInput>() {
        override fun serialize(
            input: BookInput,
            gen: JsonGenerator,
            provider: SerializerProvider,
        ) {
            gen.writeStartObject()
            provider.defaultSerializeField("id", input.id, gen)
            provider.defaultSerializeField("isEnabled", input.isEnabled, gen)
            provider.defaultSerializeField("name", input.name, gen)
            if (input.isEditionLoaded) {
                provider.defaultSerializeField("edition", input.edition, gen)
            }
            provider.defaultSerializeField("price", input.price, gen)
            if (input.isLocationLoaded) {
                provider.defaultSerializeField("location", input.location, gen)
            }
            gen.writeEndObject()
        }
    }

    @GeneratedBy
    @JsonPOJOBuilder(withPrefix = "")
    @FasterxmlJacksonDatabindAnnotationJsonNaming(value = FasterxmlJacksonDatabindPropertyNamingStrategies.SnakeCaseStrategy::class)
    public class Builder {
        private var id: Long? = null

        private var isIdLoaded: Boolean = false

        private var isEnabled: Boolean? = null

        private var name: String? = null

        private var edition: Int? = null

        private var isEditionLoaded: Boolean = false

        private var price: BigDecimal? = null

        private var location: TargetOf_location? = null

        private var isLocationLoaded: Boolean = false

        public fun id(id: Long?): Builder {
            this.id = id
            this.isIdLoaded = true
            return this
        }

        public fun isEnabled(isEnabled: Boolean): Builder {
            this.isEnabled = isEnabled
            return this
        }

        @JsonAlias(
            `value` = [
                "dto-name"
            ]
        )
        public fun name(name: String?): Builder {
            this.name = name
            return this
        }

        @JsonAlias("base-edition")
        public fun edition(edition: Int?): Builder {
            this.edition = edition
            this.isEditionLoaded = true
            return this
        }

        public fun price(price: BigDecimal?): Builder {
            this.price = price
            return this
        }

        public fun location(location: TargetOf_location?): Builder {
            this.location = location
            this.isLocationLoaded = true
            return this
        }

        public fun build(): BookInput = BookInput(
            // FIXED
            if (!isIdLoaded) {
                throw Input.unknownNullableProperty(BookInput::class.java, "id")} else {
                id}
            ,
            isEnabled ?: throw Input.unknownNonNullProperty(BookInput::class.java, "isEnabled"),
            name,
            // DYNAMIC
            edition,
            isEditionLoaded,
            price,
            // DYNAMIC
            location,
            isLocationLoaded,
        )
    }
}

@GeneratedBy(type = Book::class)
public fun Iterable<BookInput>.toEntities(): List<Book> = map(BookInput::toEntity)

@GeneratedBy(type = Book::class)
public fun Iterable<BookInput>.toEntities(block: BookDraft.() -> Unit): List<Book> = map {
    it.toEntity(block)
}
