package org.babyfish.jimmer.json.jackson.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.babyfish.jimmer.json.codec.JsonCodec;
import org.babyfish.jimmer.json.codec.JsonCodecProvider;
import org.jspecify.annotations.NonNull;

public class JsonCodecProviderV2 implements JsonCodecProvider {

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
    @NonNull
    public JsonCodec codec() {
        return mapper != null ? new JsonCodecV2(mapper) : new JsonCodecV2();
    }
}
