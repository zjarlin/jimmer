package org.babyfish.jimmer.sql.cache.redisson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.SimpleType;
import org.babyfish.jimmer.meta.ImmutableProp;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

class InvalidateMessageDeserializer extends StdDeserializer<InvalidateMessage> {

    protected InvalidateMessageDeserializer() {
        super(InvalidateMessage.class);
    }

    @Override
    public InvalidateMessage deserialize(JsonParser jp, DeserializationContext ctx) throws IOException {
        JsonNode node = jp.getCodec().readTree(jp);
        UUID trackerId = UUID.fromString(node.get("trackerId").asText());
        String typeName = node.get("typeName").asText();
        String propName = node.get("propName").isNull() ? null : node.get("propName").asText();
        InvalidateMessage message = new InvalidateMessage(trackerId, typeName, propName);
        ImmutableProp idProp = message.getType().getIdProp();
        message.ids = ctx.readTreeAsValue(
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
