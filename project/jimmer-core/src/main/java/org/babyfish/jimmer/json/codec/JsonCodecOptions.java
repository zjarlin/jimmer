package org.babyfish.jimmer.json.codec;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 与具体 JSON 框架无关的单次编解码选项。
 */
public final class JsonCodecOptions {

    public static final JsonCodecOptions DEFAULT = new Builder().build();

    private final boolean prettyPrint;

    private final PropertyNaming propertyNaming;

    private final Map<Object, Object> attributes;

    private JsonCodecOptions(Builder builder) {
        prettyPrint = builder.prettyPrint;
        propertyNaming = builder.propertyNaming;
        attributes = builder.attributes.isEmpty() ?
                Collections.emptyMap() :
                Collections.unmodifiableMap(new LinkedHashMap<>(builder.attributes));
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    @Nullable
    public PropertyNaming getPropertyNaming() {
        return propertyNaming;
    }

    public Map<Object, Object> getAttributes() {
        return attributes;
    }

    public enum PropertyNaming {
        LOWER_CAMEL_CASE,
        UPPER_CAMEL_CASE,
        LOWER_CASE,
        SNAKE_CASE,
        KEBAB_CASE,
        LOWER_DOT_CASE
    }

    public static final class Builder {

        private boolean prettyPrint;

        private PropertyNaming propertyNaming;

        private final Map<Object, Object> attributes = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder prettyPrint(boolean prettyPrint) {
            this.prettyPrint = prettyPrint;
            return this;
        }

        public Builder propertyNaming(@Nullable PropertyNaming propertyNaming) {
            this.propertyNaming = propertyNaming;
            return this;
        }

        public Builder attribute(Object key, @Nullable Object value) {
            attributes.put(Objects.requireNonNull(key, "key cannot be null"), value);
            return this;
        }

        public Builder attributes(Map<?, ?> attributes) {
            Objects.requireNonNull(attributes, "attributes cannot be null");
            for (Map.Entry<?, ?> entry : attributes.entrySet()) {
                attribute(entry.getKey(), entry.getValue());
            }
            return this;
        }

        public JsonCodecOptions build() {
            return new JsonCodecOptions(this);
        }
    }
}
