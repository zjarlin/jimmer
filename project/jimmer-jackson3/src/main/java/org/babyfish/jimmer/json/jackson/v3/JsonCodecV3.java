package org.babyfish.jimmer.json.jackson.v3;

import org.babyfish.jimmer.json.codec.JsonCodec;
import org.babyfish.jimmer.json.codec.JsonCodecOptions;
import org.babyfish.jimmer.json.codec.JsonType;
import org.babyfish.jimmer.json.codec.Node;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import static org.babyfish.jimmer.json.jackson.v3.ModulesRegistrarV3.registerWellKnownModules;
import static tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS;
import static tools.jackson.databind.MapperFeature.SORT_PROPERTIES_ALPHABETICALLY;

public class JsonCodecV3 implements JsonCodec {
    private final JsonMapper mapper;
    private final JacksonTypeFactoryV3 typeFactory;

    public JsonCodecV3() {
        this(createDefaultMapper());
    }

    public JsonCodecV3(JsonMapper mapper) {
        this.mapper = mapper;
        this.typeFactory = new JacksonTypeFactoryV3(mapper.getTypeFactory());
    }

    private static JsonMapper createDefaultMapper() {
        JsonMapper.Builder builder = JsonMapper.builder()
                .disable(FAIL_ON_TRAILING_TOKENS)
                .disable(SORT_PROPERTIES_ALPHABETICALLY);

        registerWellKnownModules(builder);
        ModulesRegistrarV3.registerImmutableModule(builder);

        return builder.build();
    }

    @Override
    public String encode(Object value, JsonType type, JsonCodecOptions options) throws Exception {
        JsonMapper mapper = mapper(options);
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
        JsonMapper mapper = mapper(options);
        if (type.getType() == Node.class) {
            JsonNode node = mapper.readTree(json);
            return (T) new NodeV3(node);
        }
        ObjectReader reader = mapper.readerFor(typeFactory.javaType(type));
        if (!options.getAttributes().isEmpty()) {
            reader = reader.withAttributes(options.getAttributes());
        }
        return reader.readValue(json);
    }

    private JsonMapper mapper(JsonCodecOptions options) {
        JsonCodecOptions.PropertyNaming propertyNaming = options.getPropertyNaming();
        if (propertyNaming == null) {
            return mapper;
        }
        JsonMapper.Builder builder = mapper.rebuild();
        switch (propertyNaming) {
            case LOWER_CAMEL_CASE:
                return builder.propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE).build();
            case UPPER_CAMEL_CASE:
                return builder.propertyNamingStrategy(PropertyNamingStrategies.UPPER_CAMEL_CASE).build();
            case LOWER_CASE:
                return builder.propertyNamingStrategy(PropertyNamingStrategies.LOWER_CASE).build();
            case SNAKE_CASE:
                return builder.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
            case KEBAB_CASE:
                return builder.propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE).build();
            case LOWER_DOT_CASE:
                return builder.propertyNamingStrategy(PropertyNamingStrategies.LOWER_DOT_CASE).build();
            default:
                throw new AssertionError("Unknown property naming: " + propertyNaming);
        }
    }
}
