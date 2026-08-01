package org.babyfish.jimmer.json.jackson.v3;

import org.babyfish.jimmer.json.codec.JsonCodec;
import org.babyfish.jimmer.json.codec.JsonCodecProvider;
import org.jspecify.annotations.NonNull;
import tools.jackson.databind.json.JsonMapper;

public class JsonCodecProviderV3 implements JsonCodecProvider {

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
    @NonNull
    public JsonCodec codec() {
        return mapper != null ? new JsonCodecV3(mapper) : new JsonCodecV3();
    }
}
