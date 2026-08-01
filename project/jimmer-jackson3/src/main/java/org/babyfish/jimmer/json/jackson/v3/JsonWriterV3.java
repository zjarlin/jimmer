package org.babyfish.jimmer.json.jackson.v3;

import tools.jackson.databind.ObjectWriter;

import java.io.OutputStream;
import java.io.Writer;

public class JsonWriterV3 {
    private final ObjectWriter objectWriter;

    public JsonWriterV3(ObjectWriter objectWriter) {
        this.objectWriter = objectWriter;
    }

    public JsonWriterV3 withDefaultPrettyPrinter() {
        return new JsonWriterV3(objectWriter.withDefaultPrettyPrinter());
    }

    public String writeAsString(Object obj) throws Exception {
        return objectWriter.writeValueAsString(obj);
    }

    public byte[] writeAsBytes(Object obj) throws Exception {
        return objectWriter.writeValueAsBytes(obj);
    }

    public void write(Writer writer, Object obj) throws Exception {
        objectWriter.writeValue(writer, obj);
    }

    public void write(OutputStream os, Object obj) throws Exception {
        objectWriter.writeValue(os, obj);
    }
}
