package org.babyfish.jimmer.sql.cache.redisson;

import org.babyfish.jimmer.meta.ImmutableProp;
import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.sql.cache.CacheTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.Collection;
import java.util.UUID;

@tools.jackson.databind.annotation.JsonSerialize(using = InvalidateMessageSerializer3.class)
@tools.jackson.databind.annotation.JsonDeserialize(using = InvalidateMessageDeserializer3.class)
@com.fasterxml.jackson.databind.annotation.JsonSerialize(using = InvalidateMessageSerializer.class)
@com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = InvalidateMessageDeserializer.class)
class InvalidateMessage implements Serializable {

    @NotNull
    UUID trackerId; // No final for serialization

    @NotNull
    String typeName; // No final for serialization

    @Nullable
    String propName; // No final for serialization

    @NotNull
    Collection<?> ids; // No final for serialization

    private transient ImmutableType type;

    InvalidateMessage(
            @NotNull UUID trackerId,
            @NotNull CacheTracker.InvalidateEvent event
    ) {
        this.trackerId = trackerId;
        this.typeName = event.getType().toString();
        if (event.getProp() != null) {
            this.propName = event.getProp().getName();
        } else {
            this.propName = null;
        }
        this.ids = event.getIds();
    }

    public InvalidateMessage(
            @NotNull UUID trackerId,
            @NotNull String typeName,
            @Nullable String propName
    ) {
        this.trackerId = trackerId;
        this.typeName = typeName;
        this.propName = propName;
    }

    ImmutableType getType() {
        ImmutableType type = this.type;
        if (type == null) {
            Class<?> javaType;
            try {
                javaType = Class.forName(typeName, true, Thread.currentThread().getContextClassLoader());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(
                        "Cannot resolve the type name \"" +
                                typeName +
                                "\""
                );
            }
            this.type = type = ImmutableType.get(javaType);
        }
        return type;
    }

    CacheTracker.InvalidateEvent toEvent() {
        if (propName != null) {
            ImmutableProp prop = getType().getProp(propName);
            return new CacheTracker.InvalidateEvent(prop, ids);
        }
        return new CacheTracker.InvalidateEvent(getType(), ids);
    }
}
