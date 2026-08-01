package org.babyfish.jimmer.json.codec;

import org.jetbrains.annotations.NotNull;

public interface JsonCodecProvider {

    int priority();

    /**
     * 判断当前 Provider 是否可以编码指定值和类型。
     */
    default boolean supportsEncode(Object value, @NotNull JsonType type) {
        return true;
    }

    /**
     * 判断当前 Provider 是否可以解码指定类型。
     */
    default boolean supportsDecode(@NotNull JsonType type) {
        return true;
    }

    /**
     * 返回当前 Provider 提供的 JSON 编解码门面。
     */
    @NotNull
    JsonCodec codec();
}
