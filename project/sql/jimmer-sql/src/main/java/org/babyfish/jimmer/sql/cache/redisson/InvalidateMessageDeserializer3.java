package org.babyfish.jimmer.sql.cache.redisson;

import org.babyfish.jimmer.meta.ImmutableProp;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.type.CollectionType;
import tools.jackson.databind.type.SimpleType;

import java.util.List;
import java.util.UUID;

class InvalidateMessageDeserializer3 extends StdDeserializer<InvalidateMessage> {

    protected InvalidateMessageDeserializer3() {
        super(InvalidateMessage.class);
    }

    @Override
    public InvalidateMessage deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
        JsonNode node = ctxt.readTree(p);
        UUID trackerId = UUID.fromString(node.get("trackerId").asString());
        String typeName = node.get("typeName").asString();
        String propName = node.get("propName").isNull() ? null : node.get("propName").asString();
        InvalidateMessage message = new InvalidateMessage(trackerId, typeName, propName);
        ImmutableProp idProp = message.getType().getIdProp();
        message.ids = ctxt.readTreeAsValue(
                node.get("ids"),
                CollectionType.construct(
                        List.class,
                        null,
                        null,
                        null,
                        SimpleType.constructUnsafe(idProp.getReturnClass())
                )
        );
        return message;
    }
}
