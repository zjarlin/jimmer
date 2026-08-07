package org.babyfish.jimmer.sql.cache.redisson;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

class InvalidateMessageSerializer3 extends StdSerializer<InvalidateMessage> {

    InvalidateMessageSerializer3() {
        super(InvalidateMessage.class);
    }

    @Override
    public void serialize(InvalidateMessage value, JsonGenerator gen, SerializationContext provider) throws JacksonException {
        gen.writeStartObject();
        gen.writeStringProperty("trackerId", value.trackerId.toString());
        gen.writeStringProperty("typeName", value.typeName);
        gen.writeStringProperty("propName", value.propName);
        gen.writePOJOProperty("ids", value.ids);
        gen.writeEndObject();
    }
}
