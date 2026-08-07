package org.babyfish.jimmer.sql.cache.redisson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

class InvalidateMessageSerializer extends StdSerializer<InvalidateMessage> {

    InvalidateMessageSerializer() {
        super(InvalidateMessage.class);
    }

    @Override
    public void serialize(InvalidateMessage value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeStartObject();
        gen.writeStringField("trackerId", value.trackerId.toString());
        gen.writeStringField("typeName", value.typeName);
        gen.writeStringField("propName", value.propName);
        gen.writeObjectField("ids", value.ids);
        gen.writeEndObject();
    }
}
