package org.babyfish.jimmer.json.jackson.v3;

import org.babyfish.jimmer.json.codec.JsonCodec;
import org.babyfish.jimmer.json.codec.JsonCodecProvider;
import org.babyfish.jimmer.json.codec.JsonType;
import org.jspecify.annotations.NonNull;
import tools.jackson.databind.json.JsonMapper;

import static org.babyfish.jimmer.impl.util.JsonCodecProviderUtil.containsType;
import static org.babyfish.jimmer.impl.util.JsonCodecProviderUtil.containsValue;

public class JsonCodecProviderV3 implements JsonCodecProvider {

    private static final String[] JACKSON2_PACKAGE_PREFIXES = {
            "com.fasterxml.jackson.core.",
            "com.fasterxml.jackson.databind."
    };

    private final JsonMapper mapper;

    public JsonCodecProviderV3() {
        this.mapper = null;
    }

    public JsonCodecProviderV3(JsonMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public int priority() {
        return 300;
    }

    @Override
    public boolean supportsEncode(Object value, JsonType type) {
        if (!type.isAny()) {
            return !containsType(type.getType(), JACKSON2_PACKAGE_PREFIXES);
        }
        return !containsValue(value, JACKSON2_PACKAGE_PREFIXES);
    }

    @Override
    public boolean supportsDecode(JsonType type) {
        return !containsType(type.getType(), JACKSON2_PACKAGE_PREFIXES);
    }

    @Override
    @NonNull
    public JsonCodec codec() {
        return mapper != null ? new JsonCodecV3(mapper) : new JsonCodecV3();
    }
}
