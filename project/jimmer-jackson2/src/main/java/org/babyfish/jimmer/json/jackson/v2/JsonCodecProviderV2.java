package org.babyfish.jimmer.json.jackson.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.babyfish.jimmer.json.codec.JsonCodec;
import org.babyfish.jimmer.json.codec.JsonCodecProvider;
import org.babyfish.jimmer.json.codec.JsonType;
import org.jspecify.annotations.NonNull;

import static org.babyfish.jimmer.impl.util.JsonCodecProviderUtil.containsType;
import static org.babyfish.jimmer.impl.util.JsonCodecProviderUtil.containsValue;

public class JsonCodecProviderV2 implements JsonCodecProvider {

    private static final String JACKSON3_PACKAGE_PREFIX = "tools.jackson.";

    private final ObjectMapper mapper;

    public JsonCodecProviderV2() {
        this.mapper = null;
    }

    public JsonCodecProviderV2(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public int priority() {
        return 200;
    }

    @Override
    public boolean supportsEncode(Object value, JsonType type) {
        if (!type.isAny()) {
            return !containsType(type.getType(), JACKSON3_PACKAGE_PREFIX);
        }
        return !containsValue(value, JACKSON3_PACKAGE_PREFIX);
    }

    @Override
    public boolean supportsDecode(JsonType type) {
        return !containsType(type.getType(), JACKSON3_PACKAGE_PREFIX);
    }

    @Override
    @NonNull
    public JsonCodec codec() {
        return mapper != null ? new JsonCodecV2(mapper) : new JsonCodecV2();
    }
}
