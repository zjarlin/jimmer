package org.babyfish.jimmer.json.codec;

class JsonCodecDetector {
    static final JsonCodec DEFAULT_CODEC = JsonCodecDispatcher.load();
}
