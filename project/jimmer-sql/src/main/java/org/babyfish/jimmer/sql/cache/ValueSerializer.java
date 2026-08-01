package org.babyfish.jimmer.sql.cache;

import org.babyfish.jimmer.json.codec.JsonCodec;
import org.babyfish.jimmer.json.codec.JsonType;
import org.babyfish.jimmer.json.codec.Node;
import org.babyfish.jimmer.meta.ImmutableProp;
import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.meta.InheritanceInfo;
import org.babyfish.jimmer.meta.TargetLevel;
import org.babyfish.jimmer.runtime.DraftSpi;
import org.babyfish.jimmer.runtime.ImmutableSpi;
import org.babyfish.jimmer.runtime.Internal;
import org.babyfish.jimmer.sql.exception.SerializationException;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

import static org.babyfish.jimmer.json.codec.JsonCodec.defaultCodec;

public class ValueSerializer<T> {
    private static final byte[] NULL_BYTES = "<null>".getBytes(StandardCharsets.UTF_8);

    private final JsonCodec jsonCodec;
    private final JsonType jsonType;
    private final String discriminatorPropName;
    private final Class<?> discriminatorType;
    private final InheritanceInfo inheritanceInfo;
    private final Map<Object, ImmutableType> typeByDiscriminator;

    public ValueSerializer(@NotNull ImmutableType type) {
        this(type, null, defaultCodec());
    }

    public ValueSerializer(@NotNull ImmutableProp prop) {
        this(null, prop, defaultCodec());
    }

    public ValueSerializer(@NotNull ImmutableType type, @NotNull JsonCodec codec) {
        this(type, null, codec);
    }

    public ValueSerializer(@NotNull ImmutableProp prop, @NotNull JsonCodec codec) {
        this(null, prop, codec);
    }

    private ValueSerializer(ImmutableType type, ImmutableProp prop, JsonCodec codec) {
        if ((type == null) == (prop == null)) {
            throw new IllegalArgumentException("Internal bug: nullity of type and prop must be different");
        }
        this.jsonCodec = codec;
        this.jsonType = createValueType(type, prop);
        this.inheritanceInfo = type != null ? type.getInheritanceInfo() : null;
        if (inheritanceInfo != null && type.hasDerivedTypes()) {
            ImmutableProp discriminatorProp = inheritanceInfo.getDiscriminatorProp();
            this.discriminatorPropName = discriminatorProp.getName();
            this.discriminatorType = discriminatorProp.getReturnClass();
            this.typeByDiscriminator = inheritanceInfo.getDiscriminatorTypeMap(type);
        } else {
            this.discriminatorPropName = null;
            this.discriminatorType = null;
            this.typeByDiscriminator = null;
        }
    }

    private static JsonType createValueType(ImmutableType type, ImmutableProp prop) {
        if (prop == null) {
            return JsonType.of(type.getJavaClass());
        } else if (prop.isAssociation(TargetLevel.ENTITY)) {
            ImmutableProp targetIdProp = prop.getTargetType().getIdProp();
            if (prop.isReferenceList(TargetLevel.OBJECT)) {
                return JsonType.listOf(targetIdProp.getElementClass());
            } else {
                return JsonType.of(targetIdProp.getElementClass());
            }
        } else {
            return JsonType.of(prop.getElementClass());
        }
    }

    public byte @NotNull [] serialize(T value) {
        if (value == null) {
            return NULL_BYTES.clone();
        }
        try {
            return jsonCodec.encode(value).getBytes(StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new SerializationException(ex);
        }
    }

    @NotNull
    public <K> Map<K, byte[]> serialize(@NotNull Map<K, T> map) {
        Map<K, byte[]> serializedMap = new LinkedHashMap<>((map.size() * 4 + 2) / 3);
        for (Map.Entry<K, T> e : map.entrySet()) {
            serializedMap.put(e.getKey(), serialize(e.getValue()));
        }
        return serializedMap;
    }

    @NotNull
    public <K1, K2> Map<K2, byte[]> serialize(@NotNull Map<K1, T> map, @NotNull Function<K1, K2> keyMapper) {
        Map<K2, byte[]> serializedMap = new LinkedHashMap<>((map.size() * 4 + 2) / 3);
        for (Map.Entry<K1, T> e : map.entrySet()) {
            serializedMap.put(keyMapper.apply(e.getKey()), serialize(e.getValue()));
        }
        return serializedMap;
    }

    @SuppressWarnings("unchecked")
    public T deserialize(byte[] value) {
        if (value == null || value.length == 0 || Arrays.equals(value, NULL_BYTES)) {
            return null;
        }
        try {
            String json = new String(value, StandardCharsets.UTF_8);
            if (typeByDiscriminator != null) {
                Node node = jsonCodec.decode(json, Node.class);
                Node discriminatorNode = node.get(discriminatorPropName);
                if (discriminatorNode == null || discriminatorNode.isNull()) {
                    throw new IllegalArgumentException(
                            "The serialized polymorphic object does not contain discriminator property \"" +
                                    discriminatorPropName +
                                    "\""
                    );
                }
                Object discriminator = discriminatorNode.canCastTo(discriminatorType) ?
                        discriminatorNode.castTo(discriminatorType) :
                        jsonCodec.decode(discriminatorNode.toString(), discriminatorType);
                ImmutableType actualType = typeByDiscriminator.get(discriminator);
                if (actualType == null) {
                    throw new IllegalArgumentException(
                            "The serialized polymorphic object has illegal discriminator \"" +
                                    discriminator +
                                    "\""
                    );
                }
                return loadDiscriminator(
                        (T) jsonCodec.decode(json, actualType.getJavaClass())
                );
            }
            T result = jsonCodec.decode(json, jsonType);
            return inheritanceInfo != null ? loadDiscriminator(result) : result;
        } catch (Exception ex) {
            throw new SerializationException(ex);
        }
    }

    @SuppressWarnings("unchecked")
    private T loadDiscriminator(T value) {
        ImmutableSpi spi = (ImmutableSpi) value;
        ImmutableType actualType = spi.__type();
        ImmutableProp discriminatorProp = inheritanceInfo.getDiscriminatorProp(actualType);
        Object discriminator = inheritanceInfo.getDiscriminatorValue(actualType);
        return (T) Internal.produce(
                actualType,
                spi,
                draft -> ((DraftSpi) draft).__set(discriminatorProp.getId(), discriminator)
        );
    }

    @NotNull
    public <K> Map<K, T> deserialize(@NotNull Map<K, byte[]> map) {
        Map<K, T> deserializedMap = new LinkedHashMap<>((map.size() * 4 + 2) / 3);
        for (Map.Entry<K, byte[]> e : map.entrySet()) {
            deserializedMap.put(e.getKey(), deserialize(e.getValue()));
        }
        return deserializedMap;
    }

    @NotNull
    public <K1, K2> Map<K2, T> deserialize(@NotNull Map<K1, byte[]> map, @NotNull Function<K1, K2> keyMapper) {
        Map<K2, T> deserializedMap = new LinkedHashMap<>((map.size() * 4 + 2) / 3);
        for (Map.Entry<K1, byte[]> e : map.entrySet()) {
            deserializedMap.put(keyMapper.apply(e.getKey()), deserialize(e.getValue()));
        }
        return deserializedMap;
    }

    @NotNull
    public <K> Map<K, T> deserialize(@NotNull Collection<K> keys, @NotNull Collection<byte[]> values) {
        Map<K, T> deserializedMap = new LinkedHashMap<>((keys.size() * 4 + 2) / 3);
        Iterator<K> keyItr = keys.iterator();
        Iterator<byte[]> byteArrItr = values.iterator();
        while (keyItr.hasNext() && byteArrItr.hasNext()) {
            K key = keyItr.next();
            byte[] byteArr = byteArrItr.next();
            if (byteArr != null) {
                deserializedMap.put(key, deserialize(byteArr));
            }
        }
        return deserializedMap;
    }
}
