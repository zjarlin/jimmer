package org.babyfish.jimmer.json.codec;

/**
 * 抽象 Jimmer 与具体 JSON 实现的门面。
 */
public interface JsonCodec {
    /**
     * 返回通过 SPI 自动选择的默认 JSON 编解码门面。
     */
    static JsonCodec defaultCodec() {
        return JsonCodecDetector.DEFAULT_CODEC;
    }

    default String encode(Object value) throws Exception {
        return encode(value, JsonType.any(), JsonCodecOptions.DEFAULT);
    }

    default String encode(Object value, JsonType type) throws Exception {
        return encode(value, type, JsonCodecOptions.DEFAULT);
    }

    default String encode(Object value, JsonCodecOptions options) throws Exception {
        return encode(value, JsonType.any(), options);
    }

    String encode(Object value, JsonType type, JsonCodecOptions options) throws Exception;

    default <T> T decode(String json, Class<T> type) throws Exception {
        return decode(json, JsonType.of(type), JsonCodecOptions.DEFAULT);
    }

    default <T> T decode(String json, Class<T> type, JsonCodecOptions options) throws Exception {
        return decode(json, JsonType.of(type), options);
    }

    default <T> T decode(String json, JsonType type) throws Exception {
        return decode(json, type, JsonCodecOptions.DEFAULT);
    }

    <T> T decode(String json, JsonType type, JsonCodecOptions options) throws Exception;
}
