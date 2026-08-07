package demo.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import demo.Book;
import demo.BookDraft;
import demo.BookFetcher;
import demo.Point;
import demo.PointDraft;
import demo.PointFetcher;
import java.io.IOException;
import java.lang.Boolean;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.math.BigDecimal;
import java.util.Objects;
import org.babyfish.jimmer.EmbeddableDto;
import org.babyfish.jimmer.Input;
import org.babyfish.jimmer.client.ApiIgnore;
import org.babyfish.jimmer.impl.util.DtoPropAccessor;
import org.babyfish.jimmer.internal.FixedInputField;
import org.babyfish.jimmer.internal.GeneratedBy;
import org.babyfish.jimmer.sql.fetcher.DtoMetadata;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@GeneratedBy(
        file = "fixture/src/main/dto/demo/Book.dto"
)
@JsonSerialize(
        using = BookInput.Serializer.class
)
@JsonDeserialize(
        builder = BookInput.Builder.class
)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@tools.jackson.databind.annotation.JsonNaming(tools.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy.class)
public class BookInput implements Input<Book> {
    public static final DtoMetadata<Book, BookInput> METADATA = 
        new DtoMetadata<Book, BookInput>(
            BookInput.class,
            BookFetcher.$
                .active()
                .name()
                .edition()
                .price()
                .location(TargetOf_location.METADATA.getFetcher()),
            BookInput::new
    );

    private static final DtoPropAccessor ID_ACCESSOR = new DtoPropAccessor(
        false,
        new int[] { BookDraft.Producer.SLOT_ID }
    );

    private static final DtoPropAccessor NAME_ACCESSOR = new DtoPropAccessor(
        false,
        new int[] { BookDraft.Producer.SLOT_NAME }
    );

    private static final DtoPropAccessor EDITION_ACCESSOR = new DtoPropAccessor(
        true,
        new int[] { BookDraft.Producer.SLOT_EDITION }
    );

    private static final DtoPropAccessor PRICE_ACCESSOR = new DtoPropAccessor(
        false,
        new int[] { BookDraft.Producer.SLOT_PRICE }
    );

    private static final DtoPropAccessor LOCATION_ACCESSOR = new DtoPropAccessor(
        true,
        new int[] { BookDraft.Producer.SLOT_LOCATION },
        DtoPropAccessor.<Point, TargetOf_location>objectReferenceGetter(TargetOf_location::new),
        DtoPropAccessor.objectReferenceSetter(TargetOf_location::toImmutable)
    );

    @FixedInputField
    private Long id;

    @FixedInputField
    private Boolean isEnabled;

    @JsonAlias({
                "dto-name"
            })
    private String name;

    @JsonAlias("base-edition")
    private Integer edition;

    private boolean _isEditionLoaded;

    private BigDecimal price;

    private TargetOf_location location;

    private boolean _isLocationLoaded;

    public BookInput() {
    }

    public BookInput(@NonNull Book base) {
        this.id = ID_ACCESSOR.get(base);
        this.isEnabled = base.active();
        this.name = NAME_ACCESSOR.get(base);
        this.edition = EDITION_ACCESSOR.get(base);
        this._isEditionLoaded = EDITION_ACCESSOR.isLoaded(base);
        this.price = PRICE_ACCESSOR.get(base);
        this.location = LOCATION_ACCESSOR.get(base);
        this._isLocationLoaded = LOCATION_ACCESSOR.isLoaded(base);
    }

    @Nullable
    public Long getId() {
        return id;
    }

    public void setId(@Nullable Long id) {
        this.id = id;
    }

    public boolean isEnabled() {
        if (isEnabled == null) {
            throw new IllegalStateException("The property \"isEnabled\" is not specified");
        }
        return isEnabled;
    }

    public void setEnabled(boolean isEnabled) {
        this.isEnabled = isEnabled;
    }

    @Nullable
    @JsonAlias({
                "dto-name"
            })
    public String getName() {
        return name;
    }

    public void setName(@Nullable String name) {
        this.name = name;
    }

    @Nullable
    @JsonAlias("base-edition")
    public Integer getEdition() {
        if (!_isEditionLoaded) {
            throw new IllegalStateException("The property \"edition\" is not specified");
        }
        return edition;
    }

    public void setEdition(@Nullable Integer edition) {
        this.edition = edition;
        this._isEditionLoaded = true;
    }

    @ApiIgnore
    @JsonIgnore
    public boolean isEditionLoaded() {
        return this._isEditionLoaded;
    }

    void setEditionLoaded(boolean loaded) {
        this._isEditionLoaded = loaded;
    }

    @Nullable
    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(@Nullable BigDecimal price) {
        this.price = price;
    }

    @Nullable
    public TargetOf_location getLocation() {
        if (!_isLocationLoaded) {
            throw new IllegalStateException("The property \"location\" is not specified");
        }
        return location;
    }

    public void setLocation(@Nullable TargetOf_location location) {
        this.location = location;
        this._isLocationLoaded = true;
    }

    @ApiIgnore
    @JsonIgnore
    public boolean isLocationLoaded() {
        return this._isLocationLoaded;
    }

    void setLocationLoaded(boolean loaded) {
        this._isLocationLoaded = loaded;
    }

    private void __applyTo(BookDraft __draft) {
        ID_ACCESSOR.set(__draft, this.id);
        __draft.setActive(this.isEnabled);
        NAME_ACCESSOR.set(__draft, this.name);
        if (this._isEditionLoaded) {
            __draft.setEdition(this.edition);
        }
        if (this.price != null) {
            PRICE_ACCESSOR.set(__draft, this.price);
        }
        if (this._isLocationLoaded) {
            LOCATION_ACCESSOR.set(__draft, this.location);
        }
    }

    @Override
    public Book toEntity() {
        return toEntityById(null);
    }

    public Book toEntityById(@Nullable Long id) {
        return BookDraft.$.produce(__draft -> {
            this.__applyTo(__draft);
            if (id != null) {
                __draft.setId(id);
            }
        });
    }

    @Override
    public int hashCode() {
        int hash = Objects.hashCode(this.id);
        hash = hash * 31 + Objects.hashCode(this.isEnabled);
        hash = hash * 31 + Objects.hashCode(this.name);
        hash = hash * 31 + (this._isEditionLoaded ? Objects.hashCode(this.edition) : 0);
        hash = hash * 31 + Objects.hashCode(this._isEditionLoaded);
        hash = hash * 31 + Objects.hashCode(this.price);
        hash = hash * 31 + (this._isLocationLoaded ? Objects.hashCode(this.location) : 0);
        hash = hash * 31 + Objects.hashCode(this._isLocationLoaded);
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }
        BookInput other = (BookInput) o;
        if (!Objects.equals(this.id, other.id)) {
            return false;
        }
        if (!Objects.equals(this.isEnabled, other.isEnabled)) {
            return false;
        }
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        if (this._isEditionLoaded != other._isEditionLoaded) {
            return false;
        }
        if (this._isEditionLoaded && !Objects.equals(this.edition, other.edition)) {
            return false;
        }
        if (!Objects.equals(this.price, other.price)) {
            return false;
        }
        if (this._isLocationLoaded != other._isLocationLoaded) {
            return false;
        }
        if (this._isLocationLoaded && !Objects.equals(this.location, other.location)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("BookInput").append('(');
        String _sp = "";
        builder.append("").append("id=").append(id);
        _sp = ", ";
        builder.append(_sp).append("isEnabled=").append(isEnabled);
        _sp = ", ";
        builder.append(_sp).append("name=").append(name);
        _sp = ", ";
        if (_isEditionLoaded) {
            builder.append(_sp).append("edition=").append(edition);
            _sp = ", ";
        }
        if (price != null) {
            builder.append(_sp).append("price=").append(price);
            _sp = ", ";
        }
        if (_isLocationLoaded) {
            builder.append(_sp).append("location=").append(location);
            _sp = ", ";
        }
        builder.append(')');
        return builder.toString();
    }

    @GeneratedBy
    @JsonSerialize(
            using = TargetOf_location.Serializer.class
    )
    @JsonDeserialize(
            builder = TargetOf_location.Builder.class
    )
    public static class TargetOf_location implements EmbeddableDto<Point> {
        public static final DtoMetadata<Point, TargetOf_location> METADATA = 
            new DtoMetadata<Point, TargetOf_location>(
                TargetOf_location.class,
                PointFetcher.$
                    .x()
                    .y(),
                TargetOf_location::new
        );

        private static final DtoPropAccessor X_ACCESSOR = new DtoPropAccessor(
            false,
            new int[] { PointDraft.Producer.SLOT_X }
        );

        private static final DtoPropAccessor Y_ACCESSOR = new DtoPropAccessor(
            false,
            new int[] { PointDraft.Producer.SLOT_Y }
        );

        private Long x;

        private boolean _isXLoaded;

        private Long y;

        private boolean _isYLoaded;

        public TargetOf_location() {
        }

        public TargetOf_location(@NonNull Point base) {
            this.x = X_ACCESSOR.get(base);
            this._isXLoaded = X_ACCESSOR.isLoaded(base);
            this.y = Y_ACCESSOR.get(base);
            this._isYLoaded = Y_ACCESSOR.isLoaded(base);
        }

        @Nullable
        public Long getX() {
            if (!_isXLoaded) {
                throw new IllegalStateException("The property \"x\" is not specified");
            }
            return x;
        }

        public void setX(@Nullable Long x) {
            this.x = x;
            this._isXLoaded = true;
        }

        @ApiIgnore
        @JsonIgnore
        public boolean isXLoaded() {
            return this._isXLoaded;
        }

        void setXLoaded(boolean loaded) {
            this._isXLoaded = loaded;
        }

        @Nullable
        public Long getY() {
            if (!_isYLoaded) {
                throw new IllegalStateException("The property \"y\" is not specified");
            }
            return y;
        }

        public void setY(@Nullable Long y) {
            this.y = y;
            this._isYLoaded = true;
        }

        @ApiIgnore
        @JsonIgnore
        public boolean isYLoaded() {
            return this._isYLoaded;
        }

        void setYLoaded(boolean loaded) {
            this._isYLoaded = loaded;
        }

        private void __applyTo(PointDraft __draft) {
            if (this._isXLoaded) {
                X_ACCESSOR.set(__draft, this.x);
            }
            if (this._isYLoaded) {
                Y_ACCESSOR.set(__draft, this.y);
            }
        }

        @Override
        public Point toImmutable() {
            return PointDraft.$.produce(__draft -> {
                this.__applyTo(__draft);
            });
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = hash * 31 + (this._isXLoaded ? Objects.hashCode(this.x) : 0);
            hash = hash * 31 + Objects.hashCode(this._isXLoaded);
            hash = hash * 31 + (this._isYLoaded ? Objects.hashCode(this.y) : 0);
            hash = hash * 31 + Objects.hashCode(this._isYLoaded);
            return hash;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || this.getClass() != o.getClass()) {
                return false;
            }
            TargetOf_location other = (TargetOf_location) o;
            if (this._isXLoaded != other._isXLoaded) {
                return false;
            }
            if (this._isXLoaded && !Objects.equals(this.x, other.x)) {
                return false;
            }
            if (this._isYLoaded != other._isYLoaded) {
                return false;
            }
            if (this._isYLoaded && !Objects.equals(this.y, other.y)) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("BookInput.TargetOf_location").append('(');
            String _sp = "";
            if (_isXLoaded) {
                builder.append("").append("x=").append(x);
                _sp = ", ";
            }
            if (_isYLoaded) {
                builder.append(_sp).append("y=").append(y);
                _sp = ", ";
            }
            builder.append(')');
            return builder.toString();
        }

        public static class Serializer extends JsonSerializer<TargetOf_location> {
            @Override
            public void serialize(TargetOf_location input, JsonGenerator gen,
                    SerializerProvider provider) throws IOException {
                gen.writeStartObject();
                if (input.isXLoaded()) {
                    provider.defaultSerializeField("x", input.getX(), gen);
                }
                if (input.isYLoaded()) {
                    provider.defaultSerializeField("y", input.getY(), gen);
                }
                gen.writeEndObject();
            }
        }

        @JsonPOJOBuilder(
                withPrefix = ""
        )
        public static class Builder {
            private Long x;

            private boolean _isXLoaded;

            private Long y;

            private boolean _isYLoaded;

            public Builder x(Long x) {
                this.x = x;
                this._isXLoaded = true;
                return this;
            }

            public Builder y(Long y) {
                this.y = y;
                this._isYLoaded = true;
                return this;
            }

            public TargetOf_location build() {
                TargetOf_location _input = new TargetOf_location();
                if (_isXLoaded) {
                    _input.setX(x);
                }
                if (_isYLoaded) {
                    _input.setY(y);
                }
                return _input;
            }
        }
    }

    public static class Serializer extends JsonSerializer<BookInput> {
        @Override
        public void serialize(BookInput input, JsonGenerator gen, SerializerProvider provider)
                throws IOException {
            gen.writeStartObject();
            provider.defaultSerializeField("id", input.getId(), gen);
            provider.defaultSerializeField("isEnabled", input.isEnabled(), gen);
            provider.defaultSerializeField("name", input.getName(), gen);
            if (input.isEditionLoaded()) {
                provider.defaultSerializeField("edition", input.getEdition(), gen);
            }
            provider.defaultSerializeField("price", input.getPrice(), gen);
            if (input.isLocationLoaded()) {
                provider.defaultSerializeField("location", input.getLocation(), gen);
            }
            gen.writeEndObject();
        }
    }

    @JsonPOJOBuilder(
            withPrefix = ""
    )
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {
        private Long id;

        private boolean _isIdLoaded;

        private Boolean isEnabled;

        private String name;

        private Integer edition;

        private boolean _isEditionLoaded;

        private BigDecimal price;

        private TargetOf_location location;

        private boolean _isLocationLoaded;

        public Builder id(Long id) {
            this.id = id;
            this._isIdLoaded = true;
            return this;
        }

        public Builder isEnabled(boolean isEnabled) {
            this.isEnabled = Objects.requireNonNull(isEnabled, "The property \"isEnabled\" cannot be null");
            return this;
        }

        @JsonAlias({
                    "dto-name"
                })
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        @JsonAlias("base-edition")
        public Builder edition(Integer edition) {
            this.edition = edition;
            this._isEditionLoaded = true;
            return this;
        }

        public Builder price(BigDecimal price) {
            this.price = price;
            return this;
        }

        public Builder location(TargetOf_location location) {
            this.location = location;
            this._isLocationLoaded = true;
            return this;
        }

        public BookInput build() {
            BookInput _input = new BookInput();
            if (!_isIdLoaded) {
                throw Input.unknownNullableProperty(BookInput.class, "id");
            }
            _input.setId(id);
            if (isEnabled == null) {
                throw Input.unknownNonNullProperty(BookInput.class, "isEnabled");
            }
            _input.setIsEnabled(isEnabled);
            _input.setName(name);
            if (_isEditionLoaded) {
                _input.setEdition(edition);
            }
            if (price != null) {
                _input.setPrice(price);
            }
            if (_isLocationLoaded) {
                _input.setLocation(location);
            }
            return _input;
        }
    }
}
