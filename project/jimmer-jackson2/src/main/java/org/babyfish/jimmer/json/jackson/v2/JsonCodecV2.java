package org.babyfish.jimmer.json.jackson.v2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.babyfish.jimmer.json.codec.JsonCodec;
import org.babyfish.jimmer.json.codec.JsonCodecOptions;
import org.babyfish.jimmer.json.codec.JsonType;
import org.babyfish.jimmer.json.codec.Node;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;
import static org.babyfish.jimmer.json.jackson.v2.ModulesRegistrarV2.registerWellKnownModules;

public class JsonCodecV2 implements JsonCodec {
    private final ObjectMapper mapper;
    private final JacksonTypeFactoryV2 typeFactory;

    public JsonCodecV2() {
        this(createDefaultMapper());
    }

    public JsonCodecV2(ObjectMapper mapper) {
        this.mapper = mapper;
        this.typeFactory = new JacksonTypeFactoryV2(mapper.getTypeFactory());
    }

    private static ObjectMapper createDefaultMapper() {
        JsonMapper.Builder builder = JsonMapper.builder()
                .disable(WRITE_DATES_AS_TIMESTAMPS)
                .disable(FAIL_ON_UNKNOWN_PROPERTIES);

        registerWellKnownModules(builder);
        ModulesRegistrarV2.registerImmutableModule(builder);

        return builder.build();
    }

    @Override
    public String encode(Object value, JsonType type, JsonCodecOptions options) throws Exception {
        ObjectMapper mapper = mapper(options);
        ObjectWriter writer = type.isAny() ? mapper.writer() : mapper.writerFor(typeFactory.javaType(type));
        if (!options.getAttributes().isEmpty()) {
            writer = writer.withAttributes(options.getAttributes());
        }
        if (options.isPrettyPrint()) {
            writer = writer.withDefaultPrettyPrinter();
        }
        return writer.writeValueAsString(value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T decode(String json, JsonType type, JsonCodecOptions options) throws Exception {
        ObjectMapper mapper = mapper(options);
        if (type.getType() == Node.class) {
            JsonNode node = mapper.readTree(json);
            return (T) new NodeV2(node);
        }
        ObjectReader reader = mapper.readerFor(typeFactory.javaType(type));
        if (!options.getAttributes().isEmpty()) {
            reader = reader.withAttributes(options.getAttributes());
        }
        return reader.readValue(json);
    }

    private ObjectMapper mapper(JsonCodecOptions options) {
        JsonCodecOptions.PropertyNaming propertyNaming = options.getPropertyNaming();
        if (propertyNaming == null) {
            return mapper;
        }
        ObjectMapper copy = mapper.copy();
        switch (propertyNaming) {
            case LOWER_CAMEL_CASE:
                return copy.setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE);
            case UPPER_CAMEL_CASE:
                return copy.setPropertyNamingStrategy(PropertyNamingStrategies.UPPER_CAMEL_CASE);
            case LOWER_CASE:
                return copy.setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CASE);
            case SNAKE_CASE:
                return copy.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
            case KEBAB_CASE:
                return copy.setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE);
            case LOWER_DOT_CASE:
                return copy.setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_DOT_CASE);
            default:
                throw new AssertionError("Unknown property naming: " + propertyNaming);
        }
    }
}
