package org.babyfish.jimmer.json.codec;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;

/**
 * 按 Provider 能力和优先级为每次 JSON 操作选择编解码器。
 */
public final class JsonCodecDispatcher implements JsonCodec {

    private final List<Entry> entries;

    private JsonCodecDispatcher(Iterable<? extends JsonCodecProvider> providers) {
        List<JsonCodecProvider> sortedProviders = new ArrayList<>();
        for (JsonCodecProvider provider : providers) {
            sortedProviders.add(provider);
        }
        sortedProviders.sort((a, b) -> {
            int priorityComparison = Integer.compare(b.priority(), a.priority());
            if (priorityComparison != 0) {
                return priorityComparison;
            }
            return a.getClass().getName().compareTo(b.getClass().getName());
        });
        if (sortedProviders.isEmpty()) {
            throw new IllegalStateException(
                    "No JSON codec provider is in classpath, please add a JSON codec provider module"
            );
        }
        List<Entry> entries = new ArrayList<>(sortedProviders.size());
        for (JsonCodecProvider provider : sortedProviders) {
            JsonCodec codec = Objects.requireNonNull(
                    provider.codec(),
                    "JSON codec provided by " + provider.getClass().getName() + " cannot be null"
            );
            entries.add(new Entry(provider, codec));
        }
        this.entries = entries;
    }

    /**
     * 加载类路径中的 Provider，并允许同实现类型的显式 Provider 覆盖 SPI 实例。
     */
    @NotNull
    public static JsonCodecDispatcher load(JsonCodecProvider... overrides) {
        Map<Class<?>, JsonCodecProvider> providerMap = new LinkedHashMap<>();
        for (JsonCodecProvider provider : overrides) {
            providerMap.put(provider.getClass(), provider);
        }
        loadProviders(ServiceLoader.load(JsonCodecProvider.class), providerMap);
        loadProviders(
                ServiceLoader.load(JsonCodecProvider.class, JsonCodecProvider.class.getClassLoader()),
                providerMap
        );
        return new JsonCodecDispatcher(providerMap.values());
    }

    static JsonCodecDispatcher of(JsonCodecProvider... providers) {
        return new JsonCodecDispatcher(Arrays.asList(providers));
    }

    @Override
    public String encode(Object value, JsonType type, JsonCodecOptions options) throws Exception {
        for (Entry entry : entries) {
            if (entry.provider.supportsEncode(value, type)) {
                return entry.codec.encode(value, type, options);
            }
        }
        throw unsupported("encode", type);
    }

    @Override
    public <T> T decode(String json, JsonType type, JsonCodecOptions options) throws Exception {
        for (Entry entry : entries) {
            if (entry.provider.supportsDecode(type)) {
                return entry.codec.decode(json, type, options);
            }
        }
        throw unsupported("decode", type);
    }

    private static void loadProviders(
            ServiceLoader<JsonCodecProvider> serviceLoader,
            Map<Class<?>, JsonCodecProvider> providerMap
    ) {
        for (JsonCodecProvider provider : serviceLoader) {
            providerMap.putIfAbsent(provider.getClass(), provider);
        }
    }

    private IllegalStateException unsupported(String operation, JsonType type) {
        StringBuilder builder = new StringBuilder()
                .append("No JSON codec provider can ")
                .append(operation)
                .append(" type \"")
                .append(type)
                .append("\", available providers: ");
        for (int i = 0; i < entries.size(); i++) {
            if (i != 0) {
                builder.append(", ");
            }
            builder.append(entries.get(i).provider.getClass().getName());
        }
        return new IllegalStateException(builder.toString());
    }

    private static final class Entry {

        private final JsonCodecProvider provider;

        private final JsonCodec codec;

        private Entry(JsonCodecProvider provider, JsonCodec codec) {
            this.provider = provider;
            this.codec = codec;
        }
    }
}
