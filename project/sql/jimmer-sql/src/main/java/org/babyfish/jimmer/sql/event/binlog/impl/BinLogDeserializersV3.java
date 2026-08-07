package org.babyfish.jimmer.sql.event.binlog.impl;

import org.babyfish.jimmer.meta.ImmutableType;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.Deserializers;

class BinLogDeserializersV3 extends Deserializers.Base {

    private final BinLogParser parser;

    BinLogDeserializersV3(BinLogParser parser) {
        this.parser = parser;
    }

    @Override
    public boolean hasDeserializerFor(DeserializationConfig config, Class<?> valueType) {
        return ImmutableType.tryGet(valueType) != null;
    }

    @Override
    public ValueDeserializer<?> findBeanDeserializer(JavaType type, DeserializationConfig config, BeanDescription.Supplier beanDescRef) {
        ImmutableType immutableType = ImmutableType.tryGet(type.getRawClass());
        if (immutableType != null) {
            return new BinLogDeserializerV3(parser, immutableType);
        }
        return null;
    }
}
